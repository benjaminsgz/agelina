package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.ExecutionMode;
import java.util.function.Function;

/**
 * Metadata for a pipeline step.
 */
public final class StepDefinition<C> {

    private final String name;
    private final ExecutionMode mode;
    private final Function<C, C> handler;

    public StepDefinition(String name, ExecutionMode mode, Function<C, C> handler) {
        this.name = name;
        this.mode = mode;
        this.handler = handler;
    }

    public String getName() {
        return name;
    }

    public ExecutionMode getMode() {
        return mode;
    }

    public Function<C, C> getHandler() {
        return handler;
    }
}
