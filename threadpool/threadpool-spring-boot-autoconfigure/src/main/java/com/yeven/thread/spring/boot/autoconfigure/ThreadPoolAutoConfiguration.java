package com.yeven.thread.spring.boot.autoconfigure;

import com.yeven.thread.framework.decorator.CompositeStepDecorator;
import com.yeven.thread.framework.decorator.LoggingStepDecorator;
import com.yeven.thread.framework.decorator.StepDecorator;
import com.yeven.thread.framework.executor.DefaultExecutionDispatcher;
import com.yeven.thread.framework.executor.ExecutionDispatcher;
import com.yeven.thread.framework.executor.ExecutionMode;
import com.yeven.thread.framework.executor.ExecutorRegistry;
import com.yeven.thread.framework.executor.ThreadPoolFactory;
import com.yeven.thread.framework.pipeline.AsyncStepFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for framework execution and pipeline infrastructure.
 */
@AutoConfiguration
@EnableConfigurationProperties(ThreadPoolProperties.class)
public class ThreadPoolAutoConfiguration {

    @Bean("ioExecutor")
    @ConditionalOnMissingBean(name = "ioExecutor")
    public Executor ioExecutor(ThreadPoolProperties properties) {
        ThreadPoolProperties.Pool io = properties.getIo();
        return ThreadPoolFactory.create(
                "io-pool",
                io.getCoreSize(),
                io.getMaxSize(),
                io.getQueueCapacity(),
                io.getKeepAliveSeconds(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Bean("cpuExecutor")
    @ConditionalOnMissingBean(name = "cpuExecutor")
    public Executor cpuExecutor(ThreadPoolProperties properties) {
        ThreadPoolProperties.Pool cpu = properties.getCpu();
        return ThreadPoolFactory.create(
                "cpu-pool",
                cpu.getCoreSize(),
                cpu.getMaxSize(),
                cpu.getQueueCapacity(),
                cpu.getKeepAliveSeconds(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutorRegistry executorRegistry(
            @Qualifier("ioExecutor") Executor ioExecutor,
            @Qualifier("cpuExecutor") Executor cpuExecutor
    ) {
        return new ExecutorRegistry(Map.of(
                ExecutionMode.IO, ioExecutor,
                ExecutionMode.CPU, cpuExecutor
        ));
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutionDispatcher executionDispatcher(ExecutorRegistry executorRegistry) {
        return new DefaultExecutionDispatcher(executorRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public AsyncStepFactory asyncStepFactory(ExecutionDispatcher executionDispatcher) {
        return new AsyncStepFactory(executionDispatcher);
    }

    @Bean
    @ConditionalOnMissingBean(LoggingStepDecorator.class)
    public LoggingStepDecorator loggingStepDecorator() {
        return new LoggingStepDecorator();
    }

    @Bean
    @ConditionalOnMissingBean(CompositeStepDecorator.class)
    public CompositeStepDecorator compositeStepDecorator(ObjectProvider<List<StepDecorator>> decoratorsProvider) {
        List<StepDecorator> decorators = decoratorsProvider.getIfAvailable(List::of);
        return new CompositeStepDecorator(decorators);
    }
}
