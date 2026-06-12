package com.yeven.thread.spring.boot.autoconfigure;

import com.yeven.thread.framework.executor.ThreadPoolFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThreadPoolPropertiesTest {

    @Test
    void shouldAcceptValidLinkedBlockingConfiguration() {
        ThreadPoolProperties.Pool pool = new ThreadPoolProperties.Pool();
        pool.setCoreSize(4);
        pool.setMaxSize(8);
        pool.setQueueType(ThreadPoolFactory.QueueType.LINKED_BLOCKING);
        pool.setQueueCapacity(16);
        pool.setKeepAliveSeconds(30);
        pool.setRejectionPolicy(ThreadPoolProperties.RejectionPolicy.CALLER_RUNS);

        assertDoesNotThrow(() -> pool.validate("io"));
    }

    @Test
    void shouldRejectMaxSizeSmallerThanCoreSize() {
        ThreadPoolProperties.Pool pool = new ThreadPoolProperties.Pool();
        pool.setCoreSize(8);
        pool.setMaxSize(4);
        pool.setQueueType(ThreadPoolFactory.QueueType.LINKED_BLOCKING);
        pool.setQueueCapacity(16);
        pool.setKeepAliveSeconds(30);
        pool.setRejectionPolicy(ThreadPoolProperties.RejectionPolicy.ABORT);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> pool.validate("cpu"));
        assertEquals("cpu max-size must be greater than or equal to core-size", ex.getMessage());
    }

    @Test
    void shouldRejectNonZeroQueueCapacityForSynchronousQueue() {
        ThreadPoolProperties.Pool pool = new ThreadPoolProperties.Pool();
        pool.setCoreSize(4);
        pool.setMaxSize(16);
        pool.setQueueType(ThreadPoolFactory.QueueType.SYNCHRONOUS);
        pool.setQueueCapacity(1);
        pool.setKeepAliveSeconds(30);
        pool.setRejectionPolicy(ThreadPoolProperties.RejectionPolicy.CALLER_RUNS);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> pool.validate("io"));
        assertEquals("io queue-capacity must be 0 when queue-type is SYNCHRONOUS", ex.getMessage());
    }

    @Test
    @SuppressWarnings("deprecation")
    void shouldRejectSilentDiscardPolicy() {
        ThreadPoolProperties.Pool pool = new ThreadPoolProperties.Pool();
        pool.setCoreSize(4);
        pool.setMaxSize(16);
        pool.setQueueType(ThreadPoolFactory.QueueType.LINKED_BLOCKING);
        pool.setQueueCapacity(16);
        pool.setKeepAliveSeconds(30);
        pool.setRejectionPolicy(ThreadPoolProperties.RejectionPolicy.DISCARD);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> pool.validate("io"));
        assertEquals("io rejection-policy must not silently discard async tasks", ex.getMessage());
    }
}
