package com.yeven.thread.framework.graph;

import com.yeven.thread.framework.factory.AsyncStepFactory;
import com.yeven.thread.framework.table.SlotSymbolTable;
import com.yeven.thread.framework.definition.SlotAsyncGraphNodeDefinition;
import com.yeven.thread.framework.hook.SlotGraphMetricsRecorder;
import com.yeven.thread.framework.hook.NoopSlotGraphMetricsRecorder;
import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.constant.SlotNodeRole;
import com.yeven.thread.framework.pipeline.ReadOnlySlotContextView;
import com.yeven.thread.framework.pipeline.SlotPatch;
import com.yeven.thread.framework.runtime.RuntimeNode;
import com.yeven.thread.framework.runtime.SlotState;
import com.yeven.thread.framework.runtime.GraphFrame;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于插槽（Slot）的高性能不可变异步有向无环图（DAG）执行器。
 *
 * <p>
 * <b>设计必要性与核心价值：</b>
 * </p>
 * <ul>
 * <li><b>消除运行时并发锁与哈希开销：</b> 传统 DAG 执行器采用并发 Map（如
 * {@code ConcurrentHashMap}）作为上下文或临时数据的容器，并在节点间传递数据。这会引入显着的哈希计算开销、内存分配以及潜在的锁争用。本类在设计上使用固定大小的原生
 * {@code Object[] slots} 数组和无锁并发位集 {@code readyBits}，通过整型索引存取数据，使热路径（Hot
 * Path）的读写操作达到 O(1) 的超高性能。</li>
 * <li><b>无堆分配（Zero-Heap-Allocation）调度逻辑：</b> 与为每个节点创建包装
 * {@code CompletableFuture} 的方案相比，本执行器配合 {@link AsyncStepFactory} 使用低开销的
 * {@link com.yeven.thread.framework.executor.NodeCompletion}
 * 回调，直接与底层线程池进行事件驱动的交互，极大减轻了高频调用时的内存垃圾回收（GC）开销。</li>
 * <li><b>基于 VarHandle 的轻量级依赖削减：</b>
 * 将各节点的拓扑关系和入度在图编译期（构造时）预先计算并固化在不可变数组中。在运行期节点执行结束时，使用
 * {@link java.lang.invoke.VarHandle}
 * 对整型计数器数组进行原子减一（CAS）操作，以安全、轻量、无锁地触发后续节点的并发调度。</li>
 * <li><b>智能 Fail-Fast 机制：</b>
 * 通过编译期计算出的终点必经路径（{@code terminalPath}），如果非必经路径上的节点发生失败，不影响主流程；若必经路径上的关键节点执行出现异常，则立即触发
 * DAG 异常结束，提供极佳 of 的响应效率与容错隔离性。</li>
 * </ul>
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
        // [生命周期帧初始化]：为本次 DAG 图的单独执行创建全新的 GraphFrame 运行帧，
        // 隔离多次运行间的状态，其中包含专有的插槽存储区及当前执行计数器数组。
        GraphFrame<C> frame = new GraphFrame<>(
                initialContext,
                symbolTable,
                symbolTable.slotCount(),
                initialRemainingDependencies);
        // [零入度根节点激活]：遍历查找所有入度依赖数为 0 的根节点，作为图执行的并发入口，
        // 立即开始分发调度。这些节点将并发启动并在不同的线程中运行。
        for (int nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++) {
            if (initialRemainingDependencies[nodeIndex] == 0) {
                dispatchNode(nodeIndex, frame);
            }
        }
        return frame.result;
    }

    private void dispatchNode(int nodeIndex, GraphFrame<C> frame) {
        RuntimeNode<C> node = nodes[nodeIndex];
        // [免指标测量调度通道]：当指标记录器处于 Noop 空操作状态时，直接走极速通道，
        // 避免了时间戳获取（System.nanoTime()）与测量对象的额外分配，实现极致的吞吐性能。
        if (metricsRecorder == NoopSlotGraphMetricsRecorder.INSTANCE) {
            stepFactory.dispatchNode(
                    node.mode(),
                    () -> executeNode(node, frame),
                    error -> completeNode(nodeIndex, frame, error));
            return;
        }

        // [带指标测量调度通道]：需要统计并记录各个节点的等待队列耗时与实际执行耗时。
        // 通过 started 标记以及 readyNanos 起始时间，精确测算任务在线程池排队以及真实运行的时长。
        long readyNanos = System.nanoTime();
        AtomicBoolean started = new AtomicBoolean(false);
        stepFactory.dispatchNode(
                node.mode(),
                () -> executeMeasuredNode(node, frame, readyNanos, started),
                error -> {
                    // 若节点在提交线程池排队或准备阶段就发生异常（例如线程池拒绝执行），
                    // 并且任务未能真正开始（started == false），则及时记录失败的指标。
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
            // [Fail-Fast 异常短路]：如果节点发生异常，并且该节点处于到达终点节点的必经路径上（terminalPath），
            // 则直接标记当前运行帧的整个结果 CompletableFuture 为异常完成，使客户端能以最快速度感知到不可恢复的错误。
            if (terminalPath[nodeIndex]) {
                frame.result.completeExceptionally(error);
            }
            return;
        }

        // [出口节点判定与完结]：若当前执行完毕的节点正好是指定的 DAG 终点节点，
        // 则从终端结果容器中取出汇聚后的上下文结果，正式完成整个 DAG 流水线的 CompletableFuture。
        if (nodeIndex == terminalNodeIndex) {
            C result = frame.terminalResult.get();
            if (result == null) {
                frame.result.completeExceptionally(new IllegalStateException(
                        "Terminal node '" + nodes[terminalNodeIndex].name() + "' did not produce result"));
                return;
            }
            frame.result.complete(result);
        }

        // [无锁下游激活与依赖递减]：遍历当前节点完成后需要通知的所有下游依赖节点，
        // 使用 VarHandle 对下游节点的入度计数器进行原子递减，返回旧值。
        for (int dependentIndex : dependentsByNode[nodeIndex]) {
            // 原子减 1 操作（无锁 CAS），确保多线程并发更新依赖计数时的线程安全性与内存可见性
            int previous = (int) INT_ARRAY_HANDLE.getAndAdd(frame.remainingDependencies, dependentIndex, -1);
            int remaining = previous - 1;
            // [依赖就绪判定]：当依赖计数器正好减至 0，说明该下游节点的所有前置拓扑条件全部达成，
            // 立即分发该下游节点，驱动图继续向前流转。
            if (remaining == 0) {
                dispatchNode(dependentIndex, frame);
            } else if (remaining < 0) {
                // 如果计数器下溢为负数，表明出现了系统级的计数逻辑错误，强行中断并反馈异常
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
}
