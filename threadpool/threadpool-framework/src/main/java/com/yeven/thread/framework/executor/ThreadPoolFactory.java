package com.yeven.thread.framework.executor;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory for building named thread pool executors.
 */
public final class ThreadPoolFactory {

    private ThreadPoolFactory() {
    }

    /**
     * Create a new executor backed by a {@link ThreadPoolExecutor}.
     *
     * @param prefix thread name prefix
     * @param coreSize minimum number of live threads
     * @param maxSize maximum number of live threads
     * @param queueCapacity task queue capacity
     * @param keepAliveSeconds idle keep alive time in seconds
     * @param rejectedExecutionHandler rejection policy
     * @return configured executor
     */
    public static Executor create(
            String prefix,
            int coreSize,
            int maxSize,
            int queueCapacity,
            long keepAliveSeconds,
            RejectedExecutionHandler rejectedExecutionHandler
    ) {
        return new ThreadPoolExecutor(
                coreSize,
                maxSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                namedThreadFactory(prefix),
                rejectedExecutionHandler
        );
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
}
