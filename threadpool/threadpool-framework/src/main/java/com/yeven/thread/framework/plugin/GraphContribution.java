package com.yeven.thread.framework.plugin;

import java.util.Objects;

/**
 * 启动期间注册的有向图贡献描述符。
 *
 * @param name 唯一的贡献名称
 * @param order 启动加载排序权重，数值越小应用越早
 * @param contributor 图贡献器实例
 * @param <C> 图上下文类型
 */
public record GraphContribution<C>(
        String name,
        int order,
        GraphContributor<C> contributor
) implements NamedContribution {

    public GraphContribution {
        ContributionNames.validate(name, "name");
        Objects.requireNonNull(contributor, "contributor");
    }
}
