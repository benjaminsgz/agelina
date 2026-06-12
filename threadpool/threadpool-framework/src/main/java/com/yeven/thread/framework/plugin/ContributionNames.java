package com.yeven.thread.framework.plugin;

/**
 * 插件扩展贡献名称校验工具类。
 */
final class ContributionNames {

    private ContributionNames() {
    }

    /**
     * 校验指定的属性名称是否为空。
     *
     * @param value 属性值
     * @param field 字段名称描述，用于异常日志中
     * @throws IllegalArgumentException 当 value 为空时抛出
     */
    static void validate(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
