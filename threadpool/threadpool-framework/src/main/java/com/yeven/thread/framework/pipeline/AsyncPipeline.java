package com.yeven.thread.framework.pipeline;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Ordered async pipeline.
 */
public class AsyncPipeline<C> {

    private final List<AsyncStep<C>> steps;

    public AsyncPipeline(List<AsyncStep<C>> steps) {
        this.steps = List.copyOf(steps);
    }

    public CompletableFuture<C> execute(C context) {
        CompletableFuture<C> future = CompletableFuture.completedFuture(context);
        for (AsyncStep<C> step : steps) {
            future = future.thenCompose(step::apply);
        }
        return future;
    }
}
