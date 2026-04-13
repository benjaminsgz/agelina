package com.yeven.thread.framework.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for async pipelines.
 */
public class AsyncPipelineBuilder<C> {

    private final List<AsyncStep<C>> steps = new ArrayList<>();

    public AsyncPipelineBuilder<C> addStep(AsyncStep<C> step) {
        this.steps.add(step);
        return this;
    }

    public AsyncPipeline<C> build() {
        return new AsyncPipeline<>(steps);
    }
}
