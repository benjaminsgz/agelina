package com.yeven.thread.framework.executor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Registry of executors keyed by execution mode.
 */
public class ExecutorRegistry {

    private final Map<ExecutionMode, Executor> executors;

    public ExecutorRegistry(Map<ExecutionMode, Executor> executors) {
        this.executors = Map.copyOf(executors);
    }

    public Executor getExecutor(ExecutionMode mode) {
        Executor executor = executors.get(mode);
        if (executor == null) {
            throw new IllegalArgumentException("No executor registered for mode: " + mode);
        }
        return executor;
    }
}
