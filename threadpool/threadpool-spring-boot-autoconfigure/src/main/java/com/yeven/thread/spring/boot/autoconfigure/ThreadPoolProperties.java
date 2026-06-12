package com.yeven.thread.spring.boot.autoconfigure;

import com.yeven.thread.framework.executor.ThreadPoolFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定自配置前缀 {@code threadpool.async.*} 的线程池配置属性类。
 *
 * <p>配置示例：</p>
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
     * 线程池拒绝策略枚举。
     *
     * <p>异步编排必须让每个提交的任务最终成功或失败完成。静默丢弃任务会导致调用方拿到永远不完成的 Future，
     * 因此只允许显式失败或调用方执行这两种可观测策略。</p>
     */
    public enum RejectionPolicy {
        /**
         * 抛出 RejectedExecutionException 异常（默认策略）。
         */
        ABORT,
        /**
         * 直接在调用者线程中同步执行该任务（常用于 I/O 阻塞型线程池以提供天然的背压限制）。
         */
        CALLER_RUNS,
        /**
         * 已废弃：静默丢弃任务会破坏 Future 完成语义，配置该值会在启动校验时失败。
         */
        @Deprecated
        DISCARD,
        /**
         * 已废弃：丢弃队列中旧任务无法通知原始 Future，配置该值会在启动校验时失败。
         */
        @Deprecated
        DISCARD_OLDEST
    }

    /**
     * 单个线程池实例的配置参数。
     */
    public static class Pool {
        private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

        /**
         * 核心线程数，线程池中保留的最小存活线程数。
         */
        private int coreSize;
        /**
         * 最大线程数，线程池允许的最大并发线程数。
         */
        private int maxSize;
        /**
         * 线程池使用的任务队列类型。
         */
        private ThreadPoolFactory.QueueType queueType;
        /**
         * 任务阻塞队列的容量大小。
         */
        private int queueCapacity;
        /**
         * 空闲非核心线程在终止前等待新任务的最长时间（单位：秒）。
         */
        private long keepAliveSeconds;
        /**
         * 当队列满且线程数达到最大值时的拒绝处理策略。
         */
        private RejectionPolicy rejectionPolicy;

        /**
         * 构造默认的 IO 线程池配置（I/O 密集型）。
         *
         * @return 默认 IO 线程池配置实例
         */
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

        /**
         * 构造默认的 CPU 线程池配置（计算密集型）。
         *
         * @return 默认 CPU 线程池配置实例
         */
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

        /**
         * 校验当前线程池配置参数的合法性。
         *
         * @param poolName 线程池名称，用于异常描述
         */
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
            if (rejectionPolicy == RejectionPolicy.DISCARD || rejectionPolicy == RejectionPolicy.DISCARD_OLDEST) {
                throw new IllegalStateException(
                        poolName + " rejection-policy must not silently discard async tasks");
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
