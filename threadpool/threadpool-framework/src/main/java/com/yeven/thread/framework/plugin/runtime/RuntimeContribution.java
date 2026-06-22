package com.yeven.thread.framework.plugin.runtime;

import com.yeven.thread.framework.plugin.common.ContributionNames;
import com.yeven.thread.framework.plugin.common.NamedContribution;
import java.util.Objects;

/**
 * 启动期间注册的运行期提供者贡献描述符。
 *
 * @param name     唯一的贡献名称
 * @param order    启动加载排序权重，数值越小应用越早
 * @param provider 运行期提供者实例
 */
public record RuntimeContribution(
        String name,
        int order,
        RuntimeProvider provider) implements NamedContribution {

    public RuntimeContribution {
        ContributionNames.validate(name, "name");
        Objects.requireNonNull(provider, "provider");
    }
}
