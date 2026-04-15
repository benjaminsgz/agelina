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
 * Builder for slot-based async graph.
 *
 * <p>Design goals:
 * 1) hot path uses int slot indexing only,
 * 2) startup rejects slot write collisions,
 * 3) startup validates read-slot dependency closure.</p>
 *
 * @param <C> base context type
 */
public final class SlotAsyncGraphBuilder<C> {

    private final AsyncStepFactory stepFactory;
    private final SlotSymbolTable fixedSymbolTable;
    private final SlotSymbolTable.Builder dynamicSymbolBuilder;
    private final Map<String, SlotAsyncGraphNodeDefinition<C>> definitions = new LinkedHashMap<>();
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
     * <p>Use string-based APIs to read/write slots and let builder assign int slot ids.</p>
     *
     * @param stepFactory async step factory
     */
    public SlotAsyncGraphBuilder(AsyncStepFactory stepFactory) {
        this(stepFactory, null, SlotSymbolTable.builder());
    }

    private SlotAsyncGraphBuilder(
            AsyncStepFactory stepFactory,
            SlotSymbolTable fixedSymbolTable,
            SlotSymbolTable.Builder dynamicSymbolBuilder
    ) {
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
            Function<ReadOnlySlotContextView<C>, Object> evaluator
    ) {
        Objects.requireNonNull(evaluator, "evaluator");
        return addPatchStep(
                name,
                dependencies,
                mode,
                readSlots,
                new int[]{writeSlot},
                view -> SlotPatch.of(writeSlot, evaluator.apply(view))
        );
    }

    /**
     * Adds one one-slot writer node with symbolic slot names.
     *
     * @param name node name
     * @param dependencies dependency node names
     * @param mode execution mode
     * @param readSlotSymbols read slot symbols
     * @param writeSlotSymbol write slot symbol
     * @param evaluator slot evaluator
     * @return same builder
     */
    public synchronized SlotAsyncGraphBuilder<C> addSlotStep(
            String name,
            List<String> dependencies,
            ExecutionMode mode,
            List<String> readSlotSymbols,
            String writeSlotSymbol,
            Function<ReadOnlySlotContextView<C>, Object> evaluator
    ) {
        return addSlotStep(
                name,
                dependencies,
                mode,
                resolveSlotIds(readSlotSymbols),
                resolveOrAllocateSlotId(writeSlotSymbol),
                evaluator
        );
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
            Function<ReadOnlySlotContextView<C>, SlotPatch> evaluator
    ) {
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
                evaluator
        ));
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
            Function<ReadOnlySlotContextView<C>, SlotPatch> evaluator
    ) {
        return addPatchStep(
                name,
                dependencies,
                mode,
                resolveSlotIds(readSlotSymbols),
                resolveSlotIds(writeSlotSymbols),
                evaluator
        );
    }

    /**
     * Adds one multi-slot writer node with symbolic slots and symbolic patch return value.
     *
     * <p>This is API-friendly sugar and is converted to int-indexed {@link SlotPatch}.</p>
     */
    public synchronized SlotAsyncGraphBuilder<C> addSymbolicPatchStep(
            String name,
            List<String> dependencies,
            ExecutionMode mode,
            List<String> readSlotSymbols,
            List<String> writeSlotSymbols,
            Function<ReadOnlySlotContextView<C>, SymbolicSlotPatch> evaluator
    ) {
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
                        writeSlotIdBySymbol
                )
        );
    }

    /**
     * Adds one terminal context resolver node.
     */
    public synchronized SlotAsyncGraphBuilder<C> addTerminalStep(
            String name,
            List<String> dependencies,
            ExecutionMode mode,
            int[] readSlots,
            Function<ReadOnlySlotContextView<C>, C> evaluator
    ) {
        if (terminalNodeName != null) {
            throw new IllegalStateException(
                    "Only one terminal node is allowed. Existing=" + terminalNodeName + ", new=" + name
            );
        }
        validateSlotIndexes(readSlots, "readSlots", name);
        addDefinition(SlotAsyncGraphNodeDefinition.terminalNode(
                name,
                mode,
                normalizeDependencies(dependencies),
                readSlots,
                evaluator
        ));
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
            Function<ReadOnlySlotContextView<C>, C> evaluator
    ) {
        return addTerminalStep(name, dependencies, mode, resolveSlotIds(readSlotSymbols), evaluator);
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
                terminalNodeName
        );
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
                            "Node '" + definition.getName() + "' depends on missing node '" + dependency + "'"
                    );
                }
            }
        }
    }

    private List<String> buildTopologicalOrder() {
        List<String> order = new ArrayList<>(definitions.size());
        Map<String, VisitState> states = new LinkedHashMap<>();
        Deque<String> path = new ArrayDeque<>();
        for (String nodeName : definitions.keySet()) {
            if (states.get(nodeName) == null) {
                dfs(nodeName, states, path, order);
            }
        }
        return order;
    }

    private void dfs(
            String nodeName,
            Map<String, VisitState> states,
            Deque<String> path,
            List<String> order
    ) {
        states.put(nodeName, VisitState.VISITING);
        path.addLast(nodeName);
        for (String dependency : definitions.get(nodeName).getDependencies()) {
            VisitState state = states.get(dependency);
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
            if (state == null) {
                dfs(dependency, states, path, order);
            }
        }
        path.removeLast();
        states.put(nodeName, VisitState.VISITED);
        order.add(nodeName);
    }

    private void validateSlotContracts(SlotSymbolTable symbolTable) {
        Map<Integer, String> producerBySlot = new LinkedHashMap<>();
        for (SlotAsyncGraphNodeDefinition<C> definition : definitions.values()) {
            if (definition.getRole() != SlotNodeRole.PATCH) {
                continue;
            }
            for (int slotId : definition.getDeclaredWriteSlots()) {
                String existing = producerBySlot.putIfAbsent(slotId, definition.getName());
                if (existing != null) {
                    throw new IllegalStateException(
                            "Slot write collision on " + symbolTable.describe(slotId)
                                    + ". Existing writer=" + existing + ", new writer=" + definition.getName()
                    );
                }
            }
        }
    }

    private void validateReadSlotDependencyClosure(SlotSymbolTable symbolTable) {
        Map<Integer, String> producerBySlot = new LinkedHashMap<>();
        for (SlotAsyncGraphNodeDefinition<C> definition : definitions.values()) {
            if (definition.getRole() == SlotNodeRole.PATCH) {
                for (int slotId : definition.getDeclaredWriteSlots()) {
                    producerBySlot.put(slotId, definition.getName());
                }
            }
        }

        Map<String, Set<String>> ancestorsByNode = buildAncestorsByNode();
        for (SlotAsyncGraphNodeDefinition<C> definition : definitions.values()) {
            Set<String> ancestors = ancestorsByNode.get(definition.getName());
            for (int slotId : definition.getReadSlots()) {
                String producer = producerBySlot.get(slotId);
                if (producer == null) {
                    throw new IllegalStateException(
                            "Node '" + definition.getName() + "' reads unbound slot " + symbolTable.describe(slotId)
                    );
                }
                if (!ancestors.contains(producer)) {
                    throw new IllegalStateException(
                            "Node '" + definition.getName() + "' reads " + symbolTable.describe(slotId)
                                    + " from '" + producer + "' without dependency path"
                    );
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
                        "Node '" + nodeName + "' has invalid " + field + " slot id: " + slotId
                );
            }
        }
        int[] sorted = Arrays.copyOf(slotIds, slotIds.length);
        Arrays.sort(sorted);
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] == sorted[i - 1]) {
                throw new IllegalArgumentException(
                        "Node '" + nodeName + "' has duplicated slot id in " + field + ": " + sorted[i]
                );
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
                    slotCount
            );
        }
    }

    private static void validateSlotRange(String nodeName, String field, int[] slotIds, int slotCount) {
        for (int slotId : slotIds) {
            if (slotId < 0 || slotId >= slotCount) {
                throw new IllegalArgumentException(
                        "Node '" + nodeName + "' has invalid " + field + " slot id: "
                                + slotId + ", slotCount=" + slotCount
                );
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
            Map<String, Integer> writeSlotIdBySymbol
    ) {
        int[] slotIds = new int[symbolicPatch.size()];
        Object[] values = new Object[symbolicPatch.size()];
        for (int i = 0; i < symbolicPatch.size(); i++) {
            String slotSymbol = symbolicPatch.slotSymbolAt(i);
            Integer slotId = writeSlotIdBySymbol.get(slotSymbol);
            if (slotId == null) {
                throw new IllegalStateException(
                        "Symbolic patch writes undeclared slot symbol '" + slotSymbol + "'"
                );
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
