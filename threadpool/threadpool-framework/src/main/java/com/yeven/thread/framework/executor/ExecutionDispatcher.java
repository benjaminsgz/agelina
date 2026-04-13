package com.yeven.thread.framework.executor;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Dispatch work onto the proper execution mode.
 */
public interface ExecutionDispatcher {

    <T> CompletableFuture<T> dispatch(ExecutionMode mode, Supplier<T> supplier);
}
