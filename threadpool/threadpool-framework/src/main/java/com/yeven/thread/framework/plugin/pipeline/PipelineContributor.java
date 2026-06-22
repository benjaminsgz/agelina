package com.yeven.thread.framework.plugin.pipeline;

import com.yeven.thread.framework.pipeline.linear.AsyncPipelineBuilder;
import com.yeven.thread.framework.pipeline.core.AsyncStepFactory;

/**
 * 启动期向异步管道（Pipeline）构建器贡献阶段步骤的扩展程序接口。
 *
 * @param <C> 管道上下文类型
 */
@FunctionalInterface
public interface PipelineContributor<C> {

    /**
     * 向构建器中添加异步管道步骤。
     *
     * @param builder     异步管道构建器
     * @param stepFactory 异步步骤工厂
     */
    void contribute(AsyncPipelineBuilder<C> builder, AsyncStepFactory stepFactory);
}
