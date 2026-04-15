package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.ExecutionDispatcher;
import com.yeven.thread.framework.executor.ExecutionMode;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Builds executable {@link AsyncStep} instances from {@link StepDefinition}.
 *
 * <p>The generated step delegates scheduling to {@link ExecutionDispatcher}, so each step can run on
 * IO/CPU/direct mode without changing business logic code.</p>
 */
public class AsyncStepFactory {

    private final ExecutionDispatcher dispatcher;

    public AsyncStepFactory(ExecutionDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Creates one asynchronous step.
     *
     * @param definition step definition
     * @param <C> context type
     * @return async step that dispatches the handler according to execution mode
     */
    public <C> AsyncStep<C> create(StepDefinition<C> definition) {
        return context -> dispatcher.dispatch(
                definition.getMode(),
                () -> definition.getHandler().apply(context)
        );
    }

    /**
     * Dispatches one task with explicit execution mode.
     *
     * <p>This is used by graph-style runtimes that do not map directly to {@link AsyncStep}
     * but still need mode-aware scheduling.</p>
     *
     * @param mode execution mode
     * @param supplier task supplier
     * @param <T> result type
     * @return dispatched task future
     */
    public <T> CompletableFuture<T> dispatch(ExecutionMode mode, Supplier<T> supplier) {
        return dispatcher.dispatch(mode, supplier);
    }
}
