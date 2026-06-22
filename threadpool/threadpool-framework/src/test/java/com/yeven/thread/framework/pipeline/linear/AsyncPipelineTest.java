package com.yeven.thread.framework.pipeline.linear;

import com.yeven.thread.framework.pipeline.core.AsyncStep;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AsyncPipeline 单元测试类。
 * 验证顺序异步管道的链式执行逻辑与 Fail-Fast 熔断短路机制。
 * 遵循 AAA (Arrange, Act, Assert) 模式。
 */
class AsyncPipelineTest {

    // 共享上下文测试模型
    private static class TestContext {
        final List<String> log = Collections.synchronizedList(new ArrayList<>());
        boolean executedStep3 = false;
    }

    @Test
    void testSequentialPipelineExecution() throws Exception {
        // Arrange (准备数据/对象)
        TestContext context = new TestContext();

        AsyncStep<TestContext> step1 = ctx -> {
            ctx.log.add("step1");
            return CompletableFuture.completedFuture(ctx);
        };

        AsyncStep<TestContext> step2 = ctx -> {
            ctx.log.add("step2");
            return CompletableFuture.completedFuture(ctx);
        };

        AsyncStep<TestContext> step3 = ctx -> {
            ctx.executedStep3 = true;
            ctx.log.add("step3");
            return CompletableFuture.completedFuture(ctx);
        };

        // 构造串行管道并传入步骤列表
        AsyncPipeline<TestContext> pipeline = new AsyncPipeline<>(List.of(step1, step2, step3));

        // Act (执行被测方法)
        CompletableFuture<TestContext> future = pipeline.execute(context);
        TestContext result = future.get();

        // Assert (断言预期结果)
        // 验证各步骤在同一个上下文中按顺序正常完成流转
        assertNotNull(result);
        assertEquals(List.of("step1", "step2", "step3"), result.log, "步骤应按顺序执行且记录日志");
        assertTrue(result.executedStep3, "步骤 3 应该已被执行");
    }

    @Test
    void testPipelineFailFastAndShortCircuit() {
        // Arrange
        TestContext context = new TestContext();
        RuntimeException expectedException = new RuntimeException("Simulated step2 failure");

        AsyncStep<TestContext> step1 = ctx -> {
            ctx.log.add("step1");
            return CompletableFuture.completedFuture(ctx);
        };

        // 步骤 2 抛出异常，触发熔断
        AsyncStep<TestContext> step2 = ctx -> {
            CompletableFuture<TestContext> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(expectedException);
            return failedFuture;
        };

        AsyncStep<TestContext> step3 = ctx -> {
            // 如果 Fail-Fast 成功熔断，此步骤不应执行
            ctx.executedStep3 = true;
            ctx.log.add("step3");
            return CompletableFuture.completedFuture(ctx);
        };

        // 构造包含故障步骤的串行管道
        AsyncPipeline<TestContext> pipeline = new AsyncPipeline<>(List.of(step1, step2, step3));

        // Act (执行被测方法)
        CompletableFuture<TestContext> future = pipeline.execute(context);

        // Assert (断言预期结果)
        // 1. 验证 Future 异常结束
        ExecutionException executionException = assertThrows(ExecutionException.class, future::get);
        assertSame(expectedException, executionException.getCause(), "熔断抛出的底层异常应与步骤 2 抛出的相同");

        // 2. 验证熔断短路行为：步骤 1 执行，步骤 3 绝不执行
        assertEquals(List.of("step1"), context.log, "在发生异常后，剩余步骤应被跳过（仅执行步骤 1）");
        assertFalse(context.executedStep3, "发生熔断时，后续步骤 3 不应被执行");
    }
}
