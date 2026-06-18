package com.yeven.thread.framework.plugin;

import com.yeven.thread.framework.table.SlotSymbolTable;

/**
 * 启动期声明并分配符号插槽（Symbolic Slots）的扩展程序接口。
 */
@FunctionalInterface
public interface SlotSchemaContributor {

    /**
     * 分配和声明所需的符号插槽名称及 ID。
     *
     * @param builder 槽符号构建器
     */
    void contribute(SlotSymbolTable.Builder builder);
}
