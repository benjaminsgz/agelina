package com.yeven.thread.framework.plugin;

import java.util.Objects;

/**
 * 启动期间注册的插槽 Schema 贡献描述符。
 *
 * @param name 唯一的贡献名称
 * @param order 启动加载排序权重，数值越小应用越早
 * @param contributor 插槽 Schema 贡献器实例
 */
public record SlotSchemaContribution(
         String name,
         int order,
         SlotSchemaContributor contributor
) implements NamedContribution {

    public SlotSchemaContribution {
        ContributionNames.validate(name, "name");
        Objects.requireNonNull(contributor, "contributor");
    }
}
