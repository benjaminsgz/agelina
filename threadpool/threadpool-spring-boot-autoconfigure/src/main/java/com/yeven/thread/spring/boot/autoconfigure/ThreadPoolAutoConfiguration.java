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
 * Spring Boot auto-configuration for thread-pool based async pipeline infrastructure.
 *
 * <p>This configuration publishes the following core beans:</p>
 * <ul>
 *     <li>{@code ioExecutor}/{@code cpuExecutor}</li>
 *     <li>{@link ExecutorRegistry}</li>
 *     <li>{@link ExecutionDispatcher}</li>
 *     <li>{@link AsyncStepFactory}</li>
 *     <li>{@link CompositeStepDecorator}</li>
 * </ul>
 *
 * <p>After importing the starter, applications can inject {@link AsyncStepFactory} to build
 * mode-aware async steps and compose them with {@link com.yeven.thread.framework.pipeline.AsyncPipelineBuilder}.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(ThreadPoolProperties.class)
public class ThreadPoolAutoConfiguration {

    /**
     * IO executor bean.
     *
     * @param properties pool properties
     * @return IO executor
     */
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
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /**
     * CPU executor bean.
     *
     * @param properties pool properties
     * @return CPU executor
     */
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
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /**
     * Builds the execution mode to executor mapping.
     *
     * @param ioExecutor IO executor
     * @param cpuExecutor CPU executor
     * @return registry bean
     */
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

    /**
     * Default dispatcher bean.
     *
     * @param executorRegistry executor registry
     * @return dispatcher
     */
    @Bean
    @ConditionalOnMissingBean
    public ExecutionDispatcher executionDispatcher(ExecutorRegistry executorRegistry) {
        return new DefaultExecutionDispatcher(executorRegistry);
    }

    /**
     * Factory bean used by business code to convert {@code StepDefinition} into runnable steps.
     *
     * @param executionDispatcher dispatcher
     * @return step factory
     */
    @Bean
    @ConditionalOnMissingBean
    public AsyncStepFactory asyncStepFactory(ExecutionDispatcher executionDispatcher) {
        return new AsyncStepFactory(executionDispatcher);
    }

    /**
     * Default logging decorator bean.
     *
     * @return logging decorator
     */
    @Bean
    @ConditionalOnMissingBean(LoggingStepDecorator.class)
    public LoggingStepDecorator loggingStepDecorator() {
        return new LoggingStepDecorator();
    }

    /**
     * Decorator chain bean.
     *
     * @param decoratorsProvider all available decorators from context
     * @return composite decorator
     */
    @Bean
    @ConditionalOnMissingBean(CompositeStepDecorator.class)
    public CompositeStepDecorator compositeStepDecorator(ObjectProvider<List<StepDecorator>> decoratorsProvider) {
        List<StepDecorator> decorators = decoratorsProvider.getIfAvailable(List::of);
        return new CompositeStepDecorator(decorators);
    }
}
