package com.yeven.thread.framework.dispatcher;

import com.yeven.thread.framework.constant.ExecutionMode;
import com.yeven.thread.framework.executor.NodeCompletion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FutureBackedNodeDispatcher 单元测试类。
 * 验证不支持直接节点调度的分发器的退化兼容及异常解壳机制。
 * 使用 Mockito 来 Mock 依赖接口。
 * 遵循 AAA (Arrange, Act, Assert) 模式。
 */
@ExtendWith(MockitoExtension.class)
class FutureBackedNodeDispatcherTest {

    @Mock
    private ExecutionDispatcher dispatcher;

    @Mock
    private NodeCompletion completion;

    private FutureBackedNodeDispatcher nodeDispatcher;

    @BeforeEach
    void setUp() {
        nodeDispatcher = new FutureBackedNodeDispatcher(dispatcher);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDispatchSuccess() {
        // Arrange (准备数据/对象)
        AtomicBoolean taskRun = new AtomicBoolean(false);
        Runnable task = () -> taskRun.set(true);

        // 插桩模拟 dispatcher.dispatch 的行为：执行传入的 supplier，并返回成功的 CompletableFuture
        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            Object result = supplier.get();
            return CompletableFuture.completedFuture(result);
        }).when(dispatcher).dispatch(any(ExecutionMode.class), any(Supplier.class));

        // Act (执行被测方法)
        nodeDispatcher.dispatchNode(ExecutionMode.CPU, task, completion);

        // Assert (断言预期结果)
        // 1. 验证任务是否确实被执行过
        assertTrue(taskRun.get(), "任务应该已被调度运行");
        // 2. 验证回调 completion.complete(null) 是否被且仅被触发了一次
        verify(completion, times(1)).complete(null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDispatchWithPlainException() {
        // Arrange
        RuntimeException plainError = new RuntimeException("plain connection timeout");
        Runnable task = () -> {
            throw plainError;
        };

        // 插桩模拟：当执行 supplier 任务时如果抛出异常，返回以该异常结束的失败 CompletableFuture
        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            try {
                Object result = supplier.get();
                return CompletableFuture.completedFuture(result);
            } catch (Throwable t) {
                CompletableFuture<?> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(t);
                return failedFuture;
            }
        }).when(dispatcher).dispatch(any(ExecutionMode.class), any(Supplier.class));

        // Act
        nodeDispatcher.dispatchNode(ExecutionMode.IO, task, completion);

        // Assert
        // 验证任务抛出普通异常时，回调能够完好地将该原样异常汇报出去，无额外包装
        verify(completion, times(1)).complete(plainError);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDispatchWithCompletionException() {
        // Arrange
        NullPointerException cause = new NullPointerException("NPE on target field");
        // 构造 CompletionException 实例以模拟 CompletableFuture 链路的自动包装
        CompletionException completionException = new CompletionException(cause);

        Runnable task = () -> {};

        // 模拟返回包装了 NPE 异常的失败 Future 结果，此时不模拟具体任务执行过程，专注测试解包机制
        CompletableFuture<?> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(completionException);
        doReturn(failedFuture).when(dispatcher).dispatch(any(ExecutionMode.class), any(Supplier.class));

        // Act
        nodeDispatcher.dispatchNode(ExecutionMode.IO, task, completion);

        // Assert
        // 验证 FutureBackedNodeDispatcher.unwrapCompletion 能将 CompletionException 剥除，
        // 确保回调接收的是真实根源异常 (NullPointerException)
        verify(completion, times(1)).complete(cause);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDispatchWithCompletionExceptionEmptyCause() {
        // Arrange
        // 构造一个 Cause 为空的 CompletionException
        CompletionException emptyCauseException = new CompletionException(null);

        Runnable task = () -> {};

        CompletableFuture<?> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(emptyCauseException);
        doReturn(failedFuture).when(dispatcher).dispatch(any(ExecutionMode.class), any(Supplier.class));

        // Act
        nodeDispatcher.dispatchNode(ExecutionMode.CPU, task, completion);

        // Assert
        // 如果 Cause 为空，由于无法进一步解壳，则预期应回退直接传递该异常本身
        verify(completion, times(1)).complete(emptyCauseException);
    }
}
