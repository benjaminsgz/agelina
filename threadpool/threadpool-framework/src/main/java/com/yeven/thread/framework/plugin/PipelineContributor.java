package com.yeven.thread.framework.plugin;

import com.yeven.thread.framework.pipeline.AsyncPipelineBuilder;
import com.yeven.thread.framework.pipeline.AsyncStepFactory;

/**
 * Startup procedure that contributes stages to a pipeline builder.
 *
 * @param <C> pipeline context type
 */
@FunctionalInterface
public interface PipelineContributor<C> {

    /**
     * Adds pipeline stages.
     *
     * @param builder pipeline builder
     * @param stepFactory async step factory
     */
    void contribute(AsyncPipelineBuilder<C> builder, AsyncStepFactory stepFactory);
}
