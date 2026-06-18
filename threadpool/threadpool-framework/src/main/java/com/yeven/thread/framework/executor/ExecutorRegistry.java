package com.yeven.thread.framework.executor;

import java.util.Map;
import java.util.concurrent.Executor;

import com.yeven.thread.framework.constant.ExecutionMode;

/**
 * 线程池执行器注册表，维护任务执行模式到对应线程池实例的映射。
 * 
 * <p>
 * <b>设计必要性与核心价值：</b>
 * </p>
 * <ul>
 * <li><b>多线程隔离与池化管理：</b> 在实际的高性能业务系统中，不同的任务类型对 CPU、I/O 等系统资源的消耗迥异。通过本注册表，可以将特定的
 * {@link ExecutionMode}（如 CPU 密集型、I/O 阻塞型）绑定至专门调优过的不同物理线程池实例，从而实现线程资源隔离，防止 I/O
 * 阻塞型任务饿死 CPU 计算线程。</li>
 * <li><b>不可变性与线程安全：</b> 采用不可变只读设计（使用
 * {@link Map#copyOf}），在构建后不允许进行任何外部修改。这为高并发的多线程调度环境提供了极高的安全保障，无需任何额外的锁机制。</li>
 * </ul>
 */
public class ExecutorRegistry {

    private final Map<ExecutionMode, Executor> executors;

    /**
     * 构建执行器注册表。
     *
     * @param executors 执行模式与线程池的映射 Map，将被拷贝为不可变副本
     */
    public ExecutorRegistry(Map<ExecutionMode, Executor> executors) {
        this.executors = Map.copyOf(executors);
    }

    /**
     * 根据指定的任务执行模式获取其绑定的线程池执行器。
     *
     * @param mode 任务执行模式 {@link ExecutionMode}
     * @return 对应的 {@link Executor} 实例
     * @throws IllegalArgumentException 当指定的模式未注册任何绑定线程池时抛出
     */
    public Executor getExecutor(ExecutionMode mode) {
        Executor executor = executors.get(mode);
        if (executor == null) {
            throw new IllegalArgumentException("No executor registered for mode: " + mode);
        }
        return executor;
    }
}
