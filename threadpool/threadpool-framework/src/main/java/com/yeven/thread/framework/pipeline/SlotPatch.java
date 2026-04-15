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

    private final int[] slotIds;
    private final Object[] values;

    private SlotPatch(int[] slotIds, Object[] values) {
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
        return new SlotPatch(new int[]{slotId}, new Object[]{value});
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
        return new SlotPatch(
                new int[]{firstSlot, secondSlot},
                new Object[]{firstValue, secondValue}
        );
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
        int[] slotCopy = Arrays.copyOf(slotIds, slotIds.length);
        Object[] valueCopy = Arrays.copyOf(values, values.length);
        return new SlotPatch(slotCopy, valueCopy);
    }

    int size() {
        return slotIds.length;
    }

    int slotIdAt(int index) {
        return slotIds[index];
    }

    Object valueAt(int index) {
        return values[index];
    }
}
