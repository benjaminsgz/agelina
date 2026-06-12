package com.yeven.thread.framework.plugin;

/**
 * Agelina 框架仅在启动阶段生效的扩展点接口（插件接口）。
 *
 * <p>插件必须仅在应用程序启动期间贡献构建器、处理器、Schema 定义和运行期提供者。
 * 极速执行热路径（Hot Path）应该直接使用编译后的数组和函数表，而不应该包含任何运行时的插件查询逻辑，以确保性能最大化。</p>
 */
@FunctionalInterface
public interface AgelinaPlugin {

    /**
     * 在框架启动时注册自定义的贡献内容。
     *
     * @param contributions 可变的启动期扩展点注册接口
     */
    void contribute(AgelinaContributions contributions);
}
