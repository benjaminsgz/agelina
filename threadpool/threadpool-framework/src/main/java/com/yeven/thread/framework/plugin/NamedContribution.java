package com.yeven.thread.framework.plugin;

/**
 * 具有唯一名称的插件扩展点贡献接口。
 */
interface NamedContribution {

    /**
     * 获取当前贡献的唯一标识名称。
     *
     * @return 扩展贡献名称
     */
    String name();
}
