package com.yeven.thread.framework.definition;

import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.pipeline.AsyncStep;
import java.util.function.Function;

/**
 * 构建 {@link AsyncStep} 的声明式元数据定义类。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>配置与执行隔离：</b> 将“具体执行什么业务逻辑（{@code handler}）”与“具体在哪里运行（{@code mode}）”在逻辑和元数据定义层彻底解耦，提供了高度灵活的线程路由控制。</li>
 *   <li><b>支持切面与动态修饰：</b> 作为描述单步任务的最小完备元数据单元，它可以极其方便地被各类步骤装饰器（例如性能日志记录、重试逻辑、安全审计等 {@link com.yeven.thread.framework.decorator.StepDecorator}）进行拦截和加工包装，而无需侵入底层步骤实现本身。</li>
 * </ul>
 *
 * @param <C> 管道上下文类型
 */
public final class StepDefinition<C> {

    private final String name;
    private final ExecutionMode mode;
    private final Function<C, C> handler;

    /**
     * 创建一个步骤定义实例。
     *
     * @param name 逻辑步骤名称，用于日志记录、性能监控和切面装饰器
     * @param mode 执行路由模式，决定任务路由到哪个线程池运行
     * @param handler 当前步骤的具体业务处理逻辑函数
     */
    public StepDefinition(String name, ExecutionMode mode, Function<C, C> handler) {
        this.name = name;
        this.mode = mode;
        this.handler = handler;
    }

    /**
     * 获取逻辑步骤名称。
     *
     * @return 逻辑步骤名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取执行路由模式。
     *
     * @return 执行路由模式 {@link ExecutionMode}
     */
    public ExecutionMode getMode() {
        return mode;
    }

    /**
     * 获取业务处理逻辑函数。
     *
     * @return 业务处理函数
     */
    public Function<C, C> getHandler() {
        return handler;
    }
}
