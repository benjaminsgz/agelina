package com.yeven.thread.framework.executor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Default {@link ExecutionDispatcher} backed by {@link ExecutorRegistry}.
 *
 * <p>{@link ExecutionMode#DIRECT} is executed immediately in caller thread.
 * Other modes are dispatched to their corresponding executors.</p>
 */
public class DefaultExecutionDispatcher implements ExecutionDispatcher {

    private final ExecutorRegistry executorRegistry;

    public DefaultExecutionDispatcher(ExecutorRegistry executorRegistry) {
        this.executorRegistry = executorRegistry;
    }

    @Override
    public <T> CompletableFuture<T> dispatch(ExecutionMode mode, Supplier<T> supplier) {
        try {
            if (mode == ExecutionMode.DIRECT) {
                return CompletableFuture.completedFuture(supplier.get());
            }

            Executor executor = executorRegistry.getExecutor(mode);
            return CompletableFuture.supplyAsync(supplier, executor);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(e);
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }
}
