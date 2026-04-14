package com.yeven.thread.framework.decorator;

import com.yeven.thread.framework.pipeline.AsyncStep;

/**
 * Decorator for applying cross-cutting behavior around {@link AsyncStep}.
 *
 * <p>Typical use cases: logging, tracing, metrics and timeout guards.</p>
 */
public interface StepDecorator {

    /**
     * Wraps one step.
     *
     * @param stepName step logical name
     * @param step original step
     * @param <C> context type
     * @return decorated step
     */
    <C> AsyncStep<C> decorate(String stepName, AsyncStep<C> step);
}
