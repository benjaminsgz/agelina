package com.yeven.thread.framework.executor;

/**
 * Low-allocation dispatcher for graph node execution.
 *
 * <p>Unlike {@link ExecutionDispatcher}, this interface does not allocate one
 * {@code CompletableFuture} per node. It is intended for compiled graph runtimes
 * that already own the final result future and dependency counters.</p>
 */
public interface NodeDispatcher {

    /**
     * Dispatches one runnable node.
     *
     * @param mode execution mode
     * @param task node task
     * @param completion completion callback
     */
    void dispatchNode(ExecutionMode mode, Runnable task, NodeCompletion completion);
}
