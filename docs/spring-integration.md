# Spring Boot Integration and Auto-Configuration

Agelina offers seamless integration with the Spring Boot container, allowing you to declare and auto-register execution steps using declarative annotations. This eliminates manual setup boilerplate and lets you inject steps directly as Spring beans.

## Auto-Configuration Starter

By adding the `threadpool-spring-boot-starter` dependency, the framework registers:
1. `ThreadPoolAutoConfiguration`: Prepares the `ExecutionDispatcher`, the default thread pools (`IO` and `CPU`), and the thread registry.
2. `AsyncStepBeanPostProcessor`: Scans all Component beans in the context, extracts methods annotated with `@AsyncStepBean`, and dynamically registers them.

---

## Declaring Steps with @AsyncStepBean

To expose a method as a reusable execution step, annotate it with `@AsyncStepBean`.

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
            throw new IllegalStateException("Insufficient stock for SKU: " + context.getSku());
        }
        context.setAvailable(true);
        return context;
    }
}
```

### Method Signature Constraints

The `@AsyncStepBean` post-processor enforces strict constraints on target methods at startup. If a method violates any of these, the application context fails to boot with a descriptive exception:

1. **Exactly One Parameter**: The method must accept exactly one parameter, representing the context object (e.g., `OrderContext`).
2. **Non-Void Return Type**: The return type must not be `void`. It must return the updated context or a compatible object.
3. **Context Compatibility**: The return value must be assignable back to the parameter type or compatible with the context chain.

---

## How it Works: AsyncStepBeanPostProcessor

The `AsyncStepBeanPostProcessor` implements Spring's `BeanPostProcessor` interface. During container initialization:

1. **Scan**: It inspects every bean class looking for methods marked with `@AsyncStepBean`.
2. **Wrap**: For every matching method, it wraps the execution in a declarative `StepDefinition` and generates an `AsyncStep` using the `AsyncStepFactory`.
3. **Register**: It dynamically registers two beans into the Spring application context:
   * A `StepDefinition` bean named `stepDefinition.<stepName>`.
   * An `AsyncStep` bean named `asyncStep.<stepName>`.

This dual registration gives you total flexibility in how you consume steps.

---

## Consuming Autoregistered Steps

Once steps are annotated, they can be injected directly into your orchestration logic.

### 1. In AsyncPipeline

Inject `AsyncStep` beans directly to assemble a sequential pipeline:

```java
@Service
public class OrderOrchestrator {

    // Inject steps registered by AsyncStepBeanPostProcessor
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

### 2. In SlotAsyncGraph

Inject the declarative `StepDefinition` beans to configure DAG slots:

```java
@Service
public class BookingOrchestrator {

    private final AsyncStepFactory stepFactory;
    
    // Inject step definitions
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
                    // Assemble flight and hotel Details
                    return ctx;
                })
            .build();
    }
}
```
