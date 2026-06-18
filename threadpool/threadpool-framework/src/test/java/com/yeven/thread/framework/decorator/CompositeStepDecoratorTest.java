package com.yeven.thread.framework.decorator;

import com.yeven.thread.framework.pipeline.AsyncStep;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompositeStepDecorator 单元测试类。
 * 验证多个步骤装饰器的聚合与嵌套拦截调用链顺序是否符合预期。
 * 遵循 AAA (Arrange, Act, Assert) 模式。
 */
class CompositeStepDecoratorTest {

    @Test
    void testCompositeDecorationOrder() throws Exception {
        // Arrange (准备数据/对象)
        List<String> trace = Collections.synchronizedList(new ArrayList<>());

        // 创建两个装饰器，分别在步骤执行前后向 trace 列表里追加对应的开始与结束标志
        StepDecorator decorator1 = (stepName, step) -> ctx -> {
            trace.add("d1_start");
            return step.apply(ctx).thenApply(res -> {
                trace.add("d1_end");
                return res;
            });
        };

        StepDecorator decorator2 = (stepName, step) -> ctx -> {
            trace.add("d2_start");
            return step.apply(ctx).thenApply(res -> {
                trace.add("d2_end");
                return res;
            });
        };

        // 初始的基础步骤
        AsyncStep<String> baseStep = ctx -> {
            trace.add("base_step");
            return CompletableFuture.completedFuture(ctx + "_processed");
        };

        // 使用组合装饰器包装，列表中 decorator1 在前，decorator2 在后
        CompositeStepDecorator compositeDecorator = new CompositeStepDecorator(List.of(
                decorator1,
                decorator2
        ));

        // Act (执行被测方法)
        AsyncStep<String> decoratedStep = compositeDecorator.decorate("testStep", baseStep);
        CompletableFuture<String> future = decoratedStep.apply("initial");
        String result = future.get();

        // Assert (断言预期结果)
        // 1. 验证最终的上下文返回值是否正确被传递和处理
        assertEquals("initial_processed", result, "步骤执行结果应正确");

        // 2. 验证执行追踪。由于组合装饰器从后往前反向嵌套：
        //    即 decorator2 包装 baseStep 得到 nested2；然后 decorator1 包装 nested2 得到最终的 decoratedStep。
        //    因此：
        //    - decorator1 作为最外层，最先拦截并写入 "d1_start"
        //    - decorator2 作为次外层，写入 "d2_start"
        //    - baseStep 最终执行，写入 "base_step"
        //    - decorator2 的 thenApply 先触发，写入 "d2_end"
        //    - decorator1 的 thenApply 最后触发，写入 "d1_end"
        List<String> expectedTrace = List.of(
                "d1_start",
                "d2_start",
                "base_step",
                "d2_end",
                "d1_end"
        );
        assertEquals(expectedTrace, trace, "装饰器的嵌套执行顺序与预期的洋葱模型不匹配");
    }

    @Test
    void testEmptyCompositeDecorator() throws Exception {
        // Arrange
        // 当传入空装饰器列表时，步骤装饰链应退化为 No-op 直接透传
        CompositeStepDecorator emptyComposite = new CompositeStepDecorator(List.of());
        AsyncStep<String> baseStep = ctx -> CompletableFuture.completedFuture(ctx + "_done");

        // Act
        AsyncStep<String> decoratedStep = emptyComposite.decorate("testStep", baseStep);
        String result = decoratedStep.apply("hello").get();

        // Assert
        assertEquals("hello_done", result, "空装饰器列表时，应原样返回执行结果");
    }
}
