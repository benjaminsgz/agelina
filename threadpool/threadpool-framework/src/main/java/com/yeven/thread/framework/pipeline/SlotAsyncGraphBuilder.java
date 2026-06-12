package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.ExecutionMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 基于插槽（Slot）的异步有向无环图（DAG）构建器。
 *
 * <p>
 * 设计核心目标：
 * </p>
 * 
 * <pre>
 * 1) 极速热路径（Hot Path）：在运行期，节点间的数据传递与读取完全通过整型（int）插槽索引直接定位，免去 Map 的查找开销。
 * 2) 启动期强校验（Fail-Fast）：在图构建时（build()）严格拒绝插槽写冲突（即只允许单写入者 Single Writer）。
 * 3) 闭包图关系验证（Dependency Closure）：在启动期对所有读槽步骤的拓扑依赖路径进行验证，确保槽的读取者必须拓扑依赖于该槽的写入者。
 * </pre>
 *
 * @param <C> 基础上下文类型
 */
public final class SlotAsyncGraphBuilder<C> {

    private final AsyncStepFactory stepFactory;
    private final SlotSymbolTable fixedSymbolTable;
    private final SlotSymbolTable.Builder dynamicSymbolBuilder;
    private final Map<String, SlotAsyncGraphNodeDefinition<C>> definitions = new LinkedHashMap<>();
    private SlotGraphMetricsRecorder metricsRecorder = SlotGraphMetricsRecorder.noop();
    private String terminalNodeName;

    public SlotAsyncGraphBuilder(AsyncStepFactory stepFactory, int slotCount) {
        this(stepFactory, SlotSymbolTable.anonymous(slotCount));
    }

    public SlotAsyncGraphBuilder(AsyncStepFactory stepFactory, SlotSymbolTable symbolTable) {
        this(stepFactory, Objects.requireNonNull(symbolTable, "symbolTable"), null);
    }

    /**
     * Creates one builder with symbolic slot auto-allocation.
     *
     * <p>
     * Use string-based APIs to read/write slots and let builder assign int slot
     * ids.
     * </p>
     *
     * @param stepFactory async step factory
     */
    public SlotAsyncGraphBuilder(AsyncStepFactory stepFactory) {
        this(stepFactory, null, SlotSymbolTable.builder());
    }

    private SlotAsyncGraphBuilder(
            AsyncStepFactory stepFactory,
            SlotSymbolTable fixedSymbolTable,
            SlotSymbolTable.Builder dynamicSymbolBuilder) {
        this.stepFactory = Objects.requireNonNull(stepFactory, "stepFactory");
        this.fixedSymbolTable = fixedSymbolTable;
        this.dynamicSymbolBuilder = dynamicSymbolBuilder;
    }

    /**
     * Adds one one-slot writer node.
     */
    public synchronized SlotAsyncGraphBuilder<C> addSlotStep(
            String name,
            List<String> dependencies,
            ExecutionMode mode,
            int[] readSlots,
            int writeSlot,
            Function<ReadOnlySlotContextView<C>, Object> evaluator) {
        Objects.requireNonNull(evaluator, "evaluator");
        validateSlotIndexes(readSlots, "readSlots", name);
        validateSlotIndexes(new int[] { writeSlot }, "writeSlot", name);
        addDefinition(SlotAsyncGraphNodeDefinition.slotNode(
                name,
                mode,
                normalizeDependencies(dependencies),
                readSlots,
                writeSlot,
                evaluator));
        return this;
    }

    /**
     * Adds one one-slot writer node with symbolic slot names.
     *
     * @param name            node name
     * @param dependencies    dependency node names
     * @param mode            execution mode
     * @param readSlotSymbols read slot symbols
     * @param writeSlotSymbol write slot symbol
     * @param evaluator       slot evaluator
     * @return same builder
     */
    public synchronized SlotAsyncGraphBuilder<C> addSlotStep(
            String name,
            List<String> dependencies,
            ExecutionMode mode,
            List<String> readSlotSymbols,
            String writeSlotSymbol,
            Function<ReadOnlySlotContextView<C>, Object> evaluator) {
        return addSlotStep(
                name,
                dependencies,
                mode,
                resolveSlotIds(readSlotSymbols),
                resolveOrAllocateSlotId(writeSlotSymbol),
                evaluator);
    }

    /**
     * Adds one multi-slot writer node.
     */
    public synchronized SlotAsyncGraphBuilder<C> addPatchStep(
            String name,
            List<String> dependencies,
            ExecutionMode mode,
            int[] readSlots,
            int[] declaredWriteSlots,
            Function<ReadOnlySlotContextView<C>, SlotPatch> evaluator) {
        validateSlotIndexes(readSlots, "readSlots", name);
        validateSlotIndexes(declaredWriteSlots, "declaredWriteSlots", name);
        if (declaredWriteSlots.length == 0) {
            throw new IllegalArgumentException("declaredWriteSlots must not be empty for node: " + name);
        }
        addDefinition(SlotAsyncGraphNodeDefinition.patchNode(
                name,
                mode,
                normalizeDependencies(dependencies),
                readSlots,
                declaredWriteSlots,
                evaluator));
        return this;
    }

    /**
     * Adds one multi-slot writer node with symbolic slot names.
     */
    public synchronized SlotAsyncGraphBuilder<C> addPatchStep(
            String name,
            List<String> dependencies,
            ExecutionMode mode,
            List<String> readSlotSymbols,
            List<String> writeSlotSymbols,
            Function<ReadOnlySlotContextView<C>, SlotPatch> evaluator) {
        return addPatchStep(
                name,
                dependencies,
                mode,
                resolveSlotIds(readSlotSymbols),
                resolveSlotIds(writeSlotSymbols),
                evaluator);
    }

    /**
     * Adds one multi-slot writer node with symbolic slots and symbolic patch return
     * value.
     *
     * <p>
     * This is API-friendly sugar and is converted to int-indexed {@link SlotPatch}.
     * </p>
     */
    public synchronized SlotAsyncGraphBuilder<C> addSymbolicPatchStep(
            String name,
            List<String> dependencies,
            ExecutionMode mode,
            List<String> readSlotSymbols,
            List<String> writeSlotSymbols,
            Function<ReadOnlySlotContextView<C>, SymbolicSlotPatch> evaluator) {
        Objects.requireNonNull(evaluator, "evaluator");
        Objects.requireNonNull(writeSlotSymbols, "writeSlotSymbols");
        int[] readSlotIds = resolveSlotIds(readSlotSymbols);
        int[] declaredWriteSlotIds = resolveSlotIds(writeSlotSymbols);
        Map<String, Integer> writeSlotIdBySymbol = new LinkedHashMap<>(writeSlotSymbols.size());
        for (int i = 0; i < writeSlotSymbols.size(); i++) {
            String slotSymbol = writeSlotSymbols.get(i);
            if (slotSymbol == null || slotSymbol.isBlank()) {
                throw new IllegalArgumentException("writeSlotSymbols contains blank symbol");
            }
            Integer previous = writeSlotIdBySymbol.put(slotSymbol, declaredWriteSlotIds[i]);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate write slot symbol: " + slotSymbol);
            }
        }
        return addPatchStep(
                name,
                dependencies,
                mode,
                readSlotIds,
                declaredWriteSlotIds,
                view -> toSlotPatch(
                        Objects.requireNonNull(evaluator.apply(view), "symbolic patch must not be null"),
                        writeSlotIdBySymbol));
    }

    /**
     * Adds one terminal context resolver node.
     */
    public synchronized SlotAsyncGraphBuilder<C> addTerminalStep(
            String name,
            List<String> dependencies,
            ExecutionMode mode,
            int[] readSlots,
            Function<ReadOnlySlotContextView<C>, C> evaluator) {
        if (terminalNodeName != null) {
            throw new IllegalStateException(
                    "Only one terminal node is allowed. Existing=" + terminalNodeName + ", new=" + name);
        }
        validateSlotIndexes(readSlots, "readSlots", name);
        addDefinition(SlotAsyncGraphNodeDefinition.terminalNode(
                name,
                mode,
                normalizeDependencies(dependencies),
                readSlots,
                evaluator));
        terminalNodeName = name;
        return this;
    }

    /**
     * Adds one terminal node with symbolic slot names.
     */
    public synchronized SlotAsyncGraphBuilder<C> addTerminalStep(
            String name,
            List<String> dependencies,
            ExecutionMode mode,
            List<String> readSlotSymbols,
            Function<ReadOnlySlotContextView<C>, C> evaluator) {
        return addTerminalStep(name, dependencies, mode, resolveSlotIds(readSlotSymbols), evaluator);
    }

    /**
     * Installs a metrics recorder for runtime node execution.
     *
     * <p>
     * The default recorder is no-op, so applications that do not need metrics pay
     * no timestamp cost.
     * </p>
     *
     * @param metricsRecorder metrics sink
     * @return same builder
     */
    public synchronized SlotAsyncGraphBuilder<C> withMetricsRecorder(SlotGraphMetricsRecorder metricsRecorder) {
        this.metricsRecorder = Objects.requireNonNull(metricsRecorder, "metricsRecorder");
        return this;
    }

    /**
     * Builds immutable slot graph after full contract validation.
     */
    public synchronized SlotAsyncGraph<C> build() {
        if (terminalNodeName == null) {
            throw new IllegalStateException("Missing terminal node. Call addTerminalStep(...)");
        }
        SlotSymbolTable symbolTable = resolveSymbolTable();
        validateDependencies();
        validateTerminalIsGraphExit();
        List<String> topologicalOrder = buildTopologicalOrder();
        validateSlotIndexesInRange(symbolTable);
        validateSlotContracts(symbolTable);
        validateReadSlotDependencyClosure(symbolTable);

        Map<String, SlotAsyncGraphNodeDefinition<C>> nodes = new LinkedHashMap<>(definitions);
        return new SlotAsyncGraph<>(
                stepFactory,
                symbolTable,
                nodes,
                topologicalOrder,
                terminalNodeName,
                metricsRecorder);
    }

    private void addDefinition(SlotAsyncGraphNodeDefinition<C> definition) {
        Objects.requireNonNull(definition, "definition");
        SlotAsyncGraphNodeDefinition<C> previous = definitions.putIfAbsent(definition.getName(), definition);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate node name: " + definition.getName());
        }
    }

    private List<String> normalizeDependencies(List<String> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return List.of();
        }
        for (String dependency : dependencies) {
            if (dependency == null || dependency.isBlank()) {
                throw new IllegalArgumentException("dependency must not be blank");
            }
        }
        return List.copyOf(dependencies);
    }

    private void validateDependencies() {
        for (SlotAsyncGraphNodeDefinition<C> definition : definitions.values()) {
            for (String dependency : definition.getDependencies()) {
                if (!definitions.containsKey(dependency)) {
                    throw new IllegalArgumentException(
                            "Node '" + definition.getName() + "' depends on missing node '" + dependency + "'");
                }
            }
        }
    }

    /**
     * 校验终点节点必须是真正的图出口。
     *
     * <p>如果允许终点节点之后继续挂下游节点，{@code execute()} 可能已经返回成功，
     * 但图中仍有额外节点在后台运行或失败，破坏调用方对完成状态的判断。</p>
     */
    private void validateTerminalIsGraphExit() {
        for (SlotAsyncGraphNodeDefinition<C> definition : definitions.values()) {
            if (definition.getName().equals(terminalNodeName)) {
                continue;
            }
            if (definition.getDependencies().contains(terminalNodeName)) {
                throw new IllegalStateException(
                        "Terminal node '" + terminalNodeName + "' must be graph exit, but '"
                                + definition.getName() + "' depends on it");
            }
        }
    }

    /**
     * 生成 DAG 的拓扑排序序列，并在生成过程中检测是否存在循环依赖。
     */
    private List<String> buildTopologicalOrder() {
        List<String> order = new ArrayList<>(definitions.size());
        Map<String, VisitState> states = new LinkedHashMap<>();
        Deque<String> path = new ArrayDeque<>(); // 用于记录当前 DFS 遍历路径，辅助打印循环依赖链
        for (String nodeName : definitions.keySet()) {
            if (states.get(nodeName) == null) {
                dfs(nodeName, states, path, order);
            }
        }
        return order;
    }

    /**
     * 深度优先搜索（DFS）辅助函数，利用三色标记法实现环检测与拓扑排序输出。
     */
    private void dfs(
            String nodeName,
            Map<String, VisitState> states,
            Deque<String> path,
            List<String> order) {
        // 将节点标记为 "正在访问" (VISITING)
        states.put(nodeName, VisitState.VISITING);
        path.addLast(nodeName);
        // 遍历当前节点的所有前置依赖节点
        for (String dependency : definitions.get(nodeName).getDependencies()) {
            VisitState state = states.get(dependency);
            // 如果前置依赖节点状态也是 VISITING，说明在深度遍历路径中相遇，存在循环依赖（环）
            if (state == VisitState.VISITING) {
                StringBuilder cycle = new StringBuilder();
                boolean inCycle = false;
                for (String entry : path) {
                    if (entry.equals(dependency)) {
                        inCycle = true;
                    }
                    if (inCycle) {
                        if (!cycle.isEmpty()) {
                            cycle.append(" -> ");
                        }
                        cycle.append(entry);
                    }
                }
                cycle.append(" -> ").append(dependency);
                throw new IllegalArgumentException("Cycle detected: " + cycle);
            }
            // 如果尚未访问过，继续递归深度遍历
            if (state == null) {
                dfs(dependency, states, path, order);
            }
        }
        path.removeLast();
        // 标记为 "访问完毕" (VISITED)
        states.put(nodeName, VisitState.VISITED);
        // 将访问完毕的节点加入拓扑排序结果中（前置节点先完成访问，最后输出的即为拓扑序列）
        order.add(nodeName);
    }

    /**
     * 校验插槽的单写入者协议（Single Writer Principle），拒绝任何插槽写入冲突。
     */
    private void validateSlotContracts(SlotSymbolTable symbolTable) {
        Map<Integer, String> producerBySlot = new LinkedHashMap<>();
        for (SlotAsyncGraphNodeDefinition<C> definition : definitions.values()) {
            // 只有可能修改/生产插槽值的节点（即 ROLE_PATCH 节点）参与写入校验
            if (definition.getRole() != SlotNodeRole.PATCH) {
                continue;
            }
            for (int slotId : definition.getDeclaredWriteSlots()) {
                // 如果发现该插槽已经被其他节点声明写入，直接抛出异常（写冲突）
                String existing = producerBySlot.putIfAbsent(slotId, definition.getName());
                if (existing != null) {
                    throw new IllegalStateException(
                            "Slot write collision on " + symbolTable.describe(slotId)
                                    + ". Existing writer=" + existing + ", new writer=" + definition.getName());
                }
            }
        }
    }

    /**
     * 校验读槽步骤的拓扑依赖闭包。
     * 
     * <p>
     * 如果节点 A 声明要读取插槽 X，而插槽 X 是由节点 B 写入的，
     * 那么在 DAG 的节点依赖关系中，A 必须拓扑依赖于 B（即存在从 B 到 A 的直接或间接依赖路径）。
     * 这确保了在节点 A 启动执行并读取插槽 X 时，节点 B 必定已经执行完毕并将插槽 X 写入完毕。
     * </p>
     */
    private void validateReadSlotDependencyClosure(SlotSymbolTable symbolTable) {
        // 第一步：收集每一个插槽 ID 的写入者节点名称
        Map<Integer, String> producerBySlot = new LinkedHashMap<>();
        for (SlotAsyncGraphNodeDefinition<C> definition : definitions.values()) {
            if (definition.getRole() == SlotNodeRole.PATCH) {
                for (int slotId : definition.getDeclaredWriteSlots()) {
                    producerBySlot.put(slotId, definition.getName());
                }
            }
        }

        // 第二步：通过 DFS 缓存出每个节点的所有前置祖先节点集合
        Map<String, Set<String>> ancestorsByNode = buildAncestorsByNode();

        // 第三步：验证每个读槽依赖 of 闭包性
        for (SlotAsyncGraphNodeDefinition<C> definition : definitions.values()) {
            Set<String> ancestors = ancestorsByNode.get(definition.getName());
            for (int slotId : definition.getReadSlots()) {
                String producer = producerBySlot.get(slotId);
                // 槽不能没有写入者（未绑定槽）
                if (producer == null) {
                    throw new IllegalStateException(
                            "Node '" + definition.getName() + "' reads unbound slot " + symbolTable.describe(slotId));
                }
                // 如果当前读节点的祖先节点集合中不包含写入者节点，则属于违规读取（会造成并发读写 Race Condition）
                if (!ancestors.contains(producer)) {
                    throw new IllegalStateException(
                            "Node '" + definition.getName() + "' reads " + symbolTable.describe(slotId)
                                    + " from '" + producer + "' without dependency path");
                }
            }
        }
    }

    private Map<String, Set<String>> buildAncestorsByNode() {
        Map<String, Set<String>> memo = new LinkedHashMap<>();
        for (String nodeName : definitions.keySet()) {
            collectAncestors(nodeName, memo);
        }
        return memo;
    }

    private Set<String> collectAncestors(String nodeName, Map<String, Set<String>> memo) {
        Set<String> cached = memo.get(nodeName);
        if (cached != null) {
            return cached;
        }
        LinkedHashSet<String> ancestors = new LinkedHashSet<>();
        for (String dependency : definitions.get(nodeName).getDependencies()) {
            ancestors.add(dependency);
            ancestors.addAll(collectAncestors(dependency, memo));
        }
        Set<String> immutable = Set.copyOf(ancestors);
        memo.put(nodeName, immutable);
        return immutable;
    }

    private void validateSlotIndexes(int[] slotIds, String field, String nodeName) {
        Objects.requireNonNull(slotIds, field);
        for (int slotId : slotIds) {
            if (slotId < 0) {
                throw new IllegalArgumentException(
                        "Node '" + nodeName + "' has invalid " + field + " slot id: " + slotId);
            }
        }
        int[] sorted = Arrays.copyOf(slotIds, slotIds.length);
        Arrays.sort(sorted);
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] == sorted[i - 1]) {
                throw new IllegalArgumentException(
                        "Node '" + nodeName + "' has duplicated slot id in " + field + ": " + sorted[i]);
            }
        }
    }

    private void validateSlotIndexesInRange(SlotSymbolTable symbolTable) {
        int slotCount = symbolTable.slotCount();
        for (SlotAsyncGraphNodeDefinition<C> definition : definitions.values()) {
            validateSlotRange(definition.getName(), "readSlots", definition.getReadSlots(), slotCount);
            validateSlotRange(
                    definition.getName(),
                    "declaredWriteSlots",
                    definition.getDeclaredWriteSlots(),
                    slotCount);
        }
    }

    private static void validateSlotRange(String nodeName, String field, int[] slotIds, int slotCount) {
        for (int slotId : slotIds) {
            if (slotId < 0 || slotId >= slotCount) {
                throw new IllegalArgumentException(
                        "Node '" + nodeName + "' has invalid " + field + " slot id: "
                                + slotId + ", slotCount=" + slotCount);
            }
        }
    }

    private SlotSymbolTable resolveSymbolTable() {
        if (fixedSymbolTable != null) {
            return fixedSymbolTable;
        }
        return dynamicSymbolBuilder.build();
    }

    private int[] resolveSlotIds(List<String> slotSymbols) {
        if (slotSymbols == null || slotSymbols.isEmpty()) {
            return new int[0];
        }
        int[] slotIds = new int[slotSymbols.size()];
        for (int i = 0; i < slotSymbols.size(); i++) {
            slotIds[i] = resolveOrAllocateSlotId(slotSymbols.get(i));
        }
        return slotIds;
    }

    private int resolveOrAllocateSlotId(String slotSymbol) {
        if (slotSymbol == null || slotSymbol.isBlank()) {
            throw new IllegalArgumentException("slot symbol must not be blank");
        }
        if (fixedSymbolTable != null) {
            return fixedSymbolTable.slotIdOf(slotSymbol);
        }
        return dynamicSymbolBuilder.getOrAllocate(slotSymbol);
    }

    private static SlotPatch toSlotPatch(
            SymbolicSlotPatch symbolicPatch,
            Map<String, Integer> writeSlotIdBySymbol) {
        int[] slotIds = new int[symbolicPatch.size()];
        Object[] values = new Object[symbolicPatch.size()];
        for (int i = 0; i < symbolicPatch.size(); i++) {
            String slotSymbol = symbolicPatch.slotSymbolAt(i);
            Integer slotId = writeSlotIdBySymbol.get(slotSymbol);
            if (slotId == null) {
                throw new IllegalStateException(
                        "Symbolic patch writes undeclared slot symbol '" + slotSymbol + "'");
            }
            slotIds[i] = slotId.intValue();
            values[i] = symbolicPatch.valueAt(i);
        }
        return SlotPatch.from(slotIds, values);
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
