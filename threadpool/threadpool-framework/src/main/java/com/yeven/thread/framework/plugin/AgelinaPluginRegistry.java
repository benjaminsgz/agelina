package com.yeven.thread.framework.plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable startup plugin registry.
 *
 * <p>Contributions are sorted once at build time. The arrays in this registry are
 * cold-path boot data; compiled pipelines and graphs should copy only the resulting
 * handlers, slot ids, and runtime dispatchers they need.</p>
 */
public final class AgelinaPluginRegistry {

    private static final Comparator<RuntimeContribution> RUNTIME_ORDER =
            Comparator.comparingInt(RuntimeContribution::order).thenComparing(RuntimeContribution::name);
    private static final Comparator<GraphContribution<?>> GRAPH_ORDER =
            Comparator.comparingInt(GraphContribution<?>::order).thenComparing(GraphContribution::name);
    private static final Comparator<PipelineContribution<?>> PIPELINE_ORDER =
            Comparator.comparingInt(PipelineContribution<?>::order).thenComparing(PipelineContribution::name);
    private static final Comparator<SlotSchemaContribution> SLOT_SCHEMA_ORDER =
            Comparator.comparingInt(SlotSchemaContribution::order).thenComparing(SlotSchemaContribution::name);

    private final RuntimeContribution[] runtimes;
    private final GraphContribution<?>[] graphs;
    private final PipelineContribution<?>[] pipelines;
    private final SlotSchemaContribution[] slotSchemas;
    private final Map<String, RuntimeContribution> runtimeByName;
    private final Map<String, GraphContribution<?>> graphByName;
    private final Map<String, PipelineContribution<?>> pipelineByName;
    private final Map<String, SlotSchemaContribution> slotSchemaByName;

    private AgelinaPluginRegistry(Builder builder) {
        this.runtimes = sortedRuntimeArray(builder.runtimes);
        this.graphs = sortedGraphArray(builder.graphs);
        this.pipelines = sortedPipelineArray(builder.pipelines);
        this.slotSchemas = sortedSlotSchemaArray(builder.slotSchemas);
        this.runtimeByName = indexByName(this.runtimes);
        this.graphByName = indexByName(this.graphs);
        this.pipelineByName = indexByName(this.pipelines);
        this.slotSchemaByName = indexByName(this.slotSchemas);
    }

    /**
     * Creates one empty builder.
     *
     * @return registry builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builds one registry from plugins.
     *
     * @param plugins startup plugins
     * @return immutable registry
     */
    public static AgelinaPluginRegistry from(List<? extends AgelinaPlugin> plugins) {
        Objects.requireNonNull(plugins, "plugins");
        Builder builder = builder();
        for (AgelinaPlugin plugin : plugins) {
            Objects.requireNonNull(plugin, "plugin").contribute(builder);
        }
        return builder.build();
    }

    /**
     * @return sorted runtime contributions
     */
    public RuntimeContribution[] runtimes() {
        return Arrays.copyOf(runtimes, runtimes.length);
    }

    /**
     * @return sorted graph contributions
     */
    public GraphContribution<?>[] graphs() {
        return Arrays.copyOf(graphs, graphs.length);
    }

    /**
     * @return sorted pipeline contributions
     */
    public PipelineContribution<?>[] pipelines() {
        return Arrays.copyOf(pipelines, pipelines.length);
    }

    /**
     * @return sorted slot schema contributions
     */
    public SlotSchemaContribution[] slotSchemas() {
        return Arrays.copyOf(slotSchemas, slotSchemas.length);
    }

    /**
     * Looks up one runtime contribution by name.
     *
     * @param name runtime name
     * @return contribution or null
     */
    public RuntimeContribution runtime(String name) {
        return runtimeByName.get(name);
    }

    /**
     * Looks up one graph contribution by name.
     *
     * @param name graph contribution name
     * @return contribution or null
     */
    public GraphContribution<?> graph(String name) {
        return graphByName.get(name);
    }

    /**
     * Looks up one pipeline contribution by name.
     *
     * @param name pipeline contribution name
     * @return contribution or null
     */
    public PipelineContribution<?> pipeline(String name) {
        return pipelineByName.get(name);
    }

    /**
     * Looks up one slot schema contribution by name.
     *
     * @param name slot schema contribution name
     * @return contribution or null
     */
    public SlotSchemaContribution slotSchema(String name) {
        return slotSchemaByName.get(name);
    }

    private static RuntimeContribution[] sortedRuntimeArray(List<RuntimeContribution> values) {
        RuntimeContribution[] array = values.toArray(RuntimeContribution[]::new);
        Arrays.sort(array, RUNTIME_ORDER);
        return array;
    }

    private static GraphContribution<?>[] sortedGraphArray(List<GraphContribution<?>> values) {
        GraphContribution<?>[] array = values.toArray(GraphContribution[]::new);
        Arrays.sort(array, GRAPH_ORDER);
        return array;
    }

    private static PipelineContribution<?>[] sortedPipelineArray(List<PipelineContribution<?>> values) {
        PipelineContribution<?>[] array = values.toArray(PipelineContribution[]::new);
        Arrays.sort(array, PIPELINE_ORDER);
        return array;
    }

    private static SlotSchemaContribution[] sortedSlotSchemaArray(List<SlotSchemaContribution> values) {
        SlotSchemaContribution[] array = values.toArray(SlotSchemaContribution[]::new);
        Arrays.sort(array, SLOT_SCHEMA_ORDER);
        return array;
    }

    private static <T extends NamedContribution> Map<String, T> indexByName(T[] values) {
        LinkedHashMap<String, T> index = new LinkedHashMap<>(values.length);
        for (T value : values) {
            index.put(value.name(), value);
        }
        return Map.copyOf(index);
    }

    /**
     * Mutable registry builder used only at startup.
     */
    public static final class Builder implements AgelinaContributions {

        private final List<RuntimeContribution> runtimes = new ArrayList<>();
        private final List<GraphContribution<?>> graphs = new ArrayList<>();
        private final List<PipelineContribution<?>> pipelines = new ArrayList<>();
        private final List<SlotSchemaContribution> slotSchemas = new ArrayList<>();

        private final Map<String, RuntimeContribution> runtimeByName = new LinkedHashMap<>();
        private final Map<String, GraphContribution<?>> graphByName = new LinkedHashMap<>();
        private final Map<String, PipelineContribution<?>> pipelineByName = new LinkedHashMap<>();
        private final Map<String, SlotSchemaContribution> slotSchemaByName = new LinkedHashMap<>();

        private Builder() {
        }

        @Override
        public AgelinaContributions runtime(String name, int order, RuntimeProvider provider) {
            RuntimeContribution contribution = new RuntimeContribution(name, order, provider);
            addUnique(runtimeByName, runtimes, contribution, "runtime");
            return this;
        }

        @Override
        public <C> AgelinaContributions graph(String name, int order, GraphContributor<C> contributor) {
            GraphContribution<C> contribution = new GraphContribution<>(name, order, contributor);
            addUnique(graphByName, graphs, contribution, "graph");
            return this;
        }

        @Override
        public <C> AgelinaContributions pipeline(String name, int order, PipelineContributor<C> contributor) {
            PipelineContribution<C> contribution = new PipelineContribution<>(name, order, contributor);
            addUnique(pipelineByName, pipelines, contribution, "pipeline");
            return this;
        }

        @Override
        public AgelinaContributions slotSchema(String name, int order, SlotSchemaContributor contributor) {
            SlotSchemaContribution contribution = new SlotSchemaContribution(name, order, contributor);
            addUnique(slotSchemaByName, slotSchemas, contribution, "slot schema");
            return this;
        }

        /**
         * Builds an immutable sorted registry snapshot.
         *
         * @return plugin registry
         */
        public AgelinaPluginRegistry build() {
            return new AgelinaPluginRegistry(this);
        }

        private static <T extends NamedContribution> void addUnique(
                Map<String, T> index,
                List<? super T> values,
                T contribution,
                String kind
        ) {
            T existing = index.putIfAbsent(contribution.name(), contribution);
            if (existing != null) {
                throw new IllegalArgumentException(
                        "Duplicate " + kind + " contribution name: " + contribution.name()
                );
            }
            values.add(contribution);
        }
    }
}
