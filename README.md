# ThreadPool Async Orchestrator

一个基于 Spring Boot 的轻量级异步任务流编排框架。它通过有向无环图 (DAG) 和线性管道 (Pipeline) 的抽象，将复杂的线程管理转变为直观的任务依赖描述。

## 核心特性

- **任务编排**：支持线性管道 (`AsyncPipeline`) 和 复杂有向无环图 (`AsyncGraph`)。
- **模式感知调度**：内置 `IO` 和 `CPU` 执行模式，自动适配不同的线程池策略。
- **无栈溢出风险**：`AsyncGraph` 采用迭代式拓扑排序执行，支持超深层级的依赖图。
- **非侵入式增强**：通过装饰器模式 (`StepDecorator`) 轻松实现日志、监控、重试等横切逻辑。
- **资源安全**：默认使用有界队列，且完美集成 Spring 容器生命周期管理（显式安全关闭）。
- **零成本接入**：标准的 Spring Boot Starter，引入即用。

---

## 快速上手

本章节将教你如何从零开始使用本项目编排异步业务逻辑。

### 1. 准备工作
在你的 Spring Boot 项目中引入 starter：
```xml
<dependency>
    <groupId>com.yeven</groupId>
    <artifactId>threadpool-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. 定义业务上下文 (Context)
异步流程中的所有步骤共享一个上下文对象。
```java
public class QuoteContext {
    private String productId;
    private double basePrice;
    private double finalPrice;
    // Getters and Setters...
}
```

### 3. 使用 `AsyncPipeline` (线性流程)
适用于简单的串行步骤。
```java
@Service
public class SimpleService {
    @Autowired
    private AsyncStepFactory stepFactory;

    public void runPipeline() {
        AsyncPipeline<QuoteContext> pipeline = new AsyncPipelineBuilder<QuoteContext>()
            .addStep(stepFactory.create("fetchPrice", ExecutionMode.IO, ctx -> {
                ctx.setBasePrice(100.0);
                return ctx;
            }))
            .addStep(stepFactory.create("calculateTax", ExecutionMode.CPU, ctx -> {
                ctx.setFinalPrice(ctx.getBasePrice() * 1.08);
                return ctx;
            }))
            .build();

        pipeline.execute(new QuoteContext()).join();
    }
}
```

### 4. 使用 `AsyncGraph` (复杂 DAG 流程)
适用于存在并行分支和节点合并的场景。
```java
@Service
public class QuoteFlowFactory {
    @Autowired
    private AsyncStepFactory stepFactory;

    public AsyncGraph<QuoteContext> createQuoteGraph() {
        return new AsyncGraphBuilder<QuoteContext>(stepFactory)
            // 根节点：获取基础数据
            .addRootStep("init", ExecutionMode.IO, ctx -> {
                System.out.println("Initializing data...");
                return ctx;
            })
            // 并行分支 1：获取会员折扣
            .addStep("memberDiscount", "init", ExecutionMode.IO, ctx -> {
                // 模拟查询远程服务
                return ctx;
            })
            // 并行分支 2：获取商品促销
            .addStep("promoDiscount", "init", ExecutionMode.IO, ctx -> {
                // 模拟查询数据库
                return ctx;
            })
            // 合并节点：汇总所有折扣
            .addJoinStep("finalPrice", 
                List.of("memberDiscount", "promoDiscount"), 
                ExecutionMode.CPU,
                results -> {
                    // results 是按依赖顺序排列的输出列表
                    QuoteContext merged = results.get(0); 
                    // 执行合并逻辑...
                    return merged;
                })
            .build();
    }
}
```

### 5. 自定义增强 (Decorator)
你可以定义自己的装饰器来拦截每个步骤的执行。
```java
@Component
public class MyMetricsDecorator implements StepDecorator {
    @Override
    public <T> AsyncStep<T> decorate(String stepName, AsyncStep<T> step) {
        return context -> {
            long start = System.currentTimeMillis();
            return step.apply(context).whenComplete((res, err) -> {
                System.out.println("Step [" + stepName + "] cost: " + (System.currentTimeMillis() - start) + "ms");
            });
        };
    }
}
```

---

## ⚙️ 配置说明

在 `application.yml` 中根据机器性能调整线程池参数：

```yaml
yeven:
  threadpool:
    io:
      core-size: 16
      max-size: 64
      queue-capacity: 1000
    cpu:
      core-size: 8
      max-size: 16
      queue-capacity: 500
```

---

## 🛠️ 架构设计

1.  **拓扑执行引擎**：`AsyncGraph` 采用迭代式拓扑排序执行。在构建图时通过 DFS 校验循环依赖并生成执行序列，彻底解决递归调用在大规模图场景下的 `StackOverflow` 风险。
2.  **线程模型**：
    *   `IO 模式`：适用于 RPC、DB 等阻塞操作，拥有更大的线程池和队列。
    *   `CPU 模式`：适用于计算密集型逻辑，线程数与核心数挂钩。
    *   `DIRECT 模式`：在当前线程执行，用于极轻量级的转换操作。
3.  **并发与异常安全**：
    *   `AsyncGraphBuilder` 全方法同步，支持并发环境下的动态图构建。
    *   所有 `AsyncStep` 的同步异常均会被包装为失败的 `CompletableFuture`，确保流程永不挂起。

---

## 📜 最佳实践建议

1.  **保持 Context 简洁**：尽量不要在上下文中存储大对象，避免在长流程中造成内存压力。
2.  **异常处理**：在 `AsyncStep` 内部尽量捕获业务异常并封装进上下文，只有致命异常才建议向上抛出。
3.  **合理分池**：严禁在 `CPU` 模式的步骤中调用阻塞 IO 接口，这会迅速耗尽计算资源。

---

## 📄 License

Apache License 2.0
