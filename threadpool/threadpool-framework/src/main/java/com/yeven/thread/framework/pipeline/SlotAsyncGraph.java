package com.yeven.thread.framework.pipeline;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Immutable slot-based async DAG executor.
 *
 * <p>Runtime data path:
 * 1) write values to {@code Object[] slots},
 * 2) publish availability through {@code readyBits},
 * 3) read through int slot index only.</p>
 *
 * @param <C> base context type
 */
public final class SlotAsyncGraph<C> {

    private final AsyncStepFactory stepFactory;
    private final SlotSymbolTable symbolTable;
    private final RuntimeNode<C>[] nodes;
    private final int[][] dependentsByNode;
    private final int[] initialRemainingDependencies;
    private final boolean[] terminalPath;
    private final int terminalNodeIndex;
    private final SlotGraphMetricsRecorder metricsRecorder;

    SlotAsyncGraph(
            AsyncStepFactory stepFactory,
            SlotSymbolTable symbolTable,
            Map<String, SlotAsyncGraphNodeDefinition<C>> definitions,
            List<String> topologicalOrder,
            String terminalNodeName,
            SlotGraphMetricsRecorder metricsRecorder
    ) {
        this.stepFactory = Objects.requireNonNull(stepFactory, "stepFactory");
        this.symbolTable = Objects.requireNonNull(symbolTable, "symbolTable");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(topologicalOrder, "topologicalOrder");
        Objects.requireNonNull(terminalNodeName, "terminalNodeName");
        this.metricsRecorder = Objects.requireNonNull(metricsRecorder, "metricsRecorder");

        Map<String, Integer> nodeIndexByName = buildNodeIndex(topologicalOrder);
        Integer terminalIndex = nodeIndexByName.get(terminalNodeName);
        if (terminalIndex == null) {
            throw new IllegalStateException("Terminal node not found: " + terminalNodeName);
        }
        this.nodes = compileRuntimeNodes(definitions, topologicalOrder, nodeIndexByName);
        this.terminalNodeIndex = terminalIndex;
        this.dependentsByNode = compileDependentsByNode(nodes);
        this.initialRemainingDependencies = compileRemainingDependencies(nodes);
        this.terminalPath = compileTerminalPath(nodes, terminalIndex);
    }

    /**
     * Executes the graph against one immutable context snapshot.
     *
     * @param initialContext base context
     * @return final context resolved by terminal node
     */
    public CompletableFuture<C> execute(C initialContext) {
        GraphFrame<C> frame = new GraphFrame<>(initialContext, symbolTable.slotCount(), initialRemainingDependencies);
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
            stepFactory.dispatch(node.mode(), () -> {
                executeNode(node, frame.initialContext, frame.slotState, frame.terminalResult);
                return null;
            }).whenComplete((unused, error) -> completeNode(nodeIndex, frame, error));
            return;
        }

        long readyNanos = System.nanoTime();
        AtomicBoolean started = new AtomicBoolean(false);
        CompletableFuture<Void> future = stepFactory.dispatch(node.mode(), () -> {
            started.set(true);
            long startNanos = System.nanoTime();
            try {
                executeNode(node, frame.initialContext, frame.slotState, frame.terminalResult);
                long endNanos = System.nanoTime();
                metricsRecorder.recordNode(
                        node.name(),
                        node.mode(),
                        node.role().name(),
                        startNanos - readyNanos,
                        endNanos - startNanos,
                        true,
                        null
                );
                return null;
            } catch (Throwable error) {
                long endNanos = System.nanoTime();
                metricsRecorder.recordNode(
                        node.name(),
                        node.mode(),
                        node.role().name(),
                        startNanos - readyNanos,
                        endNanos - startNanos,
                        false,
                        error
                );
                throw error;
            }
        });
        future.whenComplete((unused, error) -> {
            if (error != null && !started.get()) {
                metricsRecorder.recordNode(
                        node.name(),
                        node.mode(),
                        node.role().name(),
                        0L,
                        0L,
                        false,
                        error
                );
            }
            completeNode(nodeIndex, frame, error);
        });
    }

    private void completeNode(int nodeIndex, GraphFrame<C> frame, Throwable error) {
        if (error != null) {
            if (terminalPath[nodeIndex]) {
                frame.result.completeExceptionally(error);
            }
            return;
        }

        if (nodeIndex == terminalNodeIndex) {
            C result = frame.terminalResult.get();
            if (result == null) {
                frame.result.completeExceptionally(new IllegalStateException(
                        "Terminal node '" + nodes[terminalNodeIndex].name() + "' did not produce result"
                ));
                return;
            }
            frame.result.complete(result);
        }

        for (int dependentIndex : dependentsByNode[nodeIndex]) {
            int remaining = frame.remainingDependencies.decrementAndGet(dependentIndex);
            if (remaining == 0) {
                dispatchNode(dependentIndex, frame);
            } else if (remaining < 0) {
                frame.result.completeExceptionally(new IllegalStateException(
                        "Node '" + nodes[dependentIndex].name() + "' dependency count underflow"
                ));
            }
        }
    }

    private void executeNode(
            RuntimeNode<C> node,
            C initialContext,
            SlotState slotState,
            AtomicReference<C> terminalResult
    ) {
        for (int slotId : node.readSlots()) {
            if (!slotState.hasSlot(slotId)) {
                throw new IllegalStateException(
                        "Node '" + node.name() + "' reads unavailable slot " + symbolTable.describe(slotId)
                );
            }
        }

        ReadOnlySlotContextView<C> view = new RuntimeSlotView<>(initialContext, slotState, symbolTable);
        if (node.role() == SlotNodeRole.PATCH) {
            SlotPatch patch = Objects.requireNonNull(
                    node.patchEvaluator().apply(view),
                    "Patch evaluator returned null for node: " + node.name()
            );
            applyPatch(node, patch, slotState);
            return;
        }

        C resolvedContext = Objects.requireNonNull(
                node.terminalEvaluator().apply(view),
                "Terminal evaluator returned null for node: " + node.name()
        );
        terminalResult.set(resolvedContext);
    }

    private void applyPatch(RuntimeNode<C> node, SlotPatch patch, SlotState slotState) {
        for (int i = 0; i < patch.size(); i++) {
            int slotId = patch.slotIdAt(i);
            if (slotId < 0 || slotId >= symbolTable.slotCount()) {
                throw new IllegalStateException(
                        "Node '" + node.name() + "' writes out-of-range slot id: " + slotId
                );
            }
            if (Arrays.binarySearch(node.declaredWriteSlotsSorted(), slotId) < 0) {
                throw new IllegalStateException(
                        "Node '" + node.name() + "' writes undeclared slot " + symbolTable.describe(slotId)
                );
            }
            slotState.writeSlot(slotId, patch.valueAt(i));
        }
    }

    private static final class SlotState {

        private final Object[] slots;
        private final AtomicLongArray readyBits;

        private SlotState(int slotCount) {
            this.slots = new Object[slotCount];
            this.readyBits = new AtomicLongArray((slotCount + 63) >>> 6);
        }

        private boolean hasSlot(int slotId) {
            int wordIndex = slotId >>> 6;
            long mask = 1L << (slotId & 63);
            return (readyBits.get(wordIndex) & mask) != 0L;
        }

        private Object slot(int slotId) {
            return slots[slotId];
        }

        private void writeSlot(int slotId, Object value) {
            slots[slotId] = value;
            markReady(slotId);
        }

        private void markReady(int slotId) {
            int wordIndex = slotId >>> 6;
            long mask = 1L << (slotId & 63);
            while (true) {
                long current = readyBits.get(wordIndex);
                long next = current | mask;
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
            SlotNodeRole role,
            java.util.function.Function<ReadOnlySlotContextView<C>, SlotPatch> patchEvaluator,
            java.util.function.Function<ReadOnlySlotContextView<C>, C> terminalEvaluator
    ) {
        private static <C> RuntimeNode<C> from(
                SlotAsyncGraphNodeDefinition<C> definition,
                Map<String, Integer> nodeIndexByName
        ) {
            int[] sortedWriteSlots = definition.getDeclaredWriteSlots();
            Arrays.sort(sortedWriteSlots);
            int[] dependencyIndexes = new int[definition.getDependencies().size()];
            for (int i = 0; i < dependencyIndexes.length; i++) {
                String dependency = definition.getDependencies().get(i);
                Integer dependencyIndex = nodeIndexByName.get(dependency);
                if (dependencyIndex == null) {
                    throw new IllegalStateException(
                            "Node '" + definition.getName() + "' references unknown dependency '" + dependency + "'"
                    );
                }
                dependencyIndexes[i] = dependencyIndex;
            }
            return new RuntimeNode<>(
                    definition.getName(),
                    dependencyIndexes,
                    definition.getMode(),
                    definition.getReadSlots(),
                    sortedWriteSlots,
                    definition.getRole(),
                    definition.getPatchEvaluator(),
                    definition.getTerminalEvaluator()
            );
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
            Map<String, Integer> nodeIndexByName
    ) {
        if (definitions.size() != topologicalOrder.size()) {
            throw new IllegalArgumentException(
                    "definitions/topologicalOrder size mismatch: "
                            + definitions.size() + " vs " + topologicalOrder.size()
            );
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

        private final C initialContext;
        private final SlotState slotState;
        private final AtomicReference<C> terminalResult = new AtomicReference<>();
        private final AtomicIntegerArray remainingDependencies;
        private final CompletableFuture<C> result = new CompletableFuture<>();

        private GraphFrame(C initialContext, int slotCount, int[] initialRemainingDependencies) {
            this.initialContext = initialContext;
            this.slotState = new SlotState(slotCount);
            this.remainingDependencies = new AtomicIntegerArray(initialRemainingDependencies);
        }
    }

    private record RuntimeSlotView<C>(
            C context,
            SlotState slotState,
            SlotSymbolTable symbolTable
    ) implements ReadOnlySlotContextView<C> {

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
                        "slot id out of range: " + slotId + ", slotCount=" + symbolTable.slotCount()
                );
            }
        }
    }
}
