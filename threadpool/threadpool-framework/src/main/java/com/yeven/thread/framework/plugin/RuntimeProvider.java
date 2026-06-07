package com.yeven.thread.framework.plugin;

import com.yeven.thread.framework.executor.ExecutionDispatcher;
import com.yeven.thread.framework.executor.ExecutorRegistry;

/**
 * Builds one dispatcher from a boot-time executor registry.
 */
@FunctionalInterface
public interface RuntimeProvider {

    /**
     * Creates one execution dispatcher.
     *
     * @param executorRegistry executor registry
     * @return dispatcher used by compiled flows
     */
    ExecutionDispatcher create(ExecutorRegistry executorRegistry);
}
