package com.yeven.thread.framework.pipeline;

import java.util.Arrays;
import java.util.Objects;

/**
 * Multi-slot write patch used by slow-path flexible steps.
 *
 * <p>Hot path should prefer one-slot writers. Patch is intended for explicit
 * multi-slot result injection when one step must publish multiple derived values.</p>
 */
public final class SlotPatch {

    private final int size;
    private final int firstSlot;
    private final int secondSlot;
    private final Object firstValue;
    private final Object secondValue;
    private final int[] slotIds;
    private final Object[] values;

    private SlotPatch(
            int size,
            int firstSlot,
            Object firstValue,
            int secondSlot,
            Object secondValue,
            int[] slotIds,
            Object[] values
    ) {
        this.size = size;
        this.firstSlot = firstSlot;
        this.firstValue = firstValue;
        this.secondSlot = secondSlot;
        this.secondValue = secondValue;
        this.slotIds = slotIds;
        this.values = values;
    }

    /**
     * Creates patch for one slot.
     *
     * @param slotId slot index
     * @param value slot value
     * @return slot patch
     */
    public static SlotPatch of(int slotId, Object value) {
        return new SlotPatch(1, slotId, value, -1, null, null, null);
    }

    /**
     * Creates patch for two slots.
     *
     * @param firstSlot first slot index
     * @param firstValue first value
     * @param secondSlot second slot index
     * @param secondValue second value
     * @return slot patch
     */
    public static SlotPatch of(
            int firstSlot,
            Object firstValue,
            int secondSlot,
            Object secondValue
    ) {
        return new SlotPatch(2, firstSlot, firstValue, secondSlot, secondValue, null, null);
    }

    /**
     * Creates patch from arrays.
     *
     * @param slotIds slot indexes
     * @param values slot values
     * @return slot patch
     */
    public static SlotPatch from(int[] slotIds, Object[] values) {
        Objects.requireNonNull(slotIds, "slotIds");
        Objects.requireNonNull(values, "values");
        if (slotIds.length == 0) {
            throw new IllegalArgumentException("slotIds must not be empty");
        }
        if (slotIds.length != values.length) {
            throw new IllegalArgumentException(
                    "slotIds and values length mismatch: " + slotIds.length + " vs " + values.length
            );
        }
        if (slotIds.length == 1) {
            return of(slotIds[0], values[0]);
        }
        if (slotIds.length == 2) {
            return of(slotIds[0], values[0], slotIds[1], values[1]);
        }
        int[] slotCopy = Arrays.copyOf(slotIds, slotIds.length);
        Object[] valueCopy = Arrays.copyOf(values, values.length);
        return new SlotPatch(slotCopy.length, -1, null, -1, null, slotCopy, valueCopy);
    }

    int size() {
        return size;
    }

    int slotIdAt(int index) {
        validateIndex(index);
        if (size == 1) {
            return firstSlot;
        }
        if (size == 2) {
            return index == 0 ? firstSlot : secondSlot;
        }
        return slotIds[index];
    }

    Object valueAt(int index) {
        validateIndex(index);
        if (size == 1) {
            return firstValue;
        }
        if (size == 2) {
            return index == 0 ? firstValue : secondValue;
        }
        return values[index];
    }

    private void validateIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
        }
    }
}
