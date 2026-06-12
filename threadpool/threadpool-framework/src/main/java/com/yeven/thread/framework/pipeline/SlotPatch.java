package com.yeven.thread.framework.pipeline;

import java.util.Arrays;
import java.util.Objects;

/**
 * 多插槽写入补丁，供灵活性要求高的非热点路径步骤使用。
 *
 * <p>
 * 高性能热点路径（Hot Path）应优先使用单插槽写入。
 * 本补丁类适用于一个步骤必须发布多个派生计算值的显式多插槽结果注入场景。
 * </p>
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
