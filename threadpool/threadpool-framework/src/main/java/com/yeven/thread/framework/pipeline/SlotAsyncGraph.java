package com.yeven.thread.framework.pipeline;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于插槽（Slot）的高性能不可变异步有向无环图（DAG）执行器。
 *
 * <p>
 * 核心设计思想是在运行期避免使用复杂的 Map（如 ConcurrentHashMap）进行槽数据的读取与写入，
 * 从而消除哈希计算与锁竞争。其运行时数据路径如下：
 * </p>
 * 
 * <pre>
 * 1) 节点执行完后，将输出数据写入 {@code Object[] slots} 数组的特定索引槽位中；
 * 2) 通过无锁并发位集 {@code
 * readyBits
 * } 原子标记该插槽数据的可用性；
 * 3) 下游节点通过整型索引直接、低开销地读取所需的槽数据。
 * </pre>
 *
 * @param <C> 基础上下文类型
 */
public final class SlotAsyncGraph<C> {

    private static final VarHandle INT_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(int[].class);

    private final AsyncStepFactory stepFactory;
    private final SlotSymbolTable symbolTable;
    private final RuntimeNode<C>[] nodes;
    private final int[][] dependentsByNode;
    private final int[] initialRemainingDependencies;
    private final boolean[] terminalPath;
    private final int terminalNodeIndex;
    private final SlotGraphMetricsRecorder metricsRecorder;

    /**
     * 构造不可变插槽异步 DAG 执行器。
     *
     * @param stepFactory      异步步骤工厂
     * @param symbolTable      符号槽与整型索引映射表
     * @param definitions      节点定义 Map
     * @param topologicalOrder 经过校验的拓扑排序节点名称列表
     * @param terminalNodeName 终点节点（出口节点）名称
     * @param metricsRecorder  监控指标记录器
     */
    SlotAsyncGraph(
            AsyncStepFactory stepFactory,
            SlotSymbolTable symbolTable,
            Map<String, SlotAsyncGraphNodeDefinition<C>> definitions,
            List<String> topologicalOrder,
            String terminalNodeName,
            SlotGraphMetricsRecorder metricsRecorder) {
        this.stepFactory = Objects.requireNonNull(stepFactory, "stepFactory");
        this.symbolTable = Objects.requireNonNull(symbolTable, "symbolTable");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(topologicalOrder, "topologicalOrder");
        Objects.requireNonNull(terminalNodeName, "terminalNodeName");
        this.metricsRecorder = Objects.requireNonNull(metricsRecorder, "metricsRecorder");

        // 构建节点名称到其在拓扑排序中索引的快速映射，便于后续编译
        Map<String, Integer> nodeIndexByName = buildNodeIndex(topologicalOrder);
        Integer terminalIndex = nodeIndexByName.get(terminalNodeName);
        if (terminalIndex == null) {
            throw new IllegalStateException("Terminal node not found: " + terminalNodeName);
        }
        // 将节点定义列表编译为运行期节点数组
        this.nodes = compileRuntimeNodes(definitions, topologicalOrder, nodeIndexByName);
        this.terminalNodeIndex = terminalIndex;
        // 静态编译：计算每个节点执行完成后需要通知唤醒的下游节点索引列表
        this.dependentsByNode = compileDependentsByNode(nodes);
        // 静态编译：统计每个节点启动所依赖的上游节点总数（即入度）
        this.initialRemainingDependencies = compileRemainingDependencies(nodes);
        // 静态编译：计算哪些节点位于到达终点节点的必经路径上（用于异常 Fail-Fast 控制）
        this.terminalPath = compileTerminalPath(nodes, terminalIndex);
    }

    /**
     * 对传入的初始上下文执行异步有向无环图。
     *
     * <p>
     * 执行过程是完全并发的。计算入度为 0 的起始节点并开始并行调度，
     * 后续节点在其所有前置依赖节点执行完毕后被自动激活。
     * </p>
     *
     * @param initialContext 初始上下文快照
     * @return 最终由终点节点解析后输出的上下文 Future
     */
    public CompletableFuture<C> execute(C initialContext) {
        // 创建代表本次 DAG 运行生命周期的 GraphFrame 运行帧
        GraphFrame<C> frame = new GraphFrame<>(
                initialContext,
                symbolTable,
                symbolTable.slotCount(),
                initialRemainingDependencies);
        // 遍历所有节点，找出没有任何入度依赖（即初始依赖数为 0）的根节点，开始分发执行
        for (int nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++) {
            if (initialRemainingDependencies[nodeIndex] == 0) {
                dispatchNode(nodeIndex, frame);
            }
        }
        return frame.result;
    }

    private void dispatchNode(int nodeIndex, GraphFrame<C> frame) {
        RuntimeNode<C> node = nodes[nodeIndex];
        if (metricsRecorder == NoopSlotGraphMetricsRecorder.INSTANCE) {
            stepFactory.dispatchNode(
                    node.mode(),
                    () -> executeNode(node, frame),
                    error -> completeNode(nodeIndex, frame, error));
            return;
        }

        long readyNanos = System.nanoTime();
        AtomicBoolean started = new AtomicBoolean(false);
        stepFactory.dispatchNode(
                node.mode(),
                () -> executeMeasuredNode(node, frame, readyNanos, started),
                error -> {
                    if (error != null && !started.get()) {
                        metricsRecorder.recordNode(
                                node.name(),
                                node.mode(),
                                node.role().name(),
                                0L,
                                0L,
                                false,
                                error);
                    }
                    completeNode(nodeIndex, frame, error);
                });
    }

    private void executeMeasuredNode(
            RuntimeNode<C> node,
            GraphFrame<C> frame,
            long readyNanos,
            AtomicBoolean started) {
        started.set(true);
        long startNanos = System.nanoTime();
        try {
            executeNode(node, frame);
            long endNanos = System.nanoTime();
            metricsRecorder.recordNode(
                    node.name(),
                    node.mode(),
                    node.role().name(),
                    startNanos - readyNanos,
                    endNanos - startNanos,
                    true,
                    null);
        } catch (Throwable error) {
            long endNanos = System.nanoTime();
            metricsRecorder.recordNode(
                    node.name(),
                    node.mode(),
                    node.role().name(),
                    startNanos - readyNanos,
                    endNanos - startNanos,
                    false,
                    error);
            throw error;
        }
    }

    /**
     * 当某个节点运行结束（成功或异常失败）时触发的回调处理。
     */
    private void completeNode(int nodeIndex, GraphFrame<C> frame, Throwable error) {
        if (error != null) {
            // 如果节点执行发生异常，且该节点在通往终点的路径上，则直接令整个 DAG Future 异常结束
            if (terminalPath[nodeIndex]) {
                frame.result.completeExceptionally(error);
            }
            return;
        }

        // 如果是终点节点执行完成，获取其输出的上下文并完成整个 DAG Flow
        if (nodeIndex == terminalNodeIndex) {
            C result = frame.terminalResult.get();
            if (result == null) {
                frame.result.completeExceptionally(new IllegalStateException(
                        "Terminal node '" + nodes[terminalNodeIndex].name() + "' did not produce result"));
                return;
            }
            frame.result.complete(result);
        }

        // 遍历所有依赖当前节点的下游节点，将其未完成的依赖数减 1
        for (int dependentIndex : dependentsByNode[nodeIndex]) {
            // 利用 VarHandle 对 int 数组元素进行原子减 1 操作（无锁 CAS），返回减 1 前的旧值
            int previous = (int) INT_ARRAY_HANDLE.getAndAdd(frame.remainingDependencies, dependentIndex, -1);
            int remaining = previous - 1;
            // 如果减 1 后剩余依赖数为 0，说明该下游节点的所有前置条件全部就绪，立即分发执行该节点
            if (remaining == 0) {
                dispatchNode(dependentIndex, frame);
            } else if (remaining < 0) {
                // 出现负数说明依赖计数器发生下溢，属于系统级逻辑错误
                frame.result.completeExceptionally(new IllegalStateException(
                        "Node '" + nodes[dependentIndex].name() + "' dependency count underflow"));
            }
        }
    }

    private void executeNode(
            RuntimeNode<C> node,
            GraphFrame<C> frame) {
        for (int slotId : node.readSlots()) {
            if (!frame.slotState.hasSlot(slotId)) {
                throw new IllegalStateException(
                        "Node '" + node.name() + "' reads unavailable slot " + symbolTable.describe(slotId));
            }
        }

        ReadOnlySlotContextView<C> view = frame.view;
        if (node.role() == SlotNodeRole.PATCH) {
            if (node.slotEvaluator() != null) {
                Object value = node.slotEvaluator().apply(view);
                frame.slotState.writeSlot(node.singleWriteSlot(), value);
                return;
            }
            SlotPatch patch = Objects.requireNonNull(
                    node.patchEvaluator().apply(view),
                    "Patch evaluator returned null for node: " + node.name());
            applyPatch(node, patch, frame.slotState);
            return;
        }

        C resolvedContext = Objects.requireNonNull(
                node.terminalEvaluator().apply(view),
                "Terminal evaluator returned null for node: " + node.name());
        frame.terminalResult.set(resolvedContext);
    }

    private void applyPatch(RuntimeNode<C> node, SlotPatch patch, SlotState slotState) {
        for (int i = 0; i < patch.size(); i++) {
            int slotId = patch.slotIdAt(i);
            if (slotId < 0 || slotId >= symbolTable.slotCount()) {
                throw new IllegalStateException(
                        "Node '" + node.name() + "' writes out-of-range slot id: " + slotId);
            }
            if (Arrays.binarySearch(node.declaredWriteSlotsSorted(), slotId) < 0) {
                throw new IllegalStateException(
                        "Node '" + node.name() + "' writes undeclared slot " + symbolTable.describe(slotId));
            }
            slotState.writeSlot(slotId, patch.valueAt(i));
        }
    }

    /**
     * 运行期状态存储，维护每个插槽（Slot）的具体数据以及就绪状态的位集。
     */
    private static final class SlotState {

        // 存储实际插槽值的 Object 数组
        private final Object[] slots;
        // 基于 AtomicLongArray 的无锁就绪标记位集。每个 Long 占用 64 位，对应 64 个插槽的状态
        private final AtomicLongArray readyBits;

        private SlotState(int slotCount) {
            this.slots = new Object[slotCount];
            // 计算需要多少个 Long 来表示 slotCount 个位（向上取整除以 64）
            this.readyBits = new AtomicLongArray((slotCount + 63) >>> 6);
        }

        /**
         * 检查指定的插槽 ID 数据是否已写入就绪。
         */
        private boolean hasSlot(int slotId) {
            int wordIndex = slotId >>> 6; // 相当于 slotId / 64
            long mask = 1L << (slotId & 63); // 相当于 1L << (slotId % 64)
            return (readyBits.get(wordIndex) & mask) != 0L;
        }

        /**
         * 获取指定插槽 ID 的值。
         */
        private Object slot(int slotId) {
            return slots[slotId];
        }

        /**
         * 向指定插槽 ID 写入值，并将其标记为就绪状态。
         */
        private void writeSlot(int slotId, Object value) {
            slots[slotId] = value;
            markReady(slotId);
        }

        /**
         * 在位集中原子的将指定的插槽 ID 标记为就绪（无锁 CAS 自旋更新位图）。
         */
        private void markReady(int slotId) {
            int wordIndex = slotId >>> 6;
            long mask = 1L << (slotId & 63);
            while (true) {
                long current = readyBits.get(wordIndex);
                long next = current | mask;
                // 如果当前位已经为 1，或者通过 CAS 成功将该位置为 1，则退出循环
                if (current == next || readyBits.compareAndSet(wordIndex, current, next)) {
                    return;
                }
            }
        }
    }

    private record RuntimeNode<C>(
            String name,
            int[] dependencyIndexes,
            com.yeven.thread.framework.executor.ExecutionMode mode,
            int[] readSlots,
            int[] declaredWriteSlotsSorted,
            int singleWriteSlot,
            SlotNodeRole role,
            java.util.function.Function<ReadOnlySlotContextView<C>, Object> slotEvaluator,
            java.util.function.Function<ReadOnlySlotContextView<C>, SlotPatch> patchEvaluator,
            java.util.function.Function<ReadOnlySlotContextView<C>, C> terminalEvaluator) {
        private static <C> RuntimeNode<C> from(
                SlotAsyncGraphNodeDefinition<C> definition,
                Map<String, Integer> nodeIndexByName) {
            int[] sortedWriteSlots = definition.getDeclaredWriteSlots();
            Arrays.sort(sortedWriteSlots);
            int[] dependencyIndexes = new int[definition.getDependencies().size()];
            for (int i = 0; i < dependencyIndexes.length; i++) {
                String dependency = definition.getDependencies().get(i);
                Integer dependencyIndex = nodeIndexByName.get(dependency);
                if (dependencyIndex == null) {
                    throw new IllegalStateException(
                            "Node '" + definition.getName() + "' references unknown dependency '" + dependency + "'");
                }
                dependencyIndexes[i] = dependencyIndex;
            }
            int singleWriteSlot = definition.getSlotEvaluator() == null ? -1 : sortedWriteSlots[0];
            return new RuntimeNode<>(
                    definition.getName(),
                    dependencyIndexes,
                    definition.getMode(),
                    definition.getReadSlots(),
                    sortedWriteSlots,
                    singleWriteSlot,
                    definition.getRole(),
                    definition.getSlotEvaluator(),
                    definition.getPatchEvaluator(),
                    definition.getTerminalEvaluator());
        }
    }

    private static Map<String, Integer> buildNodeIndex(List<String> topologicalOrder) {
        LinkedHashMap<String, Integer> nodeIndexByName = new LinkedHashMap<>(topologicalOrder.size());
        for (int i = 0; i < topologicalOrder.size(); i++) {
            String nodeName = topologicalOrder.get(i);
            if (nodeName == null || nodeName.isBlank()) {
                throw new IllegalArgumentException("topologicalOrder contains blank node name at index " + i);
            }
            Integer previous = nodeIndexByName.putIfAbsent(nodeName, i);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate node in topologicalOrder: " + nodeName);
            }
        }
        return Map.copyOf(nodeIndexByName);
    }

    @SuppressWarnings("unchecked")
    private static <C> RuntimeNode<C>[] compileRuntimeNodes(
            Map<String, SlotAsyncGraphNodeDefinition<C>> definitions,
            List<String> topologicalOrder,
            Map<String, Integer> nodeIndexByName) {
        if (definitions.size() != topologicalOrder.size()) {
            throw new IllegalArgumentException(
                    "definitions/topologicalOrder size mismatch: "
                            + definitions.size() + " vs " + topologicalOrder.size());
        }
        RuntimeNode<C>[] runtimeNodes = (RuntimeNode<C>[]) new RuntimeNode<?>[topologicalOrder.size()];
        for (int i = 0; i < topologicalOrder.size(); i++) {
            String nodeName = topologicalOrder.get(i);
            SlotAsyncGraphNodeDefinition<C> definition = definitions.get(nodeName);
            if (definition == null) {
                throw new IllegalStateException("Missing node definition for topological node: " + nodeName);
            }
            runtimeNodes[i] = RuntimeNode.from(definition, nodeIndexByName);
        }
        return runtimeNodes;
    }

    private static int[][] compileDependentsByNode(RuntimeNode<?>[] nodes) {
        int[] dependentCounts = new int[nodes.length];
        for (RuntimeNode<?> node : nodes) {
            for (int dependencyIndex : node.dependencyIndexes()) {
                dependentCounts[dependencyIndex]++;
            }
        }

        int[][] dependentsByNode = new int[nodes.length][];
        for (int i = 0; i < nodes.length; i++) {
            dependentsByNode[i] = new int[dependentCounts[i]];
        }

        int[] offsets = new int[nodes.length];
        for (int nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++) {
            for (int dependencyIndex : nodes[nodeIndex].dependencyIndexes()) {
                dependentsByNode[dependencyIndex][offsets[dependencyIndex]++] = nodeIndex;
            }
        }
        return dependentsByNode;
    }

    private static int[] compileRemainingDependencies(RuntimeNode<?>[] nodes) {
        int[] remainingDependencies = new int[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            remainingDependencies[i] = nodes[i].dependencyIndexes().length;
        }
        return remainingDependencies;
    }

    private static boolean[] compileTerminalPath(RuntimeNode<?>[] nodes, int terminalNodeIndex) {
        boolean[] terminalPath = new boolean[nodes.length];
        terminalPath[terminalNodeIndex] = true;
        for (int nodeIndex = terminalNodeIndex; nodeIndex >= 0; nodeIndex--) {
            if (!terminalPath[nodeIndex]) {
                continue;
            }
            for (int dependencyIndex : nodes[nodeIndex].dependencyIndexes()) {
                terminalPath[dependencyIndex] = true;
            }
        }
        return terminalPath;
    }

    private static final class GraphFrame<C> {

        private final SlotState slotState;
        private final RuntimeSlotView<C> view;
        private final AtomicReference<C> terminalResult = new AtomicReference<>();
        private final int[] remainingDependencies;
        private final CompletableFuture<C> result = new CompletableFuture<>();

        private GraphFrame(
                C initialContext,
                SlotSymbolTable symbolTable,
                int slotCount,
                int[] initialRemainingDependencies) {
            this.slotState = new SlotState(slotCount);
            this.view = new RuntimeSlotView<>(initialContext, slotState, symbolTable);
            this.remainingDependencies = Arrays.copyOf(
                    initialRemainingDependencies,
                    initialRemainingDependencies.length);
        }
    }

    private record RuntimeSlotView<C>(
            C context,
            SlotState slotState,
            SlotSymbolTable symbolTable) implements ReadOnlySlotContextView<C> {

        @Override
        public boolean hasSlot(int slotId) {
            validateSlotRange(slotId);
            return slotState.hasSlot(slotId);
        }

        @Override
        public boolean hasSlot(String slotSymbol) {
            Objects.requireNonNull(slotSymbol, "slotSymbol");
            return hasSlot(symbolTable.slotIdOf(slotSymbol));
        }

        @Override
        public Object slot(int slotId) {
            validateSlotRange(slotId);
            if (!slotState.hasSlot(slotId)) {
                throw new IllegalStateException("Slot not ready: " + symbolTable.describe(slotId));
            }
            return slotState.slot(slotId);
        }

        @Override
        public Object slot(String slotSymbol) {
            Objects.requireNonNull(slotSymbol, "slotSymbol");
            return slot(symbolTable.slotIdOf(slotSymbol));
        }

        private void validateSlotRange(int slotId) {
            if (slotId < 0 || slotId >= symbolTable.slotCount()) {
                throw new IllegalArgumentException(
                        "slot id out of range: " + slotId + ", slotCount=" + symbolTable.slotCount());
            }
        }
    }
}
