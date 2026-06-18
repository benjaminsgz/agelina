package com.yeven.thread.spring.boot.autoconfigure;

import com.yeven.thread.framework.decorator.CompositeStepDecorator;
import com.yeven.thread.framework.decorator.LoggingStepDecorator;
import com.yeven.thread.framework.decorator.StepDecorator;
import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.dispatcher.DefaultExecutionDispatcher;
import com.yeven.thread.framework.dispatcher.ExecutionDispatcher;
import com.yeven.thread.framework.executor.ExecutorRegistry;
import com.yeven.thread.framework.executor.ThreadPoolFactory;
import com.yeven.thread.framework.factory.AsyncStepFactory;
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
 * 基于线程池隔离的异步管道和有向无环图（DAG）编排基础设施的 Spring Boot 自动配置类。
 *
 * <p>本配置类负责发布以下核心 Bean：</p>
 * <ul>
 *     <li>{@code ioExecutor} (用于阻塞型 I/O 任务的线程池) 与 {@code cpuExecutor} (用于计算密集型任务的线程池)</li>
 *     <li>{@link ExecutorRegistry} (执行器注册表)</li>
 *     <li>{@link ExecutionDispatcher} (异步任务分发器)</li>
 *     <li>{@link AsyncStepFactory} (异步步骤工厂)</li>
 *     <li>{@link CompositeStepDecorator} (装饰器组合链)</li>
 * </ul>
 *
 * <p>引入对应的 starter 后，业务模块可以直接注入 {@link AsyncStepFactory} 来便捷地创建具备执行模式路由特性的异步步骤，
 * 并通过 {@link com.yeven.thread.framework.pipeline.AsyncPipelineBuilder} 对它们进行流式编排组合。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(ThreadPoolProperties.class)
public class ThreadPoolAutoConfiguration {

    /**
     * 注册用于处理 I/O 密集型阻塞操作的线程池 Bean。
     *
     * @param properties 线程池配置属性
     * @return IO 线程池 {@link ThreadPoolExecutor} 实例
     */
    @Bean(name = "ioExecutor", destroyMethod = "")
    @ConditionalOnMissingBean(name = "ioExecutor")
    public ThreadPoolExecutor ioExecutor(ThreadPoolProperties properties) {
        return createExecutor("io", "io-pool", properties.getIo());
    }

    /**
     * 注册用于处理 CPU 密集型计算操作的线程池 Bean。
     *
     * @param properties 线程池配置属性
     * @return CPU 线程池 {@link ThreadPoolExecutor} 实例
     */
    @Bean(name = "cpuExecutor", destroyMethod = "")
    @ConditionalOnMissingBean(name = "cpuExecutor")
    public ThreadPoolExecutor cpuExecutor(ThreadPoolProperties properties) {
        return createExecutor("cpu", "cpu-pool", properties.getCpu());
    }

    /**
     * 将配置中的拒绝策略枚举转换为 Java 并发包标准的拒绝策略对象。
     *
     * <p>异步任务不能被静默丢弃，否则对应的 {@code CompletableFuture} 可能永远无法完成。
     * 因此只映射可观测的饱和策略。</p>
     */
    private RejectedExecutionHandler mapPolicy(ThreadPoolProperties.RejectionPolicy policy) {
        return switch (policy) {
            case ABORT -> new ThreadPoolExecutor.AbortPolicy();
            case CALLER_RUNS -> new ThreadPoolExecutor.CallerRunsPolicy();
            case DISCARD, DISCARD_OLDEST -> throw new IllegalStateException(
                    "Silent rejection policy is not supported for async orchestration: " + policy);
        };
    }

    /**
     * 辅助方法：根据配置参数实际创建线程池。
     */
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

    /**
     * 注册线程池生命周期优雅关闭管理的 Bean。
     */
    @Bean(name = "threadPoolLifecycle", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "threadPoolLifecycle")
    public GracefulShutdown threadPoolLifecycle(
            @Qualifier("ioExecutor") ThreadPoolExecutor ioExecutor,
            @Qualifier("cpuExecutor") ThreadPoolExecutor cpuExecutor
    ) {
        return new GracefulShutdown(ioExecutor, cpuExecutor);
    }

    /**
     * 注册执行器注册表 Bean，维护 {@link ExecutionMode} 与具体线程池的对应关系。
     *
     * @param ioExecutor IO 线程池
     * @param cpuExecutor CPU 线程池
     * @return 线程池映射注册表 {@link ExecutorRegistry} 实例
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
     * 注册默认的异步分发器 Bean。
     *
     * @param executorRegistry 线程池注册表
     * @return 分发器 {@link ExecutionDispatcher} 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ExecutionDispatcher executionDispatcher(ExecutorRegistry executorRegistry) {
        return new DefaultExecutionDispatcher(executorRegistry);
    }

    /**
     * 注册异步步骤工厂 Bean，业务代码可以通过它将 {@link com.yeven.thread.framework.pipeline.StepDefinition} 包装为可运行的步骤。
     *
     * @param executionDispatcher 异步分发器
     * @return 步骤工厂 {@link AsyncStepFactory} 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public AsyncStepFactory asyncStepFactory(ExecutionDispatcher executionDispatcher) {
        return new AsyncStepFactory(executionDispatcher);
    }

    /**
     * 注册静态 Spring Bean 后置处理器，负责扫描容器中所有带有 {@link com.yeven.thread.framework.pipeline.AsyncStepBean} 注解的方法，
     * 并动态将其声明和注册为 Spring 中的步骤定义与异步步骤 Bean。
     *
     * @return 步骤注解后置处理器 {@link AsyncStepBeanPostProcessor} 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public static AsyncStepBeanPostProcessor asyncStepBeanPostProcessor() {
        return new AsyncStepBeanPostProcessor();
    }

    /**
     * 注册默认的步骤日志切面装饰器。
     *
     * @return 日志装饰器 {@link LoggingStepDecorator} 实例
     */
    @Bean
    @ConditionalOnMissingBean(LoggingStepDecorator.class)
    public LoggingStepDecorator loggingStepDecorator() {
        return new LoggingStepDecorator();
    }

    /**
     * 注册组合装饰器链 Bean，自动聚合 Spring 容器中所有声明的 {@link StepDecorator} 装饰器。
     *
     * @param decoratorsProvider 容器中所有装饰器的提供者
     * @return 组合装饰器 {@link CompositeStepDecorator} 实例
     */
    @Bean
    @ConditionalOnMissingBean(CompositeStepDecorator.class)
    public CompositeStepDecorator compositeStepDecorator(ObjectProvider<List<StepDecorator>> decoratorsProvider) {
        List<StepDecorator> decorators = decoratorsProvider.getIfAvailable(List::of);
        return new CompositeStepDecorator(decorators);
    }

    /**
     * 内部静态类：负责在 Spring 容器销毁时对注册的隔离线程池进行优雅的 shutdown 和资源释放。
     */
    static final class GracefulShutdown {
        private final List<ExecutorService> executors;

        private GracefulShutdown(ExecutorService... executors) {
            this.executors = List.of(executors);
        }

        public void shutdown() {
            // 首先触发所有执行器的 shutdown
            for (ExecutorService executor : executors) {
                executor.shutdown();
            }
            // 依次等待每个执行器处理完队列中的剩余任务，最多等待 30 秒
            for (ExecutorService executor : executors) {
                awaitTermination(executor);
            }
        }

        private void awaitTermination(ExecutorService executor) {
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    // 超时后强行终止
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
