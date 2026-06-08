package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.ExecutionMode;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Declarative node definition for slot-based async graph execution.
 *
 * @param <C> base context type
 */
final class SlotAsyncGraphNodeDefinition<C> {

    private final String name;
    private final ExecutionMode mode;
    private final List<String> dependencies;
    private final int[] readSlots;
    private final int[] declaredWriteSlots;
    private final SlotNodeRole role;
    private final Function<ReadOnlySlotContextView<C>, Object> slotEvaluator;
    private final Function<ReadOnlySlotContextView<C>, SlotPatch> patchEvaluator;
    private final Function<ReadOnlySlotContextView<C>, C> terminalEvaluator;

    private SlotAsyncGraphNodeDefinition(
            String name,
            ExecutionMode mode,
            List<String> dependencies,
            int[] readSlots,
            int[] declaredWriteSlots,
            SlotNodeRole role,
            Function<ReadOnlySlotContextView<C>, Object> slotEvaluator,
            Function<ReadOnlySlotContextView<C>, SlotPatch> patchEvaluator,
            Function<ReadOnlySlotContextView<C>, C> terminalEvaluator
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        this.readSlots = Arrays.copyOf(Objects.requireNonNull(readSlots, "readSlots"), readSlots.length);
        this.declaredWriteSlots = Arrays.copyOf(
                Objects.requireNonNull(declaredWriteSlots, "declaredWriteSlots"),
                declaredWriteSlots.length
        );
        this.role = Objects.requireNonNull(role, "role");
        this.slotEvaluator = slotEvaluator;
        this.patchEvaluator = patchEvaluator;
        this.terminalEvaluator = terminalEvaluator;
    }

    static <C> SlotAsyncGraphNodeDefinition<C> slotNode(
            String name,
            ExecutionMode mode,
            List<String> dependencies,
            int[] readSlots,
            int writeSlot,
            Function<ReadOnlySlotContextView<C>, Object> evaluator
    ) {
        return new SlotAsyncGraphNodeDefinition<>(
                name,
                mode,
                dependencies,
                readSlots,
                new int[]{writeSlot},
                SlotNodeRole.PATCH,
                Objects.requireNonNull(evaluator, "evaluator"),
                null,
                null
        );
    }

    static <C> SlotAsyncGraphNodeDefinition<C> patchNode(
            String name,
            ExecutionMode mode,
            List<String> dependencies,
            int[] readSlots,
            int[] declaredWriteSlots,
            Function<ReadOnlySlotContextView<C>, SlotPatch> evaluator
    ) {
        return new SlotAsyncGraphNodeDefinition<>(
                name,
                mode,
                dependencies,
                readSlots,
                declaredWriteSlots,
                SlotNodeRole.PATCH,
                null,
                Objects.requireNonNull(evaluator, "evaluator"),
                null
        );
    }

    static <C> SlotAsyncGraphNodeDefinition<C> terminalNode(
            String name,
            ExecutionMode mode,
            List<String> dependencies,
            int[] readSlots,
            Function<ReadOnlySlotContextView<C>, C> evaluator
    ) {
        return new SlotAsyncGraphNodeDefinition<>(
                name,
                mode,
                dependencies,
                readSlots,
                new int[0],
                SlotNodeRole.TERMINAL,
                null,
                null,
                Objects.requireNonNull(evaluator, "evaluator")
        );
    }

    String getName() {
        return name;
    }

    ExecutionMode getMode() {
        return mode;
    }

    List<String> getDependencies() {
        return dependencies;
    }

    int[] getReadSlots() {
        return Arrays.copyOf(readSlots, readSlots.length);
    }

    int[] getDeclaredWriteSlots() {
        return Arrays.copyOf(declaredWriteSlots, declaredWriteSlots.length);
    }

    SlotNodeRole getRole() {
        return role;
    }

    Function<ReadOnlySlotContextView<C>, Object> getSlotEvaluator() {
        return slotEvaluator;
    }

    Function<ReadOnlySlotContextView<C>, SlotPatch> getPatchEvaluator() {
        return patchEvaluator;
    }

    Function<ReadOnlySlotContextView<C>, C> getTerminalEvaluator() {
        return terminalEvaluator;
    }
}

enum SlotNodeRole {
    PATCH,
    TERMINAL
}
