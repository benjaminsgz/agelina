package com.yeven.thread.framework.pipeline;

/**
 * Read-only context + slot view exposed to slot graph handlers.
 *
 * <p>The view keeps business context immutable during one execution wave and provides
 * O(1) slot lookup by integer index.</p>
 *
 * @param <C> base context type
 */
public interface ReadOnlySlotContextView<C> {

    /**
     * @return original immutable context passed to graph execution
     */
    C context();

    /**
     * Returns whether one slot already has a value.
     *
     * @param slotId slot index
     * @return true if value is available
     */
    boolean hasSlot(int slotId);

    /**
     * Reads one slot value.
     *
     * @param slotId slot index
     * @return slot value
     * @throws IllegalStateException if the slot is not ready
     */
    Object slot(int slotId);

    /**
     * Returns whether one symbolic slot already has a value.
     *
     * @param slotSymbol slot symbol
     * @return true if value is available
     */
    default boolean hasSlot(String slotSymbol) {
        throw new UnsupportedOperationException("Symbolic slot access is not supported by this view");
    }

    /**
     * Reads one symbolic slot value.
     *
     * @param slotSymbol slot symbol
     * @return slot value
     */
    default Object slot(String slotSymbol) {
        throw new UnsupportedOperationException("Symbolic slot access is not supported by this view");
    }

    /**
     * Reads one slot value with explicit type cast.
     *
     * @param slotId slot index
     * @param type expected slot value type
     * @param <T> expected slot value type
     * @return typed slot value
     */
    default <T> T slotAs(int slotId, Class<T> type) {
        Object value = slot(slotId);
        if (!type.isInstance(value)) {
            throw new IllegalStateException(
                    "Slot[" + slotId + "] value type mismatch. expected="
                            + type.getName() + ", actual=" + value.getClass().getName()
            );
        }
        return type.cast(value);
    }

    /**
     * Reads one symbolic slot value with explicit type cast.
     *
     * @param slotSymbol slot symbol
     * @param type expected slot value type
     * @param <T> expected slot value type
     * @return typed slot value
     */
    default <T> T slotAs(String slotSymbol, Class<T> type) {
        Object value = slot(slotSymbol);
        if (!type.isInstance(value)) {
            throw new IllegalStateException(
                    "Slot[" + slotSymbol + "] value type mismatch. expected="
                            + type.getName() + ", actual=" + value.getClass().getName()
            );
        }
        return type.cast(value);
    }
}
