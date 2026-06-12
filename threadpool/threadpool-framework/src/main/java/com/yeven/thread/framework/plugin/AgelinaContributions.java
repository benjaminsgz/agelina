package com.yeven.thread.framework.plugin;

/**
 * 启动期贡献注册接收接口，由 {@link AgelinaPlugin} 使用。
 *
 * <p>注册的贡献包含相应的处理器和描述符。它们在系统启动阶段被框架的编译器消费，之后将从请求处理热路径中丢弃。</p>
 */
public interface AgelinaContributions {

    /**
     * 注册一个运行期环境提供者。
     *
     * @param name 唯一的运行期提供者名称
     * @param order 排序权重，数值越小应用越早
     * @param provider 运行期提供者实例
     * @return 当前注册接口实例，用于链式调用
     */
    AgelinaContributions runtime(String name, int order, RuntimeProvider provider);

    /**
     * 注册一个有向图（Graph）贡献者。
     *
     * @param name 唯一的图贡献者名称
     * @param order 排序权重，数值越小应用越早
     * @param contributor 图贡献者实例
     * @param <C> 图上下文类型
     * @return 当前注册接口实例，用于链式调用
     */
    <C> AgelinaContributions graph(String name, int order, GraphContributor<C> contributor);

    /**
     * 注册一个异步管道（Pipeline）贡献者。
     *
     * @param name 唯一的管道贡献者名称
     * @param order 排序权重，数值越小应用越早
     * @param contributor 管道贡献者实例
     * @param <C> 管道上下文类型
     * @return 当前注册接口实例，用于链式调用
     */
    <C> AgelinaContributions pipeline(String name, int order, PipelineContributor<C> contributor);

    /**
     * 注册一个插槽 Schema 贡献者。
     *
     * @param name 唯一的插槽 Schema 贡献者名称
     * @param order 排序权重，数值越小应用越早
     * @param contributor 插槽 Schema 贡献者实例
     * @return 当前注册接口实例，用于链式调用
     */
    AgelinaContributions slotSchema(String name, int order, SlotSchemaContributor contributor);
}
