package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.ExecutionMode;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an asynchronous step.
 *
 * <p>Methods annotated with this will be automatically scanned by Spring and registered
 * as {@link StepDefinition} and {@link AsyncStep} beans.</p>
 *
 * <p>The method must take one parameter (the context) and return the same type of context.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AsyncStepBean {

    /**
     * Logical name of the step. If empty, the method name is used.
     *
     * @return step name
     */
    String name() default "";

    /**
     * Execution mode for this step.
     *
     * @return execution mode
     */
    ExecutionMode mode() default ExecutionMode.IO;
}
