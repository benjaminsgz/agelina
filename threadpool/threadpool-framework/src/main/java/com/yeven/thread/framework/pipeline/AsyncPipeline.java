package com.yeven.thread.framework.pipeline;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Immutable ordered asynchronous pipeline.
 *
 * <p>Execution is strictly sequential by step order. Each step starts only after the previous step
 * future is completed successfully.</p>
 *
 * @param <C> pipeline context type
 */
public class AsyncPipeline<C> {

    private final List<AsyncStep<C>> steps;

    public AsyncPipeline(List<AsyncStep<C>> steps) {
        this.steps = List.copyOf(steps);
    }

    /**
     * Executes the pipeline.
     *
     * <p>If any step fails, the returned future completes exceptionally and remaining steps are skipped.</p>
     *
     * @param context initial context
     * @return final context future
     */
    public CompletableFuture<C> execute(C context) {
        CompletableFuture<C> future = CompletableFuture.completedFuture(context);
        for (AsyncStep<C> step : steps) {
            future = future.thenCompose(step::apply);
        }
        return future;
    }
}
