package com.yeven.thread.framework.decorator;

import com.yeven.thread.framework.pipeline.AsyncStep;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logging decorator for pipeline steps.
 */
public class LoggingStepDecorator implements StepDecorator {

    private static final Logger LOGGER = Logger.getLogger(LoggingStepDecorator.class.getName());

    @Override
    public <C> AsyncStep<C> decorate(String stepName, AsyncStep<C> step) {
        return context -> {
            long start = System.currentTimeMillis();
            return step.apply(context).whenComplete((result, ex) -> {
                long cost = System.currentTimeMillis() - start;
                if (ex == null) {
                    LOGGER.info(() -> "[STEP] " + stepName + " SUCCESS cost=" + cost + "ms");
                } else {
                    LOGGER.log(Level.WARNING,
                            "[STEP] " + stepName + " FAILED cost=" + cost + "ms",
                            ex);
                }
            });
        };
    }
}
