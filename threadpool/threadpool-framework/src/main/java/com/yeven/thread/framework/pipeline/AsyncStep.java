package com.yeven.thread.framework.pipeline;

import java.util.concurrent.CompletableFuture;

/**
 * Context-preserving async step abstraction.
 */
@FunctionalInterface
public interface AsyncStep<C> extends AsyncFunction<C, C> {

    @Override
    CompletableFuture<C> apply(C context);

    default AsyncStep<C> then(AsyncStep<C> next) {
        return ctx -> this.apply(ctx).thenCompose(next::apply);
    }

    static <C> AsyncStep<C> identity() {
        return CompletableFuture::completedFuture;
    }
}
