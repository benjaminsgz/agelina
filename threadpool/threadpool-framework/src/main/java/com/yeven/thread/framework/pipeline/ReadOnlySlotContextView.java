package com.yeven.thread.framework.pipeline;

/**
 * 只读上下文与插槽视图接口，暴露给插槽拓扑图的节点处理器（Handlers）。
 *
 * <p>该视图在单次 DAG 执行波次中保持业务上下文 {@code C} 的不可变性，
 * 并提供基于整型插槽索引的 O(1) 复杂度极速数据检索。</p>
 *
 * @param <C> 基础上下文类型
 */
public interface ReadOnlySlotContextView<C> {

    /**
     * 获取传给有向图执行器的原始不可变业务上下文。
     *
     * @return 原始业务上下文对象
     */
    C context();

    /**
     * 判断指定的整型插槽内是否已经存在写入的数据值。
     *
     * @param slotId 插槽整型 ID
     * @return 如果值已写入就绪则返回 true，否则返回 false
     */
    boolean hasSlot(int slotId);

    /**
     * 获取指定整型插槽的值。
     *
     * @param slotId 插槽整型 ID
     * @return 对应插槽中的值对象
     * @throws IllegalStateException 如果该插槽数据尚未写入就绪（即未就绪就被读取）时抛出
     */
    Object slot(int slotId);

    /**
     * 判断指定的符号插槽内是否已经存在写入的数据值。
     *
     * @param slotSymbol 插槽符号名称
     * @return 如果值已写入就绪则返回 true，否则返回 false
     */
    default boolean hasSlot(String slotSymbol) {
        throw new UnsupportedOperationException("Symbolic slot access is not supported by this view");
    }

    /**
     * 获取指定符号插槽的值。
     *
     * @param slotSymbol 插槽符号名称
     * @return 对应插槽中的值对象
     */
    default Object slot(String slotSymbol) {
        throw new UnsupportedOperationException("Symbolic slot access is not supported by this view");
    }

    /**
     * 获取指定整型插槽的值，并自动进行显式类型转换。
     *
     * @param slotId 插槽整型 ID
     * @param type 期望的值类型 Class 对象
     * @param <T> 期望的值类型
     * @return 转换类型后的插槽值对象
     * @throws IllegalStateException 如果类型不匹配或插槽数据尚未就绪
     */
    default <T> T slotAs(int slotId, Class<T> type) {
        Object value = slot(slotId);
        if (!type.isInstance(value)) {
            throw new IllegalStateException(
                    "Slot[" + slotId + "] value type mismatch. expected="
                            + type.getName() + ", actual=" + (value == null ? "null" : value.getClass().getName())
            );
        }
        return type.cast(value);
    }

    /**
     * 获取指定符号插槽的值，并自动进行显式类型转换。
     *
     * @param slotSymbol 插槽符号名称
     * @param type 期望的值类型 Class 对象
     * @param <T> 期望的值类型
     * @return 转换类型后的插槽值对象
     * @throws IllegalStateException 如果类型不匹配或插槽数据尚未就绪
     */
    default <T> T slotAs(String slotSymbol, Class<T> type) {
        Object value = slot(slotSymbol);
        if (!type.isInstance(value)) {
            throw new IllegalStateException(
                    "Slot[" + slotSymbol + "] value type mismatch. expected="
                            + type.getName() + ", actual=" + (value == null ? "null" : value.getClass().getName())
            );
        }
        return type.cast(value);
    }
}
