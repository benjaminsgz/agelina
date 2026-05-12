package com.yeven.thread.framework.executor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory for creating named {@link ThreadPoolExecutor} instances.
 *
 * <p>Use this factory to keep thread naming and queue policy consistent across services.</p>
 */
public final class ThreadPoolFactory {

    private ThreadPoolFactory() {
    }

    /**
     * Creates a new executor backed by {@link ThreadPoolExecutor}.
     *
     * @param prefix thread name prefix
     * @param coreSize minimum number of live threads
     * @param maxSize maximum number of live threads
     * @param queueCapacity task queue capacity
     * @param keepAliveSeconds idle keep alive time in seconds
     * @param rejectedExecutionHandler rejection policy
     * @return configured executor instance
     */
    public static ThreadPoolExecutor create(
            String prefix,
            int coreSize,
            int maxSize,
            QueueType queueType,
            int queueCapacity,
            long keepAliveSeconds,
            RejectedExecutionHandler rejectedExecutionHandler
    ) {
        return new ThreadPoolExecutor(
                coreSize,
                maxSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                createQueue(queueType, queueCapacity),
                namedThreadFactory(prefix),
                rejectedExecutionHandler
        );
    }

    private static BlockingQueue<Runnable> createQueue(QueueType queueType, int queueCapacity) {
        return switch (queueType) {
            case LINKED_BLOCKING -> new LinkedBlockingQueue<>(queueCapacity);
            case SYNCHRONOUS -> new SynchronousQueue<>();
        };
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        };
    }

    public enum QueueType {
        LINKED_BLOCKING,
        SYNCHRONOUS
    }
}
