package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.ExecutionDispatcher;

/**
 * Build async steps from declarative step definitions.
 */
public class AsyncStepFactory {

    private final ExecutionDispatcher dispatcher;

    public AsyncStepFactory(ExecutionDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public <C> AsyncStep<C> create(StepDefinition<C> definition) {
        return context -> dispatcher.dispatch(
                definition.getMode(),
                () -> definition.getHandler().apply(context)
        );
    }
}
