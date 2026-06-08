package com.yeven.thread.framework.executor;

/**
 * Completion callback for one graph node dispatch.
 */
@FunctionalInterface
public interface NodeCompletion {

    /**
     * Reports node completion.
     *
     * @param error null on success, otherwise the failure that stopped the node
     */
    void complete(Throwable error);
}
