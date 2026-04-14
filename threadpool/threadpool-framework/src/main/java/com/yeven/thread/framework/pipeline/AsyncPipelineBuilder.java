package com.yeven.thread.framework.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for ordered asynchronous pipelines.
 *
 * @param <C> pipeline context type
 */
public class AsyncPipelineBuilder<C> {

    private final List<AsyncStep<C>> steps = new ArrayList<>();

    /**
     * Appends one step to the end of the pipeline.
     *
     * @param step async step
     * @return same builder for chaining
     */
    public AsyncPipelineBuilder<C> addStep(AsyncStep<C> step) {
        this.steps.add(step);
        return this;
    }

    /**
     * Builds an immutable pipeline snapshot.
     *
     * @return pipeline containing all added steps in insertion order
     */
    public AsyncPipeline<C> build() {
        return new AsyncPipeline<>(steps);
    }
}
