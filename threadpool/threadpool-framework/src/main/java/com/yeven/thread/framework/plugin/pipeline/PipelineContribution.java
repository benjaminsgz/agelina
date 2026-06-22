package com.yeven.thread.framework.plugin.pipeline;

import com.yeven.thread.framework.plugin.common.ContributionNames;
import com.yeven.thread.framework.plugin.common.NamedContribution;
import java.util.Objects;

/**
 * 启动期间注册的顺序异步管道贡献描述符。
 *
 * @param name        唯一的贡献名称
 * @param order       启动加载排序权重，数值越小应用越早
 * @param contributor 管道贡献器实例
 * @param <C>         管道上下文类型
 */
public record PipelineContribution<C>(
        String name,
        int order,
        PipelineContributor<C> contributor) implements NamedContribution {

    public PipelineContribution {
        ContributionNames.validate(name, "name");
        Objects.requireNonNull(contributor, "contributor");
    }
}
