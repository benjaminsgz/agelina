package com.yeven.thread.framework.decorator;

import com.yeven.thread.framework.pipeline.AsyncStep;
import java.util.List;

/**
 * 组合步骤装饰器，将多个装饰器聚合为一个整体的装饰器调用链。
 *
 * <p>各个装饰器的应用顺序与其在 List 中的注册顺序相对应（即列表中的第一个装饰器将成为最外层的包装器）。</p>
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
