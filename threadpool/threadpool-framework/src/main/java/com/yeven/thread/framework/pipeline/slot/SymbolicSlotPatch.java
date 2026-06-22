package com.yeven.thread.framework.pipeline.slot;

import java.util.Arrays;
import java.util.Objects;

/**
 * 通过符号名称（Symbolic Slot Name）定位的多插槽写入补丁。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>可读性与开发友好：</b> 直接使用整型插槽 ID 编写代码容易出错且可读性差。本类提供了 API 层的符号名称语义糖，使开发人员可以使用清晰易懂的字符串标识（例如 "userInfo", "paymentResult"）进行多插槽结果的构造。</li>
 *   <li><b>编译期降维：</b> 该类并不参与实际运行时的执行数据存取。在 DAG 实际调度启动前，构建器会结合符号表将其翻译并编译为零开销的 {@link SlotPatch}，在保证了可读性的同时完美兼顾了极致性能。</li>
 * </ul>
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

    public int size() {
        return slotSymbols.length;
    }

    public String slotSymbolAt(int index) {
        return slotSymbols[index];
    }

    public Object valueAt(int index) {
        return values[index];
    }
}
