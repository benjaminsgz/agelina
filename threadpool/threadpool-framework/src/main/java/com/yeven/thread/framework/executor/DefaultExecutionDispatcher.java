package com.yeven.thread.framework.executor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Default execution dispatcher backed by the executor registry.
 */
public class DefaultExecutionDispatcher implements ExecutionDispatcher {

    private final ExecutorRegistry executorRegistry;

    public DefaultExecutionDispatcher(ExecutorRegistry executorRegistry) {
        this.executorRegistry = executorRegistry;
    }

    @Override
    public <T> CompletableFuture<T> dispatch(ExecutionMode mode, Supplier<T> supplier) {
        if (mode == ExecutionMode.DIRECT) {
            return CompletableFuture.completedFuture(supplier.get());
        }

        Executor executor = executorRegistry.getExecutor(mode);
        return CompletableFuture.supplyAsync(supplier, executor);
    }
}
