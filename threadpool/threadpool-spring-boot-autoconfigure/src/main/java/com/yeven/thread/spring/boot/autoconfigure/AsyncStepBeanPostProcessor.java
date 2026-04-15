package com.yeven.thread.spring.boot.autoconfigure;

import com.yeven.thread.framework.pipeline.AsyncStepBean;
import com.yeven.thread.framework.pipeline.AsyncStepFactory;
import com.yeven.thread.framework.pipeline.StepDefinition;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.MethodIntrospector;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Automatically discovers and registers {@link AsyncStepBean} annotated methods as
 * {@link StepDefinition} and {@code AsyncStep} singleton beans.
 *
 * <p>Registered bean names are {@code stepDefinition.<stepName>} and {@code asyncStep.<stepName>}.</p>
 */
public class AsyncStepBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware {

    private static final String STEP_DEFINITION_BEAN_PREFIX = "stepDefinition.";
    private static final String ASYNC_STEP_BEAN_PREFIX = "asyncStep.";

    private final AsyncStepFactory asyncStepFactory;
    private final Map<String, String> stepOwnerByName = new ConcurrentHashMap<>();
    private ConfigurableListableBeanFactory beanFactory;

    public AsyncStepBeanPostProcessor(AsyncStepFactory asyncStepFactory) {
        this.asyncStepFactory = Objects.requireNonNull(asyncStepFactory);
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory configurableBeanFactory)) {
            throw new IllegalStateException(
                    "AsyncStepBeanPostProcessor requires ConfigurableListableBeanFactory, but got "
                            + beanFactory.getClass().getName()
            );
        }
        this.beanFactory = configurableBeanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (beanFactory == null) {
            throw new IllegalStateException("BeanFactory is not initialized");
        }
        Class<?> userClass = ClassUtils.getUserClass(bean);
        Map<Method, AsyncStepBean> annotatedMethods = MethodIntrospector.selectMethods(
                userClass,
                (MethodIntrospector.MetadataLookup<AsyncStepBean>) method ->
                        AnnotatedElementUtils.findMergedAnnotation(method, AsyncStepBean.class)
        );
        for (Map.Entry<Method, AsyncStepBean> entry : annotatedMethods.entrySet()) {
            Method method = entry.getKey();
            AsyncStepBean annotation = entry.getValue();
            validateSignature(userClass, method);

            String stepName = annotation.name().isBlank() ? method.getName() : annotation.name().trim();
            registerStepOwner(stepName, beanName, method);

            ReflectionUtils.makeAccessible(method);
            StepDefinition<Object> definition = new StepDefinition<>(
                    stepName,
                    annotation.mode(),
                    context -> invokeStep(bean, method, context, stepName)
            );
            beanFactory.registerSingleton(stepDefinitionBeanName(stepName), definition);
            beanFactory.registerSingleton(asyncStepBeanName(stepName), asyncStepFactory.create(definition));
        }
        return bean;
    }

    private void validateSignature(Class<?> userClass, Method method) {
        if (method.getParameterCount() != 1) {
            throw new IllegalStateException(
                    "Method @" + AsyncStepBean.class.getSimpleName() + " must have exactly one argument: "
                            + userClass.getName() + "#" + method.getName()
            );
        }
        if (void.class.equals(method.getReturnType())) {
            throw new IllegalStateException(
                    "Method @" + AsyncStepBean.class.getSimpleName() + " must return context value: "
                            + userClass.getName() + "#" + method.getName()
            );
        }
        Class<?> parameterType = method.getParameterTypes()[0];
        Class<?> returnType = method.getReturnType();
        if (!parameterType.isAssignableFrom(returnType)) {
            throw new IllegalStateException(
                    "Method @" + AsyncStepBean.class.getSimpleName()
                            + " must return same context type. parameter=" + parameterType.getName()
                            + ", return=" + returnType.getName()
                            + ", method=" + userClass.getName() + "#" + method.getName()
            );
        }
    }

    private void registerStepOwner(String stepName, String beanName, Method method) {
        String owner = beanName + "#" + method.getName();
        String previous = stepOwnerByName.putIfAbsent(stepName, owner);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate async step name '" + stepName + "'. Existing=" + previous + ", new=" + owner
            );
        }
    }

    private static Object invokeStep(Object bean, Method method, Object context, String stepName) {
        try {
            return method.invoke(bean, context);
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (target instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Execution failed for step: " + stepName, target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access step method: " + stepName, e);
        }
    }

    private static String stepDefinitionBeanName(String stepName) {
        return STEP_DEFINITION_BEAN_PREFIX + stepName;
    }

    private static String asyncStepBeanName(String stepName) {
        return ASYNC_STEP_BEAN_PREFIX + stepName;
    }
}
