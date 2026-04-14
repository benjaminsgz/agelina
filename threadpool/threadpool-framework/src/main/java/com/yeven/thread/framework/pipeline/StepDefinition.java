package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.ExecutionMode;
import java.util.function.Function;

/**
 * Declarative metadata for building one {@link AsyncStep}.
 *
 * <p>The framework separates "what to run" ({@code handler}) from "where to run"
 * ({@code mode}) so thread routing stays data-driven.</p>
 *
 * @param <C> pipeline context type
 */
public final class StepDefinition<C> {

    private final String name;
    private final ExecutionMode mode;
    private final Function<C, C> handler;

    /**
     * Creates one step definition.
     *
     * @param name logical step name, used by decorators/logging
     * @param mode execution mode used for executor selection
     * @param handler pure processing function for this step
     */
    public StepDefinition(String name, ExecutionMode mode, Function<C, C> handler) {
        this.name = name;
        this.mode = mode;
        this.handler = handler;
    }

    /**
     * @return logical step name
     */
    public String getName() {
        return name;
    }

    /**
     * @return execution mode
     */
    public ExecutionMode getMode() {
        return mode;
    }

    /**
     * @return step handler
     */
    public Function<C, C> getHandler() {
        return handler;
    }
}
