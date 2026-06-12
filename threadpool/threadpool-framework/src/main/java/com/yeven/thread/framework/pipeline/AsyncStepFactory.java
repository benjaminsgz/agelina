package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.ExecutionDispatcher;
import com.yeven.thread.framework.executor.ExecutionMode;
import com.yeven.thread.framework.executor.NodeCompletion;
import com.yeven.thread.framework.executor.NodeDispatcher;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * 异步步骤工厂类，负责根据 {@link StepDefinition} 创建可执行的 {@link AsyncStep} 实例。
 *
 * <p>所创建的异步步骤将任务调度委托给 {@link ExecutionDispatcher}，使得每个步骤可以在不修改业务逻辑代码的情况下，
 * 自由且灵活地运行在 IO、CPU 或直接（DIRECT）执行模式下。</p>
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
     * @return 包含任务执行结果的 {@link CompletableFuture}
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
        nodeDispatcher.dispatchNode(mode, task, completion);
    }

    /**
     * 当底层的 {@link ExecutionDispatcher} 未实现 {@link NodeDispatcher} 时，
     * 提供的降级包装分发器，其在内部通过 Future 来驱动回调。
     */
    private static final class FutureBackedNodeDispatcher implements NodeDispatcher {

        private final ExecutionDispatcher dispatcher;

        private FutureBackedNodeDispatcher(ExecutionDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public void dispatchNode(ExecutionMode mode, Runnable task, NodeCompletion completion) {
            dispatcher.dispatch(mode, () -> {
                task.run();
                return null;
            }).whenComplete((unused, error) -> completion.complete(unwrapCompletion(error)));
        }

        private static Throwable unwrapCompletion(Throwable error) {
            if (error instanceof CompletionException completionException
                    && completionException.getCause() != null) {
                return completionException.getCause();
            }
            return error;
        }
    }
}
