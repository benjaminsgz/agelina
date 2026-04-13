package com.yeven.thread.framework.pipeline;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Lowest-level async abstraction: input to future output.
 */
@FunctionalInterface
public interface AsyncFunction<I, O> extends Function<I, CompletableFuture<O>> {

    @Override
    CompletableFuture<O> apply(I input);

    default <N> AsyncFunction<I, N> thenAsync(AsyncFunction<O, N> next) {
        return input -> this.apply(input).thenCompose(next::apply);
    }

    static <T> AsyncFunction<T, T> identity() {
        return CompletableFuture::completedFuture;
    }
}
