# Spring Boot 集成与自动装配机制

Agelina 提供了与 Spring Boot 容器无缝的集成能力。通过声明式的注解，开发者可以极其简单地声明和自动注册异步执行步骤，消除了大量手动配置的样板代码，并允许将步骤作为标准的 Spring Bean 直接注入。

## 自动装配原理

当项目引入 `threadpool-spring-boot-starter` 依赖后，Spring 容器启动时会自动注册以下两个核心组件：
1. `ThreadPoolAutoConfiguration`：负责构建统一执行调度器 `ExecutionDispatcher`、初始化默认隔离线程池（`IO` 线程池与 `CPU` 线程池）并将其注册至线程注册表中。
2. `AsyncStepBeanPostProcessor`：负责扫描 Spring 容器中所有的 Component 类型的 Bean，提取出其中标注了 `@AsyncStepBean` 注解的方法，进行步骤的动态包裹与 Bean 注册。

---

## 使用 @AsyncStepBean 声明步骤

要想将一个普通的业务方法暴露为可复用的异步步骤，只需在其方法上添加 `@AsyncStepBean` 注解即可：

```java
@Component
public class InventorySteps {

    private final InventoryRepository inventoryRepository;

    public InventorySteps(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @AsyncStepBean(name = "checkStock", mode = ExecutionMode.IO)
    public OrderContext checkStock(OrderContext context) {
        int stock = inventoryRepository.getStock(context.getSku());
        if (stock < context.getQuantity()) {
            throw new IllegalStateException("库存不足，当前商品 SKU: " + context.getSku());
        }
        context.setAvailable(true);
        return context;
    }
}
```

### 方法签名规则约束

在应用启动时，`AsyncStepBeanPostProcessor` 会对标注了该注解的方法进行严格的签名校验。如果方法签名违反了以下任何一条约束，Spring 容器将立即报错拒绝启动：

1. **必须有且仅有一个入参**：方法必须接收唯一的参数，该参数代表业务的共享上下文对象（如 `OrderContext`）。
2. **返回值类型严禁为 void**：方法不能返回 `void`。它必须返回修改后的上下文对象，或者一个类型兼容的对象。
3. **上下文类型兼容性**：返回值的类型必须能够重新赋值给入参的上下文类型，以保证执行步骤链的流畅流转。

---

## 内部运转：AsyncStepBeanPostProcessor

`AsyncStepBeanPostProcessor` 实现了 Spring 提供的 `BeanPostProcessor` 接口。在容器初始化的生命周期中：

1. **扫描识别**：遍历所有已被实例化的 Bean，反射检索其内部标注了 `@AsyncStepBean` 的方法。
2. **步骤封装**：对于每一个匹配的方法，将方法调用封装进声明式的 `StepDefinition` 中，并借助 `AsyncStepFactory` 动态生成可执行的 `AsyncStep` 实例。
3. **动态注册**：在容器中动态注册两个全新的 Spring Bean：
   * 声明式元数据 Bean，命名为 `stepDefinition.<stepName>`。
   * 可执行步骤 Bean，命名为 `asyncStep.<stepName>`。

这种双向注册机制给予了开发者在不同场景下调用的绝对灵活性。

---

## 如何注入并消费自动装配的步骤

步骤被自动装配为 Spring Bean 后，您可以直接在自己的编排模块中通过依赖注入（DI）进行消费。

### 1. 在 AsyncPipeline 线性流中消费

您可以直接注入 `AsyncStep` 类型的 Bean，快速拼装一条串行流水线：

```java
@Service
public class OrderOrchestrator {

    // 直接注入由 PostProcessor 动态注册的步骤 Bean
    private final AsyncStep<OrderContext> checkStock;
    private final AsyncStep<OrderContext> chargePayment;

    public OrderOrchestrator(
            AsyncStep<OrderContext> checkStock,
            AsyncStep<OrderContext> chargePayment
    ) {
        this.checkStock = checkStock;
        this.chargePayment = chargePayment;
    }

    public AsyncPipeline<OrderContext> buildOrderPipeline() {
        return new AsyncPipelineBuilder<OrderContext>()
                .addStep(checkStock)
                .addStep(chargePayment)
                .build();
    }
}
```

### 2. 在 SlotAsyncGraph 并行图中消费

您可以注入声明式的 `StepDefinition` 元数据 Bean，用以装配有向无环图的槽位步骤：

```java
@Service
public class BookingOrchestrator {

    private final AsyncStepFactory stepFactory;
    
    // 注入步骤声明元数据 Bean
    private final StepDefinition<BookingContext> fetchFlightDef;
    private final StepDefinition<BookingContext> fetchHotelDef;

    public BookingOrchestrator(
            AsyncStepFactory stepFactory,
            StepDefinition<BookingContext> fetchFlightDef,
            StepDefinition<BookingContext> fetchHotelDef
    ) {
        this.stepFactory = stepFactory;
        this.fetchFlightDef = fetchFlightDef;
        this.fetchHotelDef = fetchHotelDef;
    }

    public SlotAsyncGraph<BookingContext> buildBookingGraph() {
        return new SlotAsyncGraphBuilder<BookingContext>(stepFactory)
            .addSlotStep("getFlight", List.of(), fetchFlightDef.getMode(), List.of(), "flightDetails",
                view -> fetchFlightDef.getHandler().apply(view.context()))
            .addSlotStep("getHotel", List.of(), fetchHotelDef.getMode(), List.of(), "hotelDetails",
                view -> fetchHotelDef.getHandler().apply(view.context()))
            .addTerminalStep("confirmBooking", List.of("getFlight", "getHotel"), ExecutionMode.DIRECT,
                List.of("flightDetails", "hotelDetails"),
                view -> {
                    BookingContext ctx = view.context();
                    // 汇聚机票和酒店详情
                    return ctx;
                })
            .build();
    }
}
```
