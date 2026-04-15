package com.yeven.thread.framework.pipeline;

import java.util.Arrays;
import java.util.Objects;

/**
 * Multi-slot write patch addressed by symbolic slot names.
 *
 * <p>This class is API-level sugar. It is converted to {@link SlotPatch}
 * before execution.</p>
 */
public final class SymbolicSlotPatch {

    private final String[] slotSymbols;
    private final Object[] values;

    private SymbolicSlotPatch(String[] slotSymbols, Object[] values) {
        this.slotSymbols = slotSymbols;
        this.values = values;
    }

    /**
     * Creates one symbolic patch from arrays.
     *
     * @param slotSymbols slot symbols
     * @param values slot values
     * @return symbolic patch
     */
    public static SymbolicSlotPatch from(String[] slotSymbols, Object[] values) {
        Objects.requireNonNull(slotSymbols, "slotSymbols");
        Objects.requireNonNull(values, "values");
        if (slotSymbols.length == 0) {
            throw new IllegalArgumentException("slotSymbols must not be empty");
        }
        if (slotSymbols.length != values.length) {
            throw new IllegalArgumentException(
                    "slotSymbols and values length mismatch: " + slotSymbols.length + " vs " + values.length
            );
        }
        String[] symbolCopy = Arrays.copyOf(slotSymbols, slotSymbols.length);
        Object[] valueCopy = Arrays.copyOf(values, values.length);
        for (String slotSymbol : symbolCopy) {
            if (slotSymbol == null || slotSymbol.isBlank()) {
                throw new IllegalArgumentException("slot symbol must not be blank");
            }
        }
        return new SymbolicSlotPatch(symbolCopy, valueCopy);
    }

    /**
     * Creates patch for one symbolic slot.
     *
     * @param slotSymbol slot symbol
     * @param value slot value
     * @return symbolic patch
     */
    public static SymbolicSlotPatch of(String slotSymbol, Object value) {
        return from(new String[]{slotSymbol}, new Object[]{value});
    }

    /**
     * Creates patch for two symbolic slots.
     */
    public static SymbolicSlotPatch of(
            String firstSlotSymbol,
            Object firstValue,
            String secondSlotSymbol,
            Object secondValue
    ) {
        return from(
                new String[]{firstSlotSymbol, secondSlotSymbol},
                new Object[]{firstValue, secondValue}
        );
    }

    int size() {
        return slotSymbols.length;
    }

    String slotSymbolAt(int index) {
        return slotSymbols[index];
    }

    Object valueAt(int index) {
        return values[index];
    }
}
