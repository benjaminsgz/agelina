package com.yeven.thread.framework.pipeline;

/**
 * Reusable template for registering one DAG subgraph.
 *
 * <p>A template defines nodes using local names. When instantiated, the builder assigns a namespace
 * prefix and optional external bindings so the same logical subgraph can be reused multiple times
 * inside one larger graph.</p>
 *
 * @param <C> graph context type
 */
@FunctionalInterface
public interface AsyncGraphTemplate<C> {

    /**
     * Adds one subgraph instance to the target builder through the template context.
     *
     * @param context template instantiation context
     */
    void apply(AsyncGraphTemplateContext<C> context);
}
