package com.yeven.thread.framework.plugin;

import com.yeven.thread.framework.graph.SlotAsyncGraphBuilder;

/**
 * 启动期向有向图（Graph）构建器贡献节点或终端定义的扩展程序接口。
 *
 * @param <C> 图上下文类型
 */
@FunctionalInterface
public interface GraphContributor<C> {

    /**
     * 向有向图构建器中添加图节点以及终端处理器定义。
     *
     * @param builder 插槽有向图构建器
     */
    void contribute(SlotAsyncGraphBuilder<C> builder);
}
