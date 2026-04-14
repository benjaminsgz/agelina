package com.yeven.thread.framework.executor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Read-only registry of executors keyed by {@link ExecutionMode}.
 */
public class ExecutorRegistry {

    private final Map<ExecutionMode, Executor> executors;

    public ExecutorRegistry(Map<ExecutionMode, Executor> executors) {
        this.executors = Map.copyOf(executors);
    }

    /**
     * Returns the executor for one mode.
     *
     * @param mode execution mode
     * @return configured executor
     * @throws IllegalArgumentException when mode has no bound executor
     */
    public Executor getExecutor(ExecutionMode mode) {
        Executor executor = executors.get(mode);
        if (executor == null) {
            throw new IllegalArgumentException("No executor registered for mode: " + mode);
        }
        return executor;
    }
}
