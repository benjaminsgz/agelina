package com.yeven.thread.framework.executor;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 任务分发器，将任务派发至与 {@link ExecutionMode} 映射的特定线程池。
 */
public interface ExecutionDispatcher {

    /**
     * 调度并执行一个带有返回值的任务。
     *
     * @param mode 执行模式，决定任务路由到哪个线程池
     * @param supplier 具体的任务逻辑提供者
     * @param <T> 返回结果的类型
     * @return 代表异步执行结果的 {@link CompletableFuture} 实例
     */
    <T> CompletableFuture<T> dispatch(ExecutionMode mode, Supplier<T> supplier);
}
