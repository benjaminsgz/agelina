package com.yeven.thread.framework.pipeline;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Base abstraction for asynchronous processing.
 *
 * <p>This type is intentionally minimal: one input, one {@link CompletableFuture} output.
 * It is useful for composing processing chains without introducing object-heavy strategy classes.</p>
 *
 * @param <I> input type
 * @param <O> output type
 */
@FunctionalInterface
public interface AsyncFunction<I, O> extends Function<I, CompletableFuture<O>> {

    /**
     * Starts asynchronous processing for one input item.
     *
     * @param input input value
     * @return future that completes with the transformed output or fails with the processing exception
     */
    @Override
    CompletableFuture<O> apply(I input);

    /**
     * Composes the current async function with the next one.
     *
     * <p>The returned function preserves failure propagation. If the first stage fails,
     * the second stage is not invoked.</p>
     *
     * @param next next async function
     * @param <N> output type of the next function
     * @return composed async function
     */
    default <N> AsyncFunction<I, N> thenAsync(AsyncFunction<O, N> next) {
        return input -> this.apply(input).thenCompose(next::apply);
    }

    /**
     * Creates an identity async function.
     *
     * @param <T> value type
     * @return function that completes immediately with the same input
     */
    static <T> AsyncFunction<T, T> identity() {
        return CompletableFuture::completedFuture;
    }
}
