package com.yeven.thread.framework.factory;

import com.yeven.thread.framework.dispatcher.ExecutionDispatcher;
import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.executor.NodeCompletion;
import com.yeven.thread.framework.dispatcher.NodeDispatcher;
import com.yeven.thread.framework.dispatcher.FutureBackedNodeDispatcher;
import com.yeven.thread.framework.definition.StepDefinition;
import com.yeven.thread.framework.pipeline.AsyncStep;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 异步步骤工厂类，负责根据 {@link StepDefinition} 创建可执行的 {@link AsyncStep} 实例。
 *
 * <p><b>设计必要性与核心价值：</b></p>
 * <ul>
 *   <li><b>业务与执行策略的解耦：</b> 实现了步骤的业务处理逻辑（在 {@link StepDefinition} 中定义）与底层的物理执行策略（CPU、IO、DIRECT）的完全隔离，允许在不更改业务逻辑的前提下灵活调整运行模式。</li>
 *   <li><b>零内存分配优化桥梁：</b> 作为 DAG 调度引擎的核心辅助类，它自适应探测底层的分发器是否实现了 {@link NodeDispatcher} 接口。如果支持，则优先使用零分配（Zero-Allocation）的任务分发方式，消除在海量拓扑节点并发执行时为每个节点单独创建 {@code CompletableFuture} 的 GC 开销。</li>
 *   <li><b>兜底退化保障：</b> 当底层分发器不支持原生节点级分发时，自动启用内部的 {@link FutureBackedNodeDispatcher} 代理进行降级处理，使用 Future 驱动回调来确保兼容性和健壮性。</li>
 * </ul>
 *
 * <p>该类是线程安全的，适合在全局流水线引擎或 DAG 编排中被单例共享。</p>
 */
public class AsyncStepFactory {

    private final ExecutionDispatcher dispatcher;
    private final NodeDispatcher nodeDispatcher;

    /**
     * 构造异步步骤工厂。
     *
     * @param dispatcher 任务分发器
     */
    public AsyncStepFactory(ExecutionDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.nodeDispatcher = dispatcher instanceof NodeDispatcher directNodeDispatcher
                ? directNodeDispatcher
                : new FutureBackedNodeDispatcher(dispatcher);
    }

    /**
     * 创建一个异步步骤。
     *
     * @param definition 步骤定义
     * @param <C> 上下文类型
     * @return 会按照其执行模式将任务分发至对应线程池运行的异步步骤 {@link AsyncStep}
     */
    public <C> AsyncStep<C> create(StepDefinition<C> definition) {
        return context -> dispatcher.dispatch(
                definition.getMode(),
                () -> definition.getHandler().apply(context)
        );
    }

    /**
     * 根据指定的执行模式异步分发执行一个任务。
     *
     * <p>供 DAG 类型的运行期使用，这些运行期不直接映射为单个 {@link AsyncStep}，
     * 但仍需要具备模式感知的线程池路由分发能力。</p>
     *
     * @param mode 执行模式
     * @param supplier 具体的任务逻辑提供者
     * @param <T> 返回值类型
     * @return 包含任务执行结果 of 的 {@link CompletableFuture}
     */
    public <T> CompletableFuture<T> dispatch(ExecutionMode mode, Supplier<T> supplier) {
        return dispatcher.dispatch(mode, supplier);
    }

    /**
     * 分发图节点任务。当底层分发器支持 {@link NodeDispatcher} 时，
     * 此分发操作不会在节点级别分配额外的 Future 对象，以降低内存垃圾产生。
     *
     * @param mode 执行模式
     * @param task 节点的 Runnable 任务
     * @param completion 节点执行完毕后的状态回调接口
     */
    public void dispatchNode(ExecutionMode mode, Runnable task, NodeCompletion completion) {
        // [性能优化路由]：直接委托给经构造函数自适应确定的 nodeDispatcher 实例。
        // 如果底层分发器本身原生地实现了 NodeDispatcher（如 DefaultExecutionDispatcher），
        // 这一步分发将完全避免在节点级别分配任何额外的 Future 包装器，显著降低高频并发时的 GC 压力。
        nodeDispatcher.dispatchNode(mode, task, completion);
    }
}
