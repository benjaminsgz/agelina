package com.yeven.thread.framework.plugin;

import com.yeven.thread.framework.pipeline.SlotAsyncGraphBuilder;

/**
 * Startup procedure that contributes nodes to a slot graph builder.
 *
 * @param <C> graph context type
 */
@FunctionalInterface
public interface GraphContributor<C> {

    /**
     * Adds graph nodes and terminal definitions.
     *
     * @param builder graph builder
     */
    void contribute(SlotAsyncGraphBuilder<C> builder);
}
