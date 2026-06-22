package com.yeven.thread.framework.pipeline.core;

import com.yeven.thread.framework.constant.ExecutionMode;
import java.util.function.Function;

/**
 * 构建 {@link AsyncStep} 的声明式元数据定义类。
 *
 * <p>该类将“执行什么逻辑”（{@code handler}）与“在哪个线程池执行”（{@code mode}）进行了清晰的解耦分离，
 * 从而保证了框架对线程路由配置的高灵活性与数据驱动特性。</p>
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
