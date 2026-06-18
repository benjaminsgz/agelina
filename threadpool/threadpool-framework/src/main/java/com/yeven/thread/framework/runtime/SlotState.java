package com.yeven.thread.framework.runtime;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 运行期状态存储，维护每个插槽（Slot）的具体数据以及就绪状态的位集。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>无锁就绪标记与数据存储：</b> 底层使用 {@code Object[]} 数组和无锁并发位集来存储各插槽值，保障图的极速执行和并发读写安全性。</li>
 * </ul>
 */
public final class SlotState {

    // 存储实际插槽值的 Object 数组，通过固定的槽索引进行直接存取，消除 Map 查找开销
    private final Object[] slots;
    // 基于 AtomicLongArray 的无锁就绪标记位集。每个 Long 占用 64 位，对应 64 个插槽的状态，提供高效并发位操作
    private final AtomicLongArray readyBits;

    public SlotState(int slotCount) {
        this.slots = new Object[slotCount];
        // 计算位集数组所需大小：每个位代表一个插槽就绪状态，向上取整以 64 位为一组（无符号右移 6 位）
        this.readyBits = new AtomicLongArray((slotCount + 63) >>> 6);
    }

    /**
     * 检查指定的插槽 ID 数据是否已写入就绪。
     */
    public boolean hasSlot(int slotId) {
        int wordIndex = slotId >>> 6; // 计算定位到第几个 Long（相当于除以 64）
        long mask = 1L << (slotId & 63); // 构造该插槽在 Long 中的对应位掩码（相当于取模 64）
        return (readyBits.get(wordIndex) & mask) != 0L; // 用原子读配合位掩码判断该位是否被置为 1
    }

    /**
     * 获取指定插槽 ID 的值。
     */
    public Object slot(int slotId) {
        return slots[slotId];
    }

    /**
     * 向指定插槽 ID 写入值，并将其标记为就绪状态。
     */
    public void writeSlot(int slotId, Object value) {
        slots[slotId] = value;
        markReady(slotId);
    }

    /**
     * 在位集中原子的将指定的插槽 ID 标记为就绪（无锁 CAS 自旋更新位图）。
     */
    private void markReady(int slotId) {
        int wordIndex = slotId >>> 6; // 定位到 Long 数组的索引
        long mask = 1L << (slotId & 63); // 定位到该位掩码
        while (true) {
            long current = readyBits.get(wordIndex);
            long next = current | mask; // 将对应位置为 1
            // [自旋 CAS 置位]：如果该位已经是 1，或者 CAS 替换成功，则置位成功，退出循环；
            // else 说明有其他线程并发修改该 Long，自旋重试，保证操作原子性与线程安全。
            if (current == next || readyBits.compareAndSet(wordIndex, current, next)) {
                return;
            }
        }
    }
}
