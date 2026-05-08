package com.yeven.thread.framework.pipeline;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Immutable ordered asynchronous pipeline.
 *
 * <p>Execution is strictly sequential by step order. Each step starts only after the previous step
 * future is completed successfully.</p>
 *
 * <p><b>Warning:</b> The context object {@code C} is shared across all steps. If steps modify
 * the context, ensure that the modifications are thread-safe or that visibility is guaranteed,
 * especially if the pipeline is integrated into a larger concurrent graph.</p>
 *
 * @param <C> pipeline context type
 */
public class AsyncPipeline<C> {

    private final AsyncStep<C> composed;

    public AsyncPipeline(List<AsyncStep<C>> steps) {
        AsyncStep<C> chain = AsyncStep.identity();
        for (AsyncStep<C> step : steps) {
            chain = chain.then(step);
        }
        this.composed = chain;
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
        return composed.apply(context);
    }
}
