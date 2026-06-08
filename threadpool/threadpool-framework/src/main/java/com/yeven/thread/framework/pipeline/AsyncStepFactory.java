package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.ExecutionDispatcher;
import com.yeven.thread.framework.executor.ExecutionMode;
import com.yeven.thread.framework.executor.NodeCompletion;
import com.yeven.thread.framework.executor.NodeDispatcher;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Builds executable {@link AsyncStep} instances from {@link StepDefinition}.
 *
 * <p>The generated step delegates scheduling to {@link ExecutionDispatcher}, so each step can run on
 * IO/CPU/direct mode without changing business logic code.</p>
 */
public class AsyncStepFactory {

    private final ExecutionDispatcher dispatcher;
    private final NodeDispatcher nodeDispatcher;

    public AsyncStepFactory(ExecutionDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.nodeDispatcher = dispatcher instanceof NodeDispatcher directNodeDispatcher
                ? directNodeDispatcher
                : new FutureBackedNodeDispatcher(dispatcher);
    }

    /**
     * Creates one asynchronous step.
     *
     * @param definition step definition
     * @param <C> context type
     * @return async step that dispatches the handler according to execution mode
     */
    public <C> AsyncStep<C> create(StepDefinition<C> definition) {
        return context -> dispatcher.dispatch(
                definition.getMode(),
                () -> definition.getHandler().apply(context)
        );
    }

    /**
     * Dispatches one task with explicit execution mode.
     *
     * <p>This is used by graph-style runtimes that do not map directly to {@link AsyncStep}
     * but still need mode-aware scheduling.</p>
     *
     * @param mode execution mode
     * @param supplier task supplier
     * @param <T> result type
     * @return dispatched task future
     */
    public <T> CompletableFuture<T> dispatch(ExecutionMode mode, Supplier<T> supplier) {
        return dispatcher.dispatch(mode, supplier);
    }

    /**
     * Dispatches one graph node without allocating a node-level future when the
     * underlying dispatcher supports {@link NodeDispatcher}.
     *
     * @param mode execution mode
     * @param task node task
     * @param completion completion callback
     */
    public void dispatchNode(ExecutionMode mode, Runnable task, NodeCompletion completion) {
        nodeDispatcher.dispatchNode(mode, task, completion);
    }

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
