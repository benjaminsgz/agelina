package com.yeven.thread.framework.pipeline;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Symbol table for translating numeric slot id into human-readable names.
 *
 * <p>Execution uses integer slot index only. Symbol table is used for startup
 * validation and diagnostics.</p>
 */
public final class SlotSymbolTable {

    private final String[] symbols;
    private final Map<String, Integer> slotIdBySymbol;

    private SlotSymbolTable(String[] symbols) {
        this.symbols = symbols;
        LinkedHashMap<String, Integer> reverse = new LinkedHashMap<>(symbols.length);
        for (int i = 0; i < symbols.length; i++) {
            String symbol = symbols[i];
            Integer previous = reverse.put(symbol, i);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate slot symbol detected: '" + symbol + "' for slots "
                                + previous + " and " + i
                );
            }
        }
        this.slotIdBySymbol = Collections.unmodifiableMap(reverse);
    }

    /**
     * Creates one symbol table with anonymous names.
     *
     * @param slotCount total slot count
     * @return symbol table
     */
    public static SlotSymbolTable anonymous(int slotCount) {
        if (slotCount <= 0) {
            throw new IllegalArgumentException("slotCount must be positive");
        }
        String[] names = new String[slotCount];
        for (int i = 0; i < slotCount; i++) {
            names[i] = "slot[" + i + "]";
        }
        return new SlotSymbolTable(names);
    }

    /**
     * Creates one symbol table from explicit names.
     *
     * @param slotCount total slot count
     * @param symbolsBySlot slot index to symbol name map
     * @return symbol table
     */
    public static SlotSymbolTable named(int slotCount, Map<Integer, String> symbolsBySlot) {
        Objects.requireNonNull(symbolsBySlot, "symbolsBySlot");
        if (slotCount <= 0) {
            throw new IllegalArgumentException("slotCount must be positive");
        }
        String[] names = anonymous(slotCount).symbols;
        for (Map.Entry<Integer, String> entry : symbolsBySlot.entrySet()) {
            int slotId = entry.getKey();
            if (slotId < 0 || slotId >= slotCount) {
                throw new IllegalArgumentException(
                        "slot id out of range: " + slotId + ", slotCount=" + slotCount
                );
            }
            String symbol = entry.getValue();
            if (symbol == null || symbol.isBlank()) {
                throw new IllegalArgumentException("symbol must not be blank for slot " + slotId);
            }
            names[slotId] = symbol;
        }
        return new SlotSymbolTable(names);
    }

    /**
     * Creates one dynamic symbol table builder.
     *
     * @return builder with auto-increment slot allocation
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return total slot count
     */
    public int slotCount() {
        return symbols.length;
    }

    /**
     * Returns one slot symbol.
     *
     * @param slotId slot index
     * @return symbol
     */
    public String symbolOf(int slotId) {
        if (slotId < 0 || slotId >= symbols.length) {
            throw new IllegalArgumentException(
                    "slot id out of range: " + slotId + ", slotCount=" + symbols.length
            );
        }
        return symbols[slotId];
    }

    /**
     * Returns slot id by symbol name.
     *
     * @param symbol slot symbol name
     * @return slot id
     */
    public int slotIdOf(String symbol) {
        Objects.requireNonNull(symbol, "symbol");
        Integer slotId = slotIdBySymbol.get(symbol);
        if (slotId == null) {
            throw new IllegalArgumentException("Unknown slot symbol: " + symbol);
        }
        return slotId;
    }

    /**
     * Returns whether one symbol exists in the table.
     *
     * @param symbol slot symbol name
     * @return true if symbol exists
     */
    public boolean containsSymbol(String symbol) {
        if (symbol == null) {
            return false;
        }
        return slotIdBySymbol.containsKey(symbol);
    }

    /**
     * Returns one safe slot symbol for diagnostics.
     *
     * @param slotId slot index
     * @return symbol or fallback
     */
    public String describe(int slotId) {
        if (slotId < 0 || slotId >= symbols.length) {
            return "slot[" + slotId + "]";
        }
        return symbols[slotId];
    }

    /**
     * @return immutable symbol snapshot
     */
    public String[] snapshot() {
        return Arrays.copyOf(symbols, symbols.length);
    }

    /**
     * @return immutable symbol->slotId mapping
     */
    public Map<String, Integer> slotIdMap() {
        return slotIdBySymbol;
    }

    /**
     * Builder for allocating slot id by symbolic name.
     */
    public static final class Builder {

        private final LinkedHashMap<String, Integer> slotIdBySymbol = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Returns existing slot id or allocates a new id for the symbol.
         *
         * @param symbol slot symbol
         * @return slot id
         */
        public int getOrAllocate(String symbol) {
            validateSymbol(symbol);
            Integer existing = slotIdBySymbol.get(symbol);
            if (existing != null) {
                return existing;
            }
            int nextId = slotIdBySymbol.size();
            slotIdBySymbol.put(symbol, nextId);
            return nextId;
        }

        /**
         * Returns allocated slot id.
         *
         * @param symbol slot symbol
         * @return slot id
         */
        public int slotIdOf(String symbol) {
            validateSymbol(symbol);
            Integer slotId = slotIdBySymbol.get(symbol);
            if (slotId == null) {
                throw new IllegalArgumentException("Unknown slot symbol: " + symbol);
            }
            return slotId;
        }

        /**
         * @return current allocated slot count
         */
        public int size() {
            return slotIdBySymbol.size();
        }

        /**
         * Builds immutable symbol table from allocated entries.
         *
         * @return symbol table
         */
        public SlotSymbolTable build() {
            if (slotIdBySymbol.isEmpty()) {
                throw new IllegalStateException("No slot symbol allocated");
            }
            String[] symbols = new String[slotIdBySymbol.size()];
            for (Map.Entry<String, Integer> entry : slotIdBySymbol.entrySet()) {
                symbols[entry.getValue()] = entry.getKey();
            }
            return new SlotSymbolTable(symbols);
        }

        private static void validateSymbol(String symbol) {
            if (symbol == null || symbol.isBlank()) {
                throw new IllegalArgumentException("slot symbol must not be blank");
            }
        }
    }
}
