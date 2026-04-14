package com.yeven.thread.framework.executor;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Dispatches tasks to the executor mapped by {@link ExecutionMode}.
 */
public interface ExecutionDispatcher {

    /**
     * Schedules and executes one supplier task.
     *
     * @param mode execution mode
     * @param supplier task supplier
     * @param <T> result type
     * @return result future
     */
    <T> CompletableFuture<T> dispatch(ExecutionMode mode, Supplier<T> supplier);
}
