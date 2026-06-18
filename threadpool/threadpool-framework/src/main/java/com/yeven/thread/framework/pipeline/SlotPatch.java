package com.yeven.thread.framework.pipeline;

import java.util.Arrays;
import java.util.Objects;

/**
 * 多插槽写入补丁，用于在一个节点中需要并发输出多个计算值的场景。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>多插槽联合输出封装：</b> 当某个 DAG 节点计算出多个关联派生值（例如解析一个复合响应并发布到多个独立的数据槽中），为了遵守“单个处理器触发”和拓扑图的高效表达，需要使用补丁形式将这些值进行统一打包写入。</li>
 *   <li><b>高频小对象内存优化：</b> 针对 1 个和 2 个插槽的补丁进行了极致的代码特化（Specialization）优化，直接存储在类的物理字段（`firstSlot`, `firstValue` 等）中，完全规避了临时整型/对象数组的二次分配与内存对齐开销。仅在插槽数大于 2 时退化为数组存储，充分体现了框架对内存垃圾产生控制的苛刻要求。</li>
 * </ul>
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
            Object[] values) {
        this.size = size;
        this.firstSlot = firstSlot;
        this.firstValue = firstValue;
        this.secondSlot = secondSlot;
        this.secondValue = secondValue;
        this.slotIds = slotIds;
        this.values = values;
    }

    /**
     * 为单个插槽创建写入补丁。
     *
     * @param slotId 插槽整型 ID
     * @param value  写入值
     * @return 插槽写入补丁对象
     */
    public static SlotPatch of(int slotId, Object value) {
        return new SlotPatch(1, slotId, value, -1, null, null, null);
    }

    /**
     * 为两个插槽创建写入补丁。
     *
     * @param firstSlot   第一个插槽的整型 ID
     * @param firstValue  第一个值
     * @param secondSlot  第二个插槽的整型 ID
     * @param secondValue 第二个值
     * @return 插槽写入补丁对象
     */
    public static SlotPatch of(
            int firstSlot,
            Object firstValue,
            int secondSlot,
            Object secondValue) {
        return new SlotPatch(2, firstSlot, firstValue, secondSlot, secondValue, null, null);
    }

    /**
     * 根据整型 ID 数组与值数组创建通用多插槽写入补丁。
     *
     * @param slotIds 插槽整型 ID 数组
     * @param values  对应的写入值数组
     * @return 插槽写入补丁对象
     */
    public static SlotPatch from(int[] slotIds, Object[] values) {
        Objects.requireNonNull(slotIds, "slotIds");
        Objects.requireNonNull(values, "values");
        if (slotIds.length == 0) {
            throw new IllegalArgumentException("slotIds must not be empty");
        }
        if (slotIds.length != values.length) {
            throw new IllegalArgumentException(
                    "slotIds and values length mismatch: " + slotIds.length + " vs " + values.length);
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

    public int size() {
        return size;
    }

    public int slotIdAt(int index) {
        validateIndex(index);
        if (size == 1) {
            return firstSlot;
        }
        if (size == 2) {
            return index == 0 ? firstSlot : secondSlot;
        }
        return slotIds[index];
    }

    public Object valueAt(int index) {
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
