package com.yeven.thread.framework.pipeline;

import java.util.Arrays;
import java.util.Objects;

/**
 * 通过符号名称（Symbolic Slot Name）定位的多插槽写入补丁。
 *
 * <p>此类提供了 API 层面的语法糖，方便开发人员使用可读的槽名称编写代码。
 * 在 DAG 实际运行执行前，它会被自动转换并编译为基于整型索引定位的 {@link SlotPatch}。</p>
 */
public final class SymbolicSlotPatch {

    private final String[] slotSymbols;
    private final Object[] values;

    private SymbolicSlotPatch(String[] slotSymbols, Object[] values) {
        this.slotSymbols = slotSymbols;
        this.values = values;
    }

    /**
     * 根据符号名称数组与值数组创建多插槽符号写入补丁。
     *
     * @param slotSymbols 插槽符号名称数组
     * @param values 对应的写入值数组
     * @return 符号写入补丁对象
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
     * 为单个符号插槽创建写入补丁。
     *
     * @param slotSymbol 插槽符号名称
     * @param value 写入值
     * @return 符号写入补丁对象
     */
    public static SymbolicSlotPatch of(String slotSymbol, Object value) {
        return from(new String[]{slotSymbol}, new Object[]{value});
    }

    /**
     * 为两个符号插槽创建写入补丁。
     *
     * @param firstSlotSymbol 第一个插槽的符号名称
     * @param firstValue 第一个值
     * @param secondSlotSymbol 第二个插槽的符号名称
     * @param secondValue 第二个值
     * @return 符号写入补丁对象
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
