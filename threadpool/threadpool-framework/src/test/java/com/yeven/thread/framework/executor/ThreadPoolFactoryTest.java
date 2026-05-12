package com.yeven.thread.framework.executor;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadPoolFactoryTest {

    @Test
    void shouldCreateLinkedBlockingQueueExecutor() {
        ThreadPoolExecutor executor = ThreadPoolFactory.create(
                "io",
                2,
                4,
                ThreadPoolFactory.QueueType.LINKED_BLOCKING,
                8,
                30,
                new ThreadPoolExecutor.AbortPolicy()
        );
        try {
            assertTrue(executor.getQueue() instanceof LinkedBlockingQueue);
            assertEquals(8, executor.getQueue().remainingCapacity());
        } finally {
            executor.shutdownNow();
        }
    }
}
