package com.yeven.thread.framework.pipeline;

import java.util.concurrent.CompletableFuture;

/**
 * Context-preserving asynchronous pipeline step.
 *
 * <p>Unlike a generic transform, a step keeps the same context type and updates the same logical
 * request state across multiple stages.</p>
 *
 * @param <C> pipeline context type
 */
@FunctionalInterface
public interface AsyncStep<C> extends AsyncFunction<C, C> {

    /**
     * Executes the step against the provided context.
     *
     * @param context request context
     * @return future that completes with the next context snapshot
     */
    @Override
    CompletableFuture<C> apply(C context);

    /**
     * Chains one more step after the current step.
     *
     * @param next next step
     * @return composed step
     */
    default AsyncStep<C> then(AsyncStep<C> next) {
        return ctx -> this.apply(ctx).thenCompose(next::apply);
    }

    /**
     * Creates a no-op step.
     *
     * @param <C> context type
     * @return step that returns the original context
     */
    static <C> AsyncStep<C> identity() {
        return CompletableFuture::completedFuture;
    }
}
