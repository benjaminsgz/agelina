package com.yeven.thread.framework.plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 不可变的启动期插件注册表。
 *
 * <p>扩展点贡献（Contributions）在系统构建期只进行一次排序。此注册表中的数组属于冷路径（Cold-Path）启动数据；
 * 编译后的线性管道和有向无环图应该只复制和提取它们所需的处理器、插槽 ID 以及运行期分发器，以避免运行期对注册表的检索和开销。</p>
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
     * 创建一个空注册表构建器。
     *
     * @return 注册表构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 根据指定的插件列表构建只读注册表。
     *
     * @param plugins 启动插件列表
     * @return 不可变插件注册表实例
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
     * 获取排序后的运行期环境贡献列表拷贝。
     *
     * @return 运行期环境贡献数组
     */
    public RuntimeContribution[] runtimes() {
        return Arrays.copyOf(runtimes, runtimes.length);
    }

    /**
     * 获取排序后的有向图贡献列表拷贝。
     *
     * @return 有向图贡献数组
     */
    public GraphContribution<?>[] graphs() {
        return Arrays.copyOf(graphs, graphs.length);
    }

    /**
     * 获取排序后的顺序管道贡献列表拷贝。
     *
     * @return 顺序管道贡献数组
     */
    public PipelineContribution<?>[] pipelines() {
        return Arrays.copyOf(pipelines, pipelines.length);
    }

    /**
     * 获取排序后的插槽 Schema 贡献列表拷贝。
     *
     * @return 插槽 Schema 贡献数组
     */
    public SlotSchemaContribution[] slotSchemas() {
        return Arrays.copyOf(slotSchemas, slotSchemas.length);
    }

    /**
     * 根据名称查询运行期环境贡献。
     *
     * @param name 运行期名称
     * @return 对应的贡献对象，未找到则返回 null
     */
    public RuntimeContribution runtime(String name) {
        return runtimeByName.get(name);
    }

    /**
     * 根据名称查询有向图贡献。
     *
     * @param name 有向图贡献名称
     * @return 对应的贡献对象，未找到则返回 null
     */
    public GraphContribution<?> graph(String name) {
        return graphByName.get(name);
    }

    /**
     * 根据名称查询顺序管道贡献。
     *
     * @param name 管道贡献名称
     * @return 对应的贡献对象，未找到则返回 null
     */
    public PipelineContribution<?> pipeline(String name) {
        return pipelineByName.get(name);
    }

    /**
     * 根据名称查询插槽 Schema 贡献。
     *
     * @param name 插槽 Schema 贡献名称
     * @return 对应的贡献对象，未找到则返回 null
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
     * 仅在启动阶段使用的可变注册表构建器。
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
         * 构建排序完成的不可变插件注册表实例。
         *
         * @return 插件注册表
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
