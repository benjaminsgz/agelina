package com.yeven.thread.framework.dispatcher;

import com.yeven.thread.framework.constant.ExecutionMode;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 核心任务分发器接口，定义了将任务派发至对应执行模式的物理通道规范。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>多线程池抽象：</b> 封装了复杂的线程管理和线程池路由细节，调用方只需指定 {@link ExecutionMode}（DIRECT、CPU、IO）即可提交任务，无需关心具体的 {@link java.util.concurrent.Executor} 实例。</li>
 *   <li><b>统一异步规约：</b> 通过返回 {@link CompletableFuture} 统一了同步与异步任务的响应机制，奠定了整个流水线或 DAG 计算的基础通信接口。</li>
 * </ul>
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
