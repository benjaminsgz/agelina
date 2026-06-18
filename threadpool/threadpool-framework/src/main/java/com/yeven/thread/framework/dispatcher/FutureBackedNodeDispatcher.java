package com.yeven.thread.framework.dispatcher;

import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.executor.NodeCompletion;
import java.util.concurrent.CompletionException;

/**
 * 当底层的 {@link ExecutionDispatcher} 未实现 {@link NodeDispatcher} 时，
 * 提供的降级包装分发器，其在内部通过 Future 来驱动回调。
 * 
 * <p>
 * <b>设计必要性与核心价值：</b>
 * </p>
 * <ul>
 * <li><b>退化兼容与稳健兜底：</b> 确保对传统非 NodeDispatcher 任务分发器提供开箱即用的兼容性。</li>
 * </ul>
 */
public final class FutureBackedNodeDispatcher implements NodeDispatcher {

    private final ExecutionDispatcher dispatcher;

    public FutureBackedNodeDispatcher(ExecutionDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void dispatchNode(ExecutionMode mode, Runnable task, NodeCompletion completion) {
        // [降级调度逻辑]：由于底层分发器不支持直接的零分配节点分发，在此退化到通过普通的 dispatch 方法分发任务。
        // 调度器会返回一个 CompletableFuture 对象，我们使用 whenComplete 钩子来响应执行结果。
        dispatcher.dispatch(mode, () -> {
            // 执行图节点的核心任务逻辑
            task.run();
            return null;
        }).whenComplete((unused, error) -> {
            // [完成回调与异常桥接]：一旦 Future 执行完毕，立即触发回调通知。
            // 如果在执行过程中产生异常，为了防范 CompletableFuture 链式调用产生的包装异常（CompletionException）干扰诊断，
            // 我们在回调 complete 接口前，对异常执行 unwrapCompletion 解包处理。
            completion.complete(unwrapCompletion(error));
        });
    }

    /**
     * 解包异常。在 CompletableFuture 异步链式流转中，抛出的业务异常或运行时异常通常会被自动包装在 CompletionException
     * 中。
     * 为了让外层调度引擎能够感知最准确的原始根源异常，必须在这里执行剥壳解包操作。
     *
     * @param error 捕获的异常对象
     * @return 剥离 CompletionException 包装后的最深层真实异常，若非包装异常则直接返回原异常
     */
    private static Throwable unwrapCompletion(Throwable error) {
        // [异常解包逻辑]：检查当前捕获的异常是否为 CompletionException 的实例，且内部 cause 是否不为空。
        if (error instanceof CompletionException completionException
                && completionException.getCause() != null) {
            // 如果存在被包装的真实原因（Cause），则提取并返回该原因（通常是具体的业务异常或 NullPointerException 等）
            return completionException.getCause();
        }
        // 若不是包装异常或 cause 为空，说明其本身就是最原始的异常源，直接原样返回即可
        return error;
    }
}
