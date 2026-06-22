package com.yeven.thread.framework.decorator;

import com.yeven.thread.framework.pipeline.core.AsyncStep;
import java.util.List;

/**
 * 组合步骤装饰器，将多个装饰器聚合为一个整体的装饰器调用链。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>多重关注点聚合（Composite Pattern）：</b> 当系统中需要同时引入多个切面增强（如同时需要日志记录、耗时度量和安全审计等）时，单个装饰器难以维护。该组合装饰器能够将多个 {@link StepDecorator} 合并为一个单一的装饰器接口暴露给外部，符合“组合模式”设计原则。</li>
 *   <li><b>可预测的嵌套层次排序：</b> 内部通过反向遍历列表，确保在 List 中注册在前面的装饰器在运行时处于调用链的最外层（最先接收调用，最后结束处理），提供了可靠、确定、可配置的拦截嵌套行为。</li>
 * </ul>
 */
public class CompositeStepDecorator implements StepDecorator {

    private final List<StepDecorator> decorators;

    /**
     * 构造组合装饰器实例。
     *
     * @param decorators 步骤装饰器列表
     */
    public CompositeStepDecorator(List<StepDecorator> decorators) {
        this.decorators = List.copyOf(decorators);
    }

    @Override
    public <C> AsyncStep<C> decorate(String stepName, AsyncStep<C> step) {
        AsyncStep<C> current = step;
        // 从后往前遍历列表，从而保证排在前面的装饰器处于调用链的最外层（即最先执行）
        for (int i = decorators.size() - 1; i >= 0; i--) {
            current = decorators.get(i).decorate(stepName, current);
        }
        return current;
    }
}
