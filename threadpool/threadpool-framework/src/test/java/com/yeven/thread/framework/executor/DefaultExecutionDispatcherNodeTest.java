package com.yeven.thread.framework.executor;

import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.dispatcher.DefaultExecutionDispatcher;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultExecutionDispatcherNodeTest {

    @Test
    void shouldRunDirectNodeInCallerThread() {
        DefaultExecutionDispatcher dispatcher = new DefaultExecutionDispatcher(new ExecutorRegistry(Map.of()));
        String callerThread = Thread.currentThread().getName();
        AtomicReference<String> executionThread = new AtomicReference<>();
        AtomicReference<Throwable> completionError = new AtomicReference<>();

        dispatcher.dispatchNode(
                ExecutionMode.DIRECT,
                () -> executionThread.set(Thread.currentThread().getName()),
                completionError::set
        );

        assertEquals(callerThread, executionThread.get());
        assertEquals(null, completionError.get());
    }

    @Test
    void shouldReportExecutorRejectionThroughNodeCompletion() {
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("full");
        };
        DefaultExecutionDispatcher dispatcher = new DefaultExecutionDispatcher(new ExecutorRegistry(Map.of(
                ExecutionMode.IO, rejectingExecutor
        )));
        AtomicReference<Throwable> completionError = new AtomicReference<>();

        dispatcher.dispatchNode(ExecutionMode.IO, () -> {
        }, completionError::set);

        assertNotNull(completionError.get());
        assertTrue(completionError.get() instanceof RejectedExecutionException);
    }

    @Test
    void shouldReportTaskFailureThroughNodeCompletion() {
        Executor directExecutor = Runnable::run;
        DefaultExecutionDispatcher dispatcher = new DefaultExecutionDispatcher(new ExecutorRegistry(Map.of(
                ExecutionMode.CPU, directExecutor
        )));
        AtomicReference<Throwable> completionError = new AtomicReference<>();

        dispatcher.dispatchNode(ExecutionMode.CPU, () -> {
            throw new IllegalStateException("boom");
        }, completionError::set);

        assertNotNull(completionError.get());
        assertTrue(completionError.get() instanceof IllegalStateException);
    }
}
