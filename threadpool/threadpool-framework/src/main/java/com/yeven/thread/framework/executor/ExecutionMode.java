package com.yeven.thread.framework.executor;

/**
 * Execution mode used for thread routing.
 */
public enum ExecutionMode {
    /**
     * For blocking IO operations such as database or remote service calls.
     */
    IO,
    /**
     * For CPU-intensive work such as hashing and complex computation.
     */
    CPU,
    /**
     * Run in caller thread without dispatching.
     */
    DIRECT
}
