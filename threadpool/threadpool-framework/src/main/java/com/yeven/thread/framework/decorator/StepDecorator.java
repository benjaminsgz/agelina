package com.yeven.thread.framework.decorator;

import com.yeven.thread.framework.pipeline.AsyncStep;

/**
 * Decorator for cross-cutting step enhancements.
 */
public interface StepDecorator {

    <C> AsyncStep<C> decorate(String stepName, AsyncStep<C> step);
}
