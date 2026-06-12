package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.ExecutionMode;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注一个方法为异步步骤。
 *
 * <p>被此注解标注的方法将被 Spring 自动扫描并注册为 {@link StepDefinition} 和 {@link AsyncStep} Bean。</p>
 *
 * <p>该注解标注的方法必须满足约束：接收且仅接收一个参数（上下文对象），并且返回值必须与该参数类型一致（即兼容上下文类型）。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AsyncStepBean {

    /**
     * 步骤的逻辑名称。如果为空，则默认使用标注的方法名。
     *
     * @return 步骤名称
     */
    String name() default "";

    /**
     * 该步骤的任务执行路由模式。
     *
     * @return 执行路由模式 {@link ExecutionMode}，默认为 {@link ExecutionMode#IO}
     */
    ExecutionMode mode() default ExecutionMode.IO;
}
