package com.yeven.thread.framework.pipeline;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 不可变的顺序异步管道。
 *
 * <p>管道中步骤的执行完全是串行且顺序相关的。只有当前一个步骤的异步 Future 成功完成之后，
 * 下一个步骤才会启动。</p>
 *
 * <p><b>警告：</b> 上下文对象 {@code C} 在所有步骤之间是共享的。如果某些步骤会修改该上下文，
 * 请确保修改是线程安全的，或者在将此管道集成到更大的并发 DAG 图时能够保证可见性。</p>
 *
 * @param <C> 管道的上下文类型
 */
public class AsyncPipeline<C> {

    private final AsyncStep<C> composed;

    /**
     * 根据传入的异步步骤列表构建异步管道。
     *
     * @param steps 异步步骤列表
     */
    public AsyncPipeline(List<AsyncStep<C>> steps) {
        AsyncStep<C> chain = AsyncStep.identity();
        for (AsyncStep<C> step : steps) {
            chain = chain.then(step);
        }
        this.composed = chain;
    }

    /**
     * 执行该异步管道。
     *
     * <p>如果其中任意一个步骤执行失败，返回的 Future 会异常结束，并且后续剩余的所有步骤都会被跳过（Fail-Fast 机制）。</p>
     *
     * @param context 初始上下文对象
     * @return 包含最终执行完毕上下文的 {@link CompletableFuture}
     */
    public CompletableFuture<C> execute(C context) {
        return composed.apply(context);
    }
}
