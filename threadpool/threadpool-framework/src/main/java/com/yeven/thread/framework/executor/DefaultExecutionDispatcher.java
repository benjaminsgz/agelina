package com.yeven.thread.framework.executor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * 默认的任务分发器实现，底层由 {@link ExecutorRegistry} 提供线程池支持。
 *
 * <p>{@link ExecutionMode#DIRECT} 模式的任务将直接在当前调用线程同步执行。
 * 其他模式的任务则会被分发至对应的线程池异步执行。</p>
 */
public class DefaultExecutionDispatcher implements ExecutionDispatcher, NodeDispatcher {

    private final ExecutorRegistry executorRegistry;

    /**
     * 创建默认分发器实例。
     *
     * @param executorRegistry 包含各执行模式对应线程池的注册表
     */
    public DefaultExecutionDispatcher(ExecutorRegistry executorRegistry) {
        this.executorRegistry = executorRegistry;
    }

    @Override
    public <T> CompletableFuture<T> dispatch(ExecutionMode mode, Supplier<T> supplier) {
        try {
            // 如果是 DIRECT 模式，直接在当前线程同步调用并返回已完成的 Future
            if (mode == ExecutionMode.DIRECT) {
                return CompletableFuture.completedFuture(supplier.get());
            }

            // 获取对应的线程池进行异步执行
            Executor executor = executorRegistry.getExecutor(mode);
            return CompletableFuture.supplyAsync(supplier, executor);
        } catch (RejectedExecutionException e) {
            // 线程池队列满拒绝策略异常处理
            return CompletableFuture.failedFuture(e);
        } catch (Throwable t) {
            // 捕获其他任何异常并返回异常结束的 Future
            return CompletableFuture.failedFuture(t);
        }
    }

    @Override
    public void dispatchNode(ExecutionMode mode, Runnable task, NodeCompletion completion) {
        try {
            // 如果是 DIRECT 模式，直接在当前线程同步执行该节点
            if (mode == ExecutionMode.DIRECT) {
                runNode(task, completion);
                return;
            }

            // 获取对应的线程池，将节点任务提交至线程池异步执行
            Executor executor = executorRegistry.getExecutor(mode);
            executor.execute(() -> runNode(task, completion));
        } catch (RejectedExecutionException e) {
            // 线程池满拒绝策略触发时，直接回调完成接口并传入拒绝异常
            completion.complete(e);
        } catch (Throwable t) {
            // 捕获提交或执行准备阶段的其他异常
            completion.complete(t);
        }
    }

    /**
     * 运行具体的节点任务逻辑，并触发完成状态回调。
     *
     * @param task 节点的可运行任务
     * @param completion 回调接口
     */
    private static void runNode(Runnable task, NodeCompletion completion) {
        try {
            task.run();
            // 成功执行，传入 null 表示无异常
            completion.complete(null);
        } catch (Throwable error) {
            // 执行发生异常，将异常回传
            completion.complete(error);
        }
    }
}
