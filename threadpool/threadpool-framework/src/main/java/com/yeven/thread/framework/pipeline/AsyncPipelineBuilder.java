package com.yeven.thread.framework.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于顺序异步管道的链式构建器。
 *
 * @param <C> 管道上下文类型
 */
public class AsyncPipelineBuilder<C> {

    private final List<AsyncStep<C>> steps = new ArrayList<>();

    /**
     * 将一个异步步骤追加到管道的末尾。
     *
     * @param step 异步步骤
     * @return 当前构建器实例，用于链式调用
     */
    public AsyncPipelineBuilder<C> addStep(AsyncStep<C> step) {
        this.steps.add(step);
        return this;
    }

    /**
     * 根据当前添加的所有步骤构建一个不可变的异步管道。
     *
     * @return 包含所有已添加步骤且按插入顺序排列的异步管道 {@link AsyncPipeline}
     */
    public AsyncPipeline<C> build() {
        return new AsyncPipeline<>(steps);
    }
}
