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
 * 线程池工厂类，用于创建带有统一命名和配置规范的 {@link ThreadPoolExecutor} 实例。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>防范线程资源失控：</b> Java 原生线程池如果配置不妥（例如使用无界队列），会导致 OOM 或由于拒绝策略配置不当导致任务无声丢失。本工厂对队列类型和拒绝策略进行了统一规约。</li>
 *   <li><b>可诊断命名规范：</b> 强制为生成的物理线程提供带业务语义的前缀命名（如 "cpu-pool-x", "io-pool-x"），使生产环境进行 JStack、线程 Dump 调试或性能分析时，能迅速定位责任池，大幅提升故障排查效率。</li>
 * </ul>
 */
public final class ThreadPoolFactory {

    private ThreadPoolFactory() {
    }

    /**
     * 创建一个新的线程池执行器。
     *
     * @param prefix 线程名称的前缀，用于在调试或堆栈转储中标识该线程池（例如 "io-pool"、"cpu-pool"）
     * @param coreSize 核心线程数，即使线程空闲也会保留在线程池中的线程数量
     * @param maxSize 线程池中允许的最大线程数量
     * @param queueType 任务队列的类型，支持有界队列（LINKED_BLOCKING）或零容量直接传递队列（SYNCHRONOUS）
     * @param queueCapacity 任务队列的容量（仅在 {@link QueueType#LINKED_BLOCKING} 模式下有效）
     * @param keepAliveSeconds 当线程池中的线程数多于核心线程数时，多余的空闲线程在终止前等待新任务的最长时间（单位：秒）
     * @param rejectedExecutionHandler 拒绝策略处理器，在线程池和队列都满时调用的饱和策略
     * @return 配置完成的 {@link ThreadPoolExecutor} 实例
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

    /**
     * 根据队列类型和容量创建对应的阻塞队列。
     */
    private static BlockingQueue<Runnable> createQueue(QueueType queueType, int queueCapacity) {
        return switch (queueType) {
            case LINKED_BLOCKING -> new LinkedBlockingQueue<>(queueCapacity);
            case SYNCHRONOUS -> new SynchronousQueue<>();
        };
    }

    /**
     * 创建一个命名的线程工厂，生成的线程是非守护线程，并且名字格式为 "prefix-序号"。
     */
    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        };
    }

    /**
     * 任务阻塞队列类型枚举。
     */
    public enum QueueType {
        /**
         * 基于链表的有界阻塞队列。
         */
        LINKED_BLOCKING,
        /**
         * 不存储元素的阻塞队列，每个插入操作必须等待另一个线程的移除操作（直接传递模式）。
         */
        SYNCHRONOUS
    }
}
