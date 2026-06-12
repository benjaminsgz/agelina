package com.yeven.thread.framework.decorator;

import com.yeven.thread.framework.pipeline.AsyncStep;

/**
 * 步骤装饰器接口，用于在 {@link AsyncStep} 执行前后织入横切关注点（Cross-cutting Behavior）。
 *
 * <p>典型应用场景包括：日志记录、链路追踪（Tracing）、监控指标记录（Metrics）以及超时熔断保护等。</p>
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
