package com.yeven.thread.framework.pipeline.slot;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 符号插槽表，用于将整型的插槽 ID 翻译为人类可读的字符串符号名称。
 *
 * <p>在 DAG 实际执行期间，仅使用整型插槽索引。符号插槽表主要在启动校验、诊断和指标监控中使用。</p>
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
     * 创建一个包含匿名（默认占位符）插槽名称的符号表。
     *
     * @param slotCount 总插槽数
     * @return 符号表实例
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
     * 根据显式定义的槽索引与名称的映射关系创建符号表。
     *
     * @param slotCount 总插槽数
     * @param symbolsBySlot 槽索引到符号名称的映射 Map
     * @return 符号表实例
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
     * 创建一个支持自动递增分配插槽 ID 的动态符号表构建器。
     *
     * @return 构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取总的插槽数量。
     *
     * @return 插槽总数
     */
    public int slotCount() {
        return symbols.length;
    }

    /**
     * 获取指定插槽 ID 对应的符号名称。
     *
     * @param slotId 插槽索引
     * @return 对应的符号名称
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
     * 根据符号名称获取其对应的插槽 ID。
     *
     * @param symbol 插槽符号名称
     * @return 整型插槽 ID
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
     * 判断当前符号表中是否包含某个符号名称。
     *
     * @param symbol 插槽符号名称
     * @return 存在则返回 true，否则返回 false
     */
    public boolean containsSymbol(String symbol) {
        if (symbol == null) {
            return false;
        }
        return slotIdBySymbol.containsKey(symbol);
    }

    /**
     * 获取用于问题诊断和异常描述的插槽描述。如果越界，则安全地降级返回 "slot[id]"。
     *
     * @param slotId 插槽索引
     * @return 描述字符串
     */
    public String describe(int slotId) {
        if (slotId < 0 || slotId >= symbols.length) {
            return "slot[" + slotId + "]";
        }
        return symbols[slotId];
    }

    /**
     * 获取一份符号数组的只读快照。
     *
     * @return 符号字符串数组快照
     */
    public String[] snapshot() {
        return Arrays.copyOf(symbols, symbols.length);
    }

    /**
     * 获取符号名称与插槽 ID 的只读映射表。
     *
     * @return 映射 Map
     */
    public Map<String, Integer> slotIdMap() {
        return slotIdBySymbol;
    }

    /**
     * 符号插槽表的构建器，支持通过符号名称动态分配插槽 ID。
     */
    public static final class Builder {

        private final LinkedHashMap<String, Integer> slotIdBySymbol = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * 获取已分配的插槽 ID，如果该符号名称第一次被请求，则自动分配一个全新的递增 ID。
         *
         * @param symbol 插槽符号名称
         * @return 对应的或新分配的整型插槽 ID
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
         * 获取某个符号名称对应的整型插槽 ID。
         *
         * @param symbol 插槽符号名称
         * @return 对应的插槽 ID
         * @throws IllegalArgumentException 当请求的符号名称未在构建器中注册分配过时抛出
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
         * 获取当前已注册分配的插槽数量。
         *
         * @return 已分配的插槽数
         */
        public int size() {
            return slotIdBySymbol.size();
        }

        /**
         * 根据已注册的映射关系构建不可变的符号插槽表 {@link SlotSymbolTable}。
         *
         * @return 符号插槽表实例
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
