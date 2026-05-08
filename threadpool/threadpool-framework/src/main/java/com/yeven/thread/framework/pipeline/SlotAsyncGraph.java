package com.yeven.thread.framework.pipeline;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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
    private final Map<String, RuntimeNode<C>> nodes;
    private final List<String> topologicalOrder;
    private final String terminalNodeName;

    SlotAsyncGraph(
            AsyncStepFactory stepFactory,
            SlotSymbolTable symbolTable,
            Map<String, SlotAsyncGraphNodeDefinition<C>> definitions,
            List<String> topologicalOrder,
            String terminalNodeName
    ) {
        this.stepFactory = Objects.requireNonNull(stepFactory, "stepFactory");
        this.symbolTable = Objects.requireNonNull(symbolTable, "symbolTable");
        this.topologicalOrder = List.copyOf(topologicalOrder);
        this.terminalNodeName = Objects.requireNonNull(terminalNodeName, "terminalNodeName");

        LinkedHashMap<String, RuntimeNode<C>> runtimeNodes = new LinkedHashMap<>(definitions.size());
        for (Map.Entry<String, SlotAsyncGraphNodeDefinition<C>> entry : definitions.entrySet()) {
            runtimeNodes.put(entry.getKey(), RuntimeNode.from(entry.getValue()));
        }
        this.nodes = Map.copyOf(runtimeNodes);
    }

    /**
     * Executes the graph against one immutable context snapshot.
     *
     * @param initialContext base context
     * @return final context resolved by terminal node
     */
    public CompletableFuture<C> execute(C initialContext) {
        SlotState slotState = new SlotState(symbolTable.slotCount());
        AtomicReference<C> terminalResult = new AtomicReference<>();
        Map<String, CompletableFuture<Void>> futures = new LinkedHashMap<>(topologicalOrder.size());

        for (String nodeName : topologicalOrder) {
            RuntimeNode<C> node = nodes.get(nodeName);
            List<String> depNames = node.dependencies();
            CompletableFuture<?>[] dependencies = new CompletableFuture[depNames.size()];
            for (int i = 0; i < depNames.size(); i++) {
                String dep = depNames.get(i);
                CompletableFuture<Void> depFuture = futures.get(dep);
                if (depFuture == null) {
                    throw new IllegalStateException(
                            "Node '" + node.name() + "' references unknown dependency future '" + dep + "'"
                    );
                }
                dependencies[i] = depFuture;
            }

            CompletableFuture<Void> nodeFuture = CompletableFuture.allOf(dependencies)
                    .thenCompose(unused -> stepFactory.dispatch(node.mode(), () -> {
                        executeNode(node, initialContext, slotState, terminalResult);
                        return null;
                    }));
            futures.put(nodeName, nodeFuture);
        }

        CompletableFuture<Void> terminalFuture = futures.get(terminalNodeName);
        if (terminalFuture == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Terminal node not found: " + terminalNodeName)
            );
        }
        return terminalFuture.thenApply(unused -> {
            C result = terminalResult.get();
            if (result == null) {
                throw new IllegalStateException("Terminal node '" + terminalNodeName + "' did not produce result");
            }
            return result;
        });
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
            List<String> dependencies,
            com.yeven.thread.framework.executor.ExecutionMode mode,
            int[] readSlots,
            int[] declaredWriteSlotsSorted,
            SlotNodeRole role,
            java.util.function.Function<ReadOnlySlotContextView<C>, SlotPatch> patchEvaluator,
            java.util.function.Function<ReadOnlySlotContextView<C>, C> terminalEvaluator
    ) {
        private static <C> RuntimeNode<C> from(SlotAsyncGraphNodeDefinition<C> definition) {
            int[] sortedWriteSlots = definition.getDeclaredWriteSlots();
            Arrays.sort(sortedWriteSlots);
            return new RuntimeNode<>(
                    definition.getName(),
                    definition.getDependencies(),
                    definition.getMode(),
                    definition.getReadSlots(),
                    sortedWriteSlots,
                    definition.getRole(),
                    definition.getPatchEvaluator(),
                    definition.getTerminalEvaluator()
            );
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
