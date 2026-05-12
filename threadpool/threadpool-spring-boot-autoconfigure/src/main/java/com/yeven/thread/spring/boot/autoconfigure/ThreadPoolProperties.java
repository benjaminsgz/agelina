package com.yeven.thread.spring.boot.autoconfigure;

import com.yeven.thread.framework.executor.ThreadPoolFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Thread pool properties bound from {@code threadpool.async.*}.
 *
 * <p>Example:</p>
 * <pre>{@code
 * threadpool:
 *   async:
 *     io:
 *       core-size: 16
 *       max-size: 32
 *       queue-capacity: 2000
 *       keep-alive-seconds: 60
 *       rejection-policy: CALLER_RUNS
 *     cpu:
 *       core-size: 8
 *       max-size: 8
 *       queue-capacity: 500
 *       keep-alive-seconds: 60
 *       rejection-policy: ABORT
 * }</pre>
 */
@ConfigurationProperties(prefix = "threadpool.async")
public class ThreadPoolProperties {

    private Pool io = Pool.ioDefaults();
    private Pool cpu = Pool.cpuDefaults();

    public Pool getIo() {
        return io;
    }

    public void setIo(Pool io) {
        this.io = io;
    }

    public Pool getCpu() {
        return cpu;
    }

    public void setCpu(Pool cpu) {
        this.cpu = cpu;
    }

    /**
     * Rejection policy enum mapping to ThreadPoolExecutor policies.
     */
    public enum RejectionPolicy {
        /**
         * Throws RejectedExecutionException.
         */
        ABORT,
        /**
         * Executes the task in the caller's thread.
         */
        CALLER_RUNS,
        /**
         * Silently discards the task.
         */
        DISCARD,
        /**
         * Discards the oldest task in the queue.
         */
        DISCARD_OLDEST
    }

    /**
     * Configuration for one named pool.
     */
    public static class Pool {
        private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

        /**
         * Minimum number of threads to keep alive.
         */
        private int coreSize;
        /**
         * Maximum allowed number of threads.
         */
        private int maxSize;
        /**
         * Work queue implementation used by this pool.
         */
        private ThreadPoolFactory.QueueType queueType;
        /**
         * Capacity of the task queue.
         */
        private int queueCapacity;
        /**
         * Time in seconds for idle threads to wait before terminating.
         */
        private long keepAliveSeconds;
        /**
         * Strategy used when the queue and max threads are exhausted.
         */
        private RejectionPolicy rejectionPolicy;

        public static Pool ioDefaults() {
            Pool pool = new Pool();
            int core = Math.max(AVAILABLE_PROCESSORS * 2, 4);
            pool.coreSize = core;
            pool.maxSize = Math.max(core * 4, core);
            pool.queueType = ThreadPoolFactory.QueueType.LINKED_BLOCKING;
            pool.queueCapacity = 32;
            pool.keepAliveSeconds = 30;
            pool.rejectionPolicy = RejectionPolicy.CALLER_RUNS;
            return pool;
        }

        public static Pool cpuDefaults() {
            Pool pool = new Pool();
            int cpuThreads = Math.max(AVAILABLE_PROCESSORS, 1);
            pool.coreSize = cpuThreads;
            pool.maxSize = cpuThreads;
            pool.queueType = ThreadPoolFactory.QueueType.LINKED_BLOCKING;
            pool.queueCapacity = 64;
            pool.keepAliveSeconds = 30;
            pool.rejectionPolicy = RejectionPolicy.ABORT;
            return pool;
        }

        public void validate(String poolName) {
            if (coreSize <= 0) {
                throw new IllegalStateException(poolName + " core-size must be greater than 0");
            }
            if (maxSize < coreSize) {
                throw new IllegalStateException(poolName + " max-size must be greater than or equal to core-size");
            }
            if (keepAliveSeconds < 0) {
                throw new IllegalStateException(poolName + " keep-alive-seconds must be greater than or equal to 0");
            }
            if (queueType == null) {
                throw new IllegalStateException(poolName + " queue-type must not be null");
            }
            if (queueType == ThreadPoolFactory.QueueType.LINKED_BLOCKING && queueCapacity <= 0) {
                throw new IllegalStateException(poolName + " queue-capacity must be greater than 0 when queue-type is LINKED_BLOCKING");
            }
            if (queueType == ThreadPoolFactory.QueueType.SYNCHRONOUS && queueCapacity != 0) {
                throw new IllegalStateException(poolName + " queue-capacity must be 0 when queue-type is SYNCHRONOUS");
            }
            if (rejectionPolicy == null) {
                throw new IllegalStateException(poolName + " rejection-policy must not be null");
            }
        }

        public int getCoreSize() {
            return coreSize;
        }

        public void setCoreSize(int coreSize) {
            this.coreSize = coreSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public ThreadPoolFactory.QueueType getQueueType() {
            return queueType;
        }

        public void setQueueType(ThreadPoolFactory.QueueType queueType) {
            this.queueType = queueType;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public long getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public void setKeepAliveSeconds(long keepAliveSeconds) {
            this.keepAliveSeconds = keepAliveSeconds;
        }

        public RejectionPolicy getRejectionPolicy() {
            return rejectionPolicy;
        }

        public void setRejectionPolicy(RejectionPolicy rejectionPolicy) {
            this.rejectionPolicy = rejectionPolicy;
        }
    }
}
