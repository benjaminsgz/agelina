package com.yeven.thread.framework.plugin;

/**
 * Startup contribution sink used by {@link AgelinaPlugin}.
 *
 * <p>The returned values are procedures and descriptors. They are intended to be
 * consumed by boot-time compilers and then discarded from the request hot path.</p>
 */
public interface AgelinaContributions {

    /**
     * Registers one runtime provider.
     *
     * @param name unique runtime name
     * @param order lower values are applied first
     * @param provider runtime provider
     * @return same contribution sink
     */
    AgelinaContributions runtime(String name, int order, RuntimeProvider provider);

    /**
     * Registers one graph contributor.
     *
     * @param name unique graph contribution name
     * @param order lower values are applied first
     * @param contributor graph contributor
     * @param <C> graph context type
     * @return same contribution sink
     */
    <C> AgelinaContributions graph(String name, int order, GraphContributor<C> contributor);

    /**
     * Registers one pipeline contributor.
     *
     * @param name unique pipeline contribution name
     * @param order lower values are applied first
     * @param contributor pipeline contributor
     * @param <C> pipeline context type
     * @return same contribution sink
     */
    <C> AgelinaContributions pipeline(String name, int order, PipelineContributor<C> contributor);

    /**
     * Registers one slot schema contributor.
     *
     * @param name unique slot schema contribution name
     * @param order lower values are applied first
     * @param contributor slot schema contributor
     * @return same contribution sink
     */
    AgelinaContributions slotSchema(String name, int order, SlotSchemaContributor contributor);
}
