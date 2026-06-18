package com.yeven.thread.spring.boot.autoconfigure;

import com.yeven.thread.framework.decorator.CompositeStepDecorator;
import com.yeven.thread.framework.pipeline.AsyncStep;
import com.yeven.thread.framework.pipeline.AsyncStepBean;
import com.yeven.thread.framework.definition.StepDefinition;
import com.yeven.thread.framework.factory.AsyncStepFactory;
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
import org.springframework.util.ReflectionUtils;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

/**
 * 自动扫描并注册带 {@link AsyncStepBean} 注解方法为 {@link StepDefinition} 和 {@code AsyncStep} 单例 Bean 的 Spring 后置处理器。
 *
 * <p>动态注册的 Bean 名称前缀规则为：</p>
 * <ul>
 *     <li>{@code stepDefinition.<stepName>}</li>
 *     <li>{@code asyncStep.<stepName>}</li>
 * </ul>
 */
public class AsyncStepBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware {

    private static final String STEP_DEFINITION_BEAN_PREFIX = "stepDefinition.";
    private static final String ASYNC_STEP_BEAN_PREFIX = "asyncStep.";

    private final Map<String, String> stepOwnerByName = new ConcurrentHashMap<>();
    private ConfigurableListableBeanFactory beanFactory;

    /**
     * 构造步骤 Bean 后置处理器。
     */
    public AsyncStepBeanPostProcessor() {
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
        // 获取用户定义的类（防止代理类导致反射扫描不到注解）
        Class<?> userClass = AopUtils.getTargetClass(bean);
        // 筛选出所有带 @AsyncStepBean 注解的方法
        Map<Method, AsyncStepBean> annotatedMethods = MethodIntrospector.selectMethods(
                userClass,
                (MethodIntrospector.MetadataLookup<AsyncStepBean>) method ->
                        AnnotatedElementUtils.findMergedAnnotation(method, AsyncStepBean.class)
        );
        for (Map.Entry<Method, AsyncStepBean> entry : annotatedMethods.entrySet()) {
            Method method = entry.getKey();
            AsyncStepBean annotation = entry.getValue();
            // 强校验方法签名：必须是单参数且返回值类型与其一致或为其子类
            validateSignature(userClass, method);

            String stepName = annotation.name().isBlank() ? method.getName() : annotation.name().trim();
            // 校验并注册步骤名称的唯一性，防止步骤名命名冲突
            registerStepOwner(stepName, beanName, method);

            // 开启方法的可访问性（支持非 public 方法的反射执行）
            ReflectionUtils.makeAccessible(method);
            // 构造步骤定义元数据，封装反射调用逻辑
            StepDefinition<Object> definition = new StepDefinition<>(
                    stepName,
                    annotation.mode(),
                    context -> invokeStep(bean, method, context, stepName)
            );
            // 动态注册 StepDefinition Bean 到 Spring 容器中
            beanFactory.registerSingleton(stepDefinitionBeanName(stepName), definition);
            // 动态注册 AsyncStep Bean 到 Spring 容器中，使其可以被直接 @Autowired 注入
            beanFactory.registerSingleton(asyncStepBeanName(stepName), createAsyncStep(stepName, definition));
        }
        return bean;
    }

    /**
     * 懒加载步骤工厂和装饰器，避免 BeanPostProcessor 创建阶段提前实例化业务基础设施。
     */
    private AsyncStep<Object> createAsyncStep(String stepName, StepDefinition<Object> definition) {
        AsyncStepFactory asyncStepFactory = beanFactory.getBean(AsyncStepFactory.class);
        AsyncStep<Object> step = asyncStepFactory.create(definition);
        CompositeStepDecorator decorator = beanFactory.getBeanProvider(CompositeStepDecorator.class).getIfAvailable();
        return decorator != null ? decorator.decorate(stepName, step) : step;
    }

    /**
     * 校验方法签名的合法性。
     * 
     * <p>约束条件：</p>
     * <pre>
     * 1) 参数个数必须是 1 个；
     * 2) 返回值类型不能为 void；
     * 3) 参数类型必须能够接收返回值类型的值（确保可以在管道中传递）。
     * </pre>
     */
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

    /**
     * 登记步骤的拥有者，防止多个方法注册相同名称的异步步骤。
     */
    private void registerStepOwner(String stepName, String beanName, Method method) {
        String owner = beanName + "#" + method.getName();
        String previous = stepOwnerByName.putIfAbsent(stepName, owner);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate async step name '" + stepName + "'. Existing=" + previous + ", new=" + owner
            );
        }
    }

    /**
     * 执行具体步骤方法反射调用的包装。
     */
    private static Object invokeStep(Object bean, Method method, Object context, String stepName) {
        try {
            Object target = bean;
            Method methodToInvoke = method;
            if (AopUtils.isAopProxy(bean)) {
                methodToInvoke = AopUtils.selectInvocableMethod(method, bean.getClass());
                if (!methodToInvoke.getDeclaringClass().isInstance(bean)) {
                    if (bean instanceof Advised advised) {
                        try {
                            target = advised.getTargetSource().getTarget();
                            methodToInvoke = method;
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
            }
            return methodToInvoke.invoke(target, context);
        } catch (InvocationTargetException e) {
            // 反射调用发生异常时，拆包取出具体的业务异常并直接抛出，避免被包裹为 InvocationTargetException
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
