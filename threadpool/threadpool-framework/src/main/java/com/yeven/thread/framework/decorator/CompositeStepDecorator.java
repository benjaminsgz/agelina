package com.yeven.thread.framework.decorator;

import com.yeven.thread.framework.pipeline.AsyncStep;
import java.util.List;

/**
 * Composes multiple decorators into one decorator chain.
 *
 * <p>Decorators are applied in registration order, i.e. the first decorator in the list
 * becomes the outermost wrapper.</p>
 */
public class CompositeStepDecorator implements StepDecorator {

    private final List<StepDecorator> decorators;

    public CompositeStepDecorator(List<StepDecorator> decorators) {
        this.decorators = List.copyOf(decorators);
    }

    @Override
    public <C> AsyncStep<C> decorate(String stepName, AsyncStep<C> step) {
        AsyncStep<C> current = step;
        for (int i = decorators.size() - 1; i >= 0; i--) {
            current = decorators.get(i).decorate(stepName, current);
        }
        return current;
    }
}
