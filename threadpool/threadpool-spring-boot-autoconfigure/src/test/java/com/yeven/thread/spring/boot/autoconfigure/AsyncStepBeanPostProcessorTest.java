package com.yeven.thread.spring.boot.autoconfigure;

import com.yeven.thread.framework.executor.ExecutionDispatcher;
import com.yeven.thread.framework.executor.ExecutionMode;
import com.yeven.thread.framework.pipeline.AsyncStep;
import com.yeven.thread.framework.pipeline.AsyncStepBean;
import com.yeven.thread.framework.pipeline.AsyncStepFactory;
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
            assertEquals("ABC", step.apply("abc").join());
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
        static AsyncStepBeanPostProcessor asyncStepBeanPostProcessor(AsyncStepFactory asyncStepFactory) {
            return new AsyncStepBeanPostProcessor(asyncStepFactory);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SingleStepConfig extends BaseConfig {

        @Bean
        StepProvider stepProvider() {
            return new StepProvider();
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
}
