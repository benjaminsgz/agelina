package com.yeven.thread.framework.decorator;

import com.yeven.thread.framework.pipeline.AsyncStep;

/**
 * 步骤装饰器接口，用于在 {@link AsyncStep} 执行前后织入横切关注点（Cross-cutting Behavior）。
 * 
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>非侵入式切面增强（AOP）：</b> 遵循面向对象设计的装饰器模式，允许开发者在完全不修改步骤核心业务逻辑的情况下，动态织入各种基础设施行为。典型应用场景包括：分布式链路追踪、性能耗时度量、日志追踪标记注入、幂等校验及重试机制。</li>
 *   <li><b>支持链式可配置的装饰器组合：</b> 极易实现多层拦截包装，为流程控制和诊断提供了高度的松耦合与组合性。</li>
 * </ul>
 */
public interface StepDecorator {

    /**
     * 包装/装饰一个异步步骤。
     *
     * @param stepName 异步步骤的逻辑名称
     * @param step 被装饰的原始步骤对象
     * @param <C> 业务上下文类型
     * @return 织入横切关注点后的新异步步骤对象
     */
    <C> AsyncStep<C> decorate(String stepName, AsyncStep<C> step);
}
