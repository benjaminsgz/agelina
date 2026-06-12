package com.yeven.thread.framework.pipeline;

import java.util.concurrent.CompletableFuture;

/**
 * 维持上下文类型的异步管道步骤。
 *
 * <p>与通用的转换函数（Transform）不同，异步步骤在多个执行阶段中始终保持相同的上下文类型 {@code C}，
 * 用于在链路中依次更新或丰富相同的逻辑请求状态。</p>
 *
 * @param <C> 管道上下文类型
 */
@FunctionalInterface
public interface AsyncStep<C> extends AsyncFunction<C, C> {

    /**
     * 对提供的上下文执行当前步骤的异步逻辑。
     *
     * @param context 请求上下文对象
     * @return 异步执行完成后返回下一个上下文快照的 {@link CompletableFuture}
     */
    @Override
    CompletableFuture<C> apply(C context);

    /**
     * 将另一个步骤链式连接到当前步骤之后，顺序执行。
     *
     * @param next 下一个要执行的步骤
     * @return 组合后的新步骤，其底层执行链为：先执行当前步骤，再将结果传递给 {@code next} 步骤执行
     */
    default AsyncStep<C> then(AsyncStep<C> next) {
        return ctx -> this.apply(ctx).thenCompose(next::apply);
    }

    /**
     * 创建一个不执行任何操作的恒等（No-op）步骤。
     *
     * @param <C> 上下文类型
     * @return 直接返回原始上下文的异步步骤
     */
    static <C> AsyncStep<C> identity() {
        return CompletableFuture::completedFuture;
    }
}
