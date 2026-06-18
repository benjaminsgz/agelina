package com.yeven.thread.framework.pipeline;

import java.util.concurrent.CompletableFuture;

/**
 * 维持上下文类型的异步管道步骤。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>单一状态单向流转：</b> 在复杂的工作流中，由于管道步骤在执行各个阶段时始终持有并丰富相同的上下文实体类型 {@code C}，该设计消除了不同对象模型之间低效的来回转换。</li>
 *   <li><b>强大的函数式链式组合：</b> 通过提供便捷的 {@link #then(AsyncStep)} 默认实现，使用 {@link CompletableFuture#thenCompose} 实现了任务的高阶函数式组合（Monadic Compose），让多步骤的顺序流转在声明式链条中一气呵成。</li>
 * </ul>
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
