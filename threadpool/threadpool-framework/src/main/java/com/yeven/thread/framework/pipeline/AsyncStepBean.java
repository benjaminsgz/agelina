package com.yeven.thread.framework.pipeline;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.yeven.thread.framework.constant.ExecutionMode;

/**
 * 标注一个方法为异步步骤的注解。
 * 
 * <p>
 * <b>设计必要性与核心价值：</b>
 * </p>
 * <ul>
 * <li><b>声明式开发体验：</b> 提供声明式（Declarative）编程入口，开发只需在普通的 Spring Bean
 * 方法上添加该注解，即可由框架自动识别并注册为 {@link com.yeven.thread.framework.definition.StepDefinition} 与 {@link AsyncStep}
 * 实例，大幅简化了手工装配的样板代码。</li>
 * <li><b>方法签名强规约：</b> 强力规约被标注方法的签名必须接收单个上下文对象，并返回相同类型的上下文对象，确保在 Spring
 * 自动配置扫描时维持管道的类型一致性。</li>
 * </ul>
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
