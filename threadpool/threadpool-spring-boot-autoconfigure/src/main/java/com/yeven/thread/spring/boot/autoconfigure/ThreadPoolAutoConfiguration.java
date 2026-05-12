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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    @Bean(name = "ioExecutor", destroyMethod = "")
    @ConditionalOnMissingBean(name = "ioExecutor")
    public ThreadPoolExecutor ioExecutor(ThreadPoolProperties properties) {
        return createExecutor("io", "io-pool", properties.getIo());
    }

    /**
     * CPU executor bean.
     *
     * @param properties pool properties
     * @return CPU executor
     */
    @Bean(name = "cpuExecutor", destroyMethod = "")
    @ConditionalOnMissingBean(name = "cpuExecutor")
    public ThreadPoolExecutor cpuExecutor(ThreadPoolProperties properties) {
        return createExecutor("cpu", "cpu-pool", properties.getCpu());
    }

    private RejectedExecutionHandler mapPolicy(ThreadPoolProperties.RejectionPolicy policy) {
        return switch (policy) {
            case ABORT -> new ThreadPoolExecutor.AbortPolicy();
            case CALLER_RUNS -> new ThreadPoolExecutor.CallerRunsPolicy();
            case DISCARD -> new ThreadPoolExecutor.DiscardPolicy();
            case DISCARD_OLDEST -> new ThreadPoolExecutor.DiscardOldestPolicy();
        };
    }

    private ThreadPoolExecutor createExecutor(String poolName, String threadPrefix, ThreadPoolProperties.Pool pool) {
        pool.validate(poolName);
        return ThreadPoolFactory.create(
                threadPrefix,
                pool.getCoreSize(),
                pool.getMaxSize(),
                pool.getQueueType(),
                pool.getQueueCapacity(),
                pool.getKeepAliveSeconds(),
                mapPolicy(pool.getRejectionPolicy())
        );
    }

    @Bean(name = "threadPoolLifecycle", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "threadPoolLifecycle")
    public GracefulShutdown threadPoolLifecycle(
            @Qualifier("ioExecutor") ThreadPoolExecutor ioExecutor,
            @Qualifier("cpuExecutor") ThreadPoolExecutor cpuExecutor
    ) {
        return new GracefulShutdown(ioExecutor, cpuExecutor);
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
     * Scans all initialized Spring beans and auto-registers methods annotated with
     * {@link com.yeven.thread.framework.pipeline.AsyncStepBean}.
     *
     * @param asyncStepFactory step factory
     * @return bean post processor
     */
    @Bean
    @ConditionalOnMissingBean
    public static AsyncStepBeanPostProcessor asyncStepBeanPostProcessor(AsyncStepFactory asyncStepFactory) {
        return new AsyncStepBeanPostProcessor(asyncStepFactory);
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

    static final class GracefulShutdown {
        private final List<ExecutorService> executors;

        private GracefulShutdown(ExecutorService... executors) {
            this.executors = List.of(executors);
        }

        public void shutdown() {
            for (ExecutorService executor : executors) {
                executor.shutdown();
            }
            for (ExecutorService executor : executors) {
                awaitTermination(executor);
            }
        }

        private void awaitTermination(ExecutorService executor) {
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
