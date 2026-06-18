package com.yeven.thread.spring.boot.autoconfigure;

import com.yeven.thread.framework.decorator.CompositeStepDecorator;
import com.yeven.thread.framework.decorator.StepDecorator;
import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.dispatcher.ExecutionDispatcher;
import com.yeven.thread.framework.pipeline.AsyncStep;
import com.yeven.thread.framework.pipeline.AsyncStepBean;
import com.yeven.thread.framework.factory.AsyncStepFactory;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncStepBeanPostProcessorTest {

    @Test
    void shouldRegisterStepBeansFromAsyncStepAnnotation() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(SingleStepConfig.class);
            context.refresh();

            assertTrue(context.containsBean("stepDefinition.uppercase"));
            assertTrue(context.containsBean("asyncStep.uppercase"));

            @SuppressWarnings("unchecked")
            AsyncStep<String> step = (AsyncStep<String>) context.getBean("asyncStep.uppercase");
            assertEquals("[ABC]", step.apply("abc").join());
        }
    }

    @Test
    void shouldFailWhenAsyncStepNamesDuplicate() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(DuplicateStepNameConfig.class);
            assertThrows(BeansException.class, context::refresh);
        }
    }

    @Test
    void shouldFailWhenAsyncStepSignatureIsInvalid() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(InvalidSignatureConfig.class);
            assertThrows(BeansException.class, context::refresh);
        }
    }

    @Test
    void shouldRegisterStepBeansFromProxiedBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ProxyStepConfig.class);
            context.refresh();

            assertTrue(context.containsBean("stepDefinition.proxyStep"));
            assertTrue(context.containsBean("asyncStep.proxyStep"));

            @SuppressWarnings("unchecked")
            AsyncStep<String> step = (AsyncStep<String>) context.getBean("asyncStep.proxyStep");
            assertEquals("PROXY-ABC", step.apply("abc").join());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BaseConfig {

        @Bean
        ExecutionDispatcher executionDispatcher() {
            return new ExecutionDispatcher() {
                @Override
                public <T> CompletableFuture<T> dispatch(ExecutionMode mode, java.util.function.Supplier<T> supplier) {
                    try {
                        return CompletableFuture.completedFuture(supplier.get());
                    } catch (Throwable t) {
                        return CompletableFuture.failedFuture(t);
                    }
                }
            };
        }

        @Bean
        AsyncStepFactory asyncStepFactory(ExecutionDispatcher executionDispatcher) {
            return new AsyncStepFactory(executionDispatcher);
        }

        @Bean
        static AsyncStepBeanPostProcessor asyncStepBeanPostProcessor() {
            return new AsyncStepBeanPostProcessor();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SingleStepConfig extends BaseConfig {

        @Bean
        StepProvider stepProvider() {
            return new StepProvider();
        }

        @Bean
        CompositeStepDecorator compositeStepDecorator() {
            return new CompositeStepDecorator(List.of(new BracketStringDecorator()));
        }
    }

    static class BracketStringDecorator implements StepDecorator {

        @Override
        @SuppressWarnings("unchecked")
        public <C> AsyncStep<C> decorate(String stepName, AsyncStep<C> step) {
            return context -> step.apply(context).thenApply(result -> (C) ("[" + result + "]"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateStepNameConfig extends BaseConfig {

        @Bean
        StepProvider leftStepProvider() {
            return new StepProvider();
        }

        @Bean
        AnotherStepProvider rightStepProvider() {
            return new AnotherStepProvider();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InvalidSignatureConfig extends BaseConfig {

        @Bean
        InvalidStepProvider invalidStepProvider() {
            return new InvalidStepProvider();
        }
    }

    static class StepProvider {

        @AsyncStepBean(name = "uppercase", mode = ExecutionMode.DIRECT)
        public String uppercase(String input) {
            return input.toUpperCase(Locale.ROOT);
        }
    }

    static class AnotherStepProvider {

        @AsyncStepBean(name = "uppercase", mode = ExecutionMode.DIRECT)
        public String duplicate(String input) {
            return input + "-x";
        }
    }

    static class InvalidStepProvider {

        @AsyncStepBean(name = "invalid", mode = ExecutionMode.DIRECT)
        public String missingArgument() {
            return "x";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ProxyStepConfig extends BaseConfig {

        @Bean
        IStepProvider stepProvider() {
            org.springframework.aop.framework.ProxyFactory factory = new org.springframework.aop.framework.ProxyFactory(new StepProviderImpl());
            factory.addInterface(IStepProvider.class);
            return (IStepProvider) factory.getProxy();
        }
    }

    interface IStepProvider {
        String proxyStep(String input);
    }

    static class StepProviderImpl implements IStepProvider {
        @Override
        @AsyncStepBean(name = "proxyStep", mode = ExecutionMode.DIRECT)
        public String proxyStep(String input) {
            return "PROXY-" + input.toUpperCase(Locale.ROOT);
        }
    }
}
