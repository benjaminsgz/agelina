package com.yeven.thread.framework.executor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 线程池执行器注册表，维护任务执行模式到对应线程池实例的映射。
 *
 * <p>该注册表是只读的，在初始化时通过传入 Map 构建其副本，以保证线程安全。</p>
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
