package com.yeven.thread.framework.runtime;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SlotState 单元测试类。
 * 包含单线程存取测试、并发位图更新测试、以及跨 64 位 Long 边界的测试。
 * 遵循 AAA (Arrange, Act, Assert) 模式。
 */
class SlotStateTest {

    @Test
    void testSingleThreadedWriteAndRead() {
        // Arrange (准备数据/对象)
        int slotCount = 10;
        SlotState state = new SlotState(slotCount);

        // Act (执行被测方法)
        state.writeSlot(0, "value0");
        state.writeSlot(9, "value9");

        // Assert (断言预期结果)
        // 验证写入的数据与就绪位图状态
        assertTrue(state.hasSlot(0), "第 0 个槽应被标记为已就绪");
        assertFalse(state.hasSlot(1), "第 1 个槽应未就绪");
        assertTrue(state.hasSlot(9), "第 9 个槽应被标记为已就绪");

        assertEquals("value0", state.slot(0), "第 0 个槽获取的值应正确");
        assertNull(state.slot(1), "第 1 个槽值应为 null");
        assertEquals("value9", state.slot(9), "第 9 个槽获取的值应正确");
    }

    @Test
    void testCrossLongBoundary() {
        // Arrange
        // 槽位总数为 70（跨越第一个 Long 的 64 位限制）
        int slotCount = 70;
        SlotState state = new SlotState(slotCount);

        // Act
        // 写入第 65 个插槽（索引为 64，位于第二个 Long 中）
        state.writeSlot(64, "value64");
        // 写入第 70 个插槽（索引为 69，同样位于第二个 Long 中）
        state.writeSlot(69, "value69");
        // 写入第 5 个插槽（索引为 4，位于第一个 Long 中）
        state.writeSlot(4, "value4");

        // Assert
        assertTrue(state.hasSlot(4), "第 4 个槽（位于第 1 个 Word）应就绪");
        assertTrue(state.hasSlot(64), "第 64 个槽（位于第 2 个 Word）应就绪");
        assertTrue(state.hasSlot(69), "第 69 个槽（位于第 2 个 Word）应就绪");
        assertFalse(state.hasSlot(63), "第 63 个槽（位于第 1 个 Word 边界）应未就绪");

        assertEquals("value4", state.slot(4));
        assertEquals("value64", state.slot(64));
        assertEquals("value69", state.slot(69));
    }

    @Test
    void testMultiThreadedBitmapConcurrency() throws InterruptedException {
        // Arrange
        // 准备一个包含 64 个插槽的状态机，所有写入操作都会落入同一个 Long 字段中，造成激烈的 CAS 竞争
        int slotCount = 64;
        SlotState state = new SlotState(slotCount);
        int threadCount = 32;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // 提交 32 个线程，每个线程负责写入两个不同的槽（总共 64 个槽）
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    // 等待发令枪响，保证并发冲突的最大化
                    startLatch.await();
                    // 写入两个槽位，触发 CAS 自旋
                    state.writeSlot(threadIndex * 2, "val" + (threadIndex * 2));
                    state.writeSlot(threadIndex * 2 + 1, "val" + (threadIndex * 2 + 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Act
        // 发令枪响
        startLatch.countDown();
        // 等待所有线程执行完毕
        boolean completed = endLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertTrue(completed, "并发测试线程未在规定时间内执行完毕");
        // 校验 64 个槽位数据全部就绪，且值完全正确，验证并发 CAS 位掩码操作的正确性
        for (int i = 0; i < slotCount; i++) {
            assertTrue(state.hasSlot(i), "并发写入后，第 " + i + " 个插槽应该已就绪");
            assertEquals("val" + i, state.slot(i), "并发写入后，第 " + i + " 个插槽的值应正确");
        }
    }
}
