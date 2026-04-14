package com.yeven.thread.spring.boot.autoconfigure;

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
 *     cpu:
 *       core-size: 8
 *       max-size: 8
 *       queue-capacity: 500
 *       keep-alive-seconds: 60
 * }</pre>
 */
@ConfigurationProperties(prefix = "threadpool.async")
public class ThreadPoolProperties {

    private Pool io = new Pool();
    private Pool cpu = new Pool();

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
     * Configuration for one named pool.
     */
    public static class Pool {
        private int coreSize = 8;
        private int maxSize = 16;
        private int queueCapacity = 1000;
        private long keepAliveSeconds = 60;

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
    }
}
