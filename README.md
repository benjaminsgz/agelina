# ThreadPool Async Orchestrator

一个面向 Spring Boot 的异步编排框架，当前只保留两套 API：

1. `AsyncPipeline`：线性流程（顺序执行）
2. `SlotAsyncGraph`：高性能 DAG（`int slot` + bitset + 启动期契约校验）

目标是让线程路由、依赖拓扑、过载行为可控，同时在 Hot Path 保持低开销。

## 模块结构

- `threadpool-framework`：核心执行与编排模型
- `threadpool-spring-boot-autoconfigure`：自动配置与 Bean 扩展点
- `threadpool-spring-boot-starter`：starter 入口
- `threadpool-auth-demo`：Pipeline 示例
- `threadpool-dag-demo`：Slot DAG 示例

## 核心概念

### ExecutionMode

- `IO`：阻塞 I/O（DB/RPC）
- `CPU`：计算密集型步骤
- `DIRECT`：调用线程直跑（无额外调度）

### AsyncStepFactory

`AsyncStepFactory` 把 `StepDefinition` 转换成可执行 `AsyncStep`，并按 `ExecutionMode` 路由到线程池。

```java
AsyncStep<Ctx> step = stepFactory.create(new StepDefinition<>(
    "loadUser",
    ExecutionMode.IO,
    ctx -> ctx.withUser(repo.find(ctx.userId()))
));
```

### AsyncPipeline

- 场景：纯顺序流程
- 行为：前一步失败，后续跳过

### SlotAsyncGraph (高性能 DAG)

`SlotAsyncGraph` 是本框架的核心组件，专门为复杂的异步依赖拓扑设计。它通过“启动期编译/校验 + 运行时槽位访问”的模式，在提供符号化易用性的同时，消除了运行时对 Map 或 ConcurrentHashMap 的依赖。

#### 1) 核心原理
- **槽位隔离 (Slotting)**：将整个执行链需要共享的中间结果抽象为 `slots` 数组。
- **符号表 (SymbolTable)**：在 Builder 阶段，框架会自动将字符串名称（如 "userInfo"）映射为数组下标（如 `index 2`）。
- **无锁可见性**：通过 `AtomicLongArray` 管理位图（ReadyBits）。当某个槽位被写入后，原子性更新位图，确保后续节点在不同线程也能看到最新的槽位值。
- **拓扑校验**：在 `build()` 时进行深度优先搜索（DFS），检测循环依赖，并验证“数据消费路径”是否符合“逻辑依赖路径”。

#### 2) 核心 API 详解

| 方法 | 说明 | 适用场景 |
| :--- | :--- | :--- |
| `addSlotStep` | 定义一个产生单输出的节点 | 大多数业务步骤，如 `loadUser` -> `User` |
| `addPatchStep` | 定义一个产生多输出（Patch）的节点 | 一个调用返回多个字段，如 `RPC` 返回 `ProductInfo` 包含名称、价格、库存 |
| `addTerminalStep` | 定义图的终点，负责汇聚结果并返回 | 整个 DAG 的出口，将所有槽位值拼装回 Context |
| `ExecutionMode` | 指定步骤运行的线程池 | `IO` (阻塞), `CPU` (计算), `DIRECT` (轻量) |

#### 3) 启动期契约校验 (Fail-Fast)
为了避免运行时出现隐晦的竞态或空指针，`build()` 阶段会强制执行以下校验：
1. **单写者约束 (Single Writer)**：同一个 Slot（符号名）在图中只能有一个生产者。
2. **依赖闭包约束 (Dependency Closure)**：如果节点 B 读取了 Slot `S`，而 `S` 是由节点 A 产生的，那么节点 B 必须在 `dependencies` 中直接或间接地包含节点 A。
3. **循环依赖检测**：严禁出现 A -> B -> A 的死循环。
4. **终点节点校验**：必须通过 `addTerminalStep` 指定唯一的出口，且该出口必须依赖所有必要的上游节点。

#### 4) 注意事项与最佳实践 (Precautions)

##### 🚀 性能建议
- **优先使用符号名**：虽然支持 `int` 索引，但推荐在 Builder 阶段使用 `String` 符号名。框架在 `build()` 后会将其固化为 `int`，不会影响执行性能。
- **控制 DIRECT 模式**：`ExecutionMode.DIRECT` 会在父节点执行完后的当前线程（通常是 IO/CPU 池线程）直接运行。仅适用于简单的字段提取、对象转换等极轻量操作（< 1ms）。

##### 🛡️ 线程安全与可见性
- **Context 的只读性**：在 `SlotAsyncGraph` 中，建议将初始 Context 视为**只读**的快照。所有节点间的中间产物应通过 **Slot** 传递，而不是通过修改 Context 属性。
- **Slot 对象的安全性**：存入 Slot 的对象应尽量是不可变的（Immutable）。如果存入可变对象并被多个并行分支同时修改，框架无法保护其内部的线程安全。

##### 调试技巧
- **符号表描述**：如果运行时抛出 `IllegalStateException: Slot not ready`，可以通过 `symbolTable.describe(slotId)` 获取可读的符号名。
- **异常传播**：DAG 中任意节点抛出未捕获异常，都会导致整个图的 `CompletableFuture` 以失败告终。建议在关键节点内部进行适当的 `try-catch` 降级，返回默认值 Slot。

### @AsyncStepBean（Spring 自动注册）

标注方法会自动注册两类 Bean：

- `stepDefinition.<stepName>`
- `asyncStep.<stepName>`

方法约束：

- 仅 1 个参数（context）
- 返回值不能是 `void`
- 返回类型必须与参数类型兼容

## 快速开始

## 1) 引入 starter

```xml
<dependency>
  <groupId>com.yeven</groupId>
  <artifactId>threadpool-spring-boot-starter</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

## 2) 配置线程池

`application.yml`

```yaml
threadpool:
  async:
    io:
      core-size: 16
      max-size: 32
      queue-capacity: 2000
      keep-alive-seconds: 60
      rejection-policy: CALLER_RUNS
    cpu:
      core-size: 8
      max-size: 8
      queue-capacity: 500
      keep-alive-seconds: 60
      rejection-policy: ABORT
```

## 3) Pipeline 示例

```java
@Service
public class LoginFlow {

    private final AsyncStepFactory stepFactory;

    public LoginFlow(AsyncStepFactory stepFactory) {
        this.stepFactory = stepFactory;
    }

    public AsyncPipeline<LoginContext> build() {
        return new AsyncPipelineBuilder<LoginContext>()
                .addStep(stepFactory.create(new StepDefinition<>(
                        "loadUser",
                        ExecutionMode.IO,
                        ctx -> ctx.withUser(loadFromDb(ctx.username()))
                )))
                .addStep(stepFactory.create(new StepDefinition<>(
                        "verifyPassword",
                        ExecutionMode.CPU,
                        this::verify
                )))
                .build();
    }
}
```

## 4) SlotAsyncGraph 示例（DAG）

```java
SlotAsyncGraph<QuoteContext> graph = new SlotAsyncGraphBuilder<QuoteContext>(stepFactory)
        .addSlotStep("validateRequest", List.of(), ExecutionMode.DIRECT, List.of(), "validatedContext",
                view -> validate(view.context()))
        .addSlotStep("loadInventory", List.of("validateRequest"), ExecutionMode.IO,
                List.of("validatedContext"), "availableStock",
                view -> inventoryGateway.getAvailableStock(
                        view.slotAs("validatedContext", QuoteContext.class).getSku()))
        .addSlotStep("loadMemberProfile", List.of("validateRequest"), ExecutionMode.IO,
                List.of("validatedContext"), "memberLevel",
                view -> memberProfileGateway.getByUserId(
                        view.slotAs("validatedContext", QuoteContext.class).getUserId()).getLevel())
        .addSymbolicPatchStep("loadProductAndBaseAmount", List.of("validateRequest"), ExecutionMode.IO,
                List.of("validatedContext"),
                List.of("productName", "unitPrice", "baseAmount"),
                view -> {
                    QuoteContext c = view.slotAs("validatedContext", QuoteContext.class);
                    ProductInfo p = productCatalogGateway.getBySku(c.getSku());
                    BigDecimal baseAmount = p.getUnitPrice().multiply(BigDecimal.valueOf(c.getQuantity()));
                    return SymbolicSlotPatch.from(
                            new String[]{"productName", "unitPrice", "baseAmount"},
                            new Object[]{p.getProductName(), p.getUnitPrice(), baseAmount}
                    );
                })
        .addSlotStep("calculateMemberDiscount", List.of("loadProductAndBaseAmount", "loadMemberProfile"),
                ExecutionMode.CPU,
                List.of("baseAmount", "memberLevel"),
                "memberDiscount",
                view -> calcMemberDiscount(view))
        .addSlotStep("calculateCouponDiscount", List.of("loadProductAndBaseAmount", "validateRequest"),
                ExecutionMode.CPU,
                List.of("baseAmount", "validatedContext"),
                "couponDiscount",
                view -> calcCouponDiscount(view))
        .addTerminalStep("buildQuote",
                List.of(
                        "validateRequest",
                        "loadInventory",
                        "loadMemberProfile",
                        "loadProductAndBaseAmount",
                        "calculateMemberDiscount",
                        "calculateCouponDiscount"
                ),
                ExecutionMode.DIRECT,
                List.of(
                        "validatedContext", "availableStock", "memberLevel",
                        "productName", "unitPrice", "baseAmount",
                        "memberDiscount", "couponDiscount"
                ),
                this::buildQuote)
        .build();
```

## 5) 注解式 Step 自动注册

```java
@Component
public class UserSteps {

    @AsyncStepBean(name = "loadUser", mode = ExecutionMode.IO)
    public LoginContext loadUser(LoginContext ctx) {
        return ctx.withUser(repo.findByUsername(ctx.getUsername()));
    }
}
```

可通过 Bean 名称引用：

- `asyncStep.loadUser`
- `stepDefinition.loadUser`

## 性能边界与建议

- `IO` 与 `CPU` 线程池分离，避免互相污染。
- `DIRECT` 仅用于极轻步骤。
- Hot Path 优先 `SlotAsyncGraph`：
  - 业务代码优先用符号名槽位 API（可读性更好）
  - 运行时仍是 `int slot` 索引访问（性能路径不变）
  - 尽量单槽输出，`SlotPatch` 只用于确实需要多槽写入的步骤

## 开发与验证

在仓库 `ThreadPool/threadpool` 目录下：

```bash
mvn -q test
mvn -q -DskipTests compile
```

## Demo

- `threadpool-auth-demo`：Pipeline 登录流程
- `threadpool-dag-demo`：Slot DAG 报价流程

## 兼容性

- JDK 17+
- Spring Boot 3.3.x

## License

Apache License 2.0
