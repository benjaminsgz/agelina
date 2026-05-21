# Agelina Async Orchestrator

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![JDK](https://img.shields.io/badge/JDK-17%2B-orange.svg)](#)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.x-brightgreen.svg)](#)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/benjaminsgz/agelina/pulls)

ThreadPool is a lightweight async orchestration framework for Spring Boot applications. It is designed for business flows that need explicit execution routing, dependency-aware coordination, and low-overhead hot paths.

The project currently exposes two main orchestration models:

- `AsyncPipeline` for linear, step-by-step flows
- `SlotAsyncGraph` for high-performance DAG execution with symbolic slots compiled to indexed access

## Why This Project Exists

In many backend services, business logic is no longer a single synchronous method call. A request may need to:

- call databases, RPC services, or caches
- mix I/O-heavy and CPU-heavy work
- execute some branches in parallel
- aggregate intermediate results safely

This project provides a structured way to model those flows while keeping thread pool ownership and execution behavior explicit.

## Modules

- `threadpool-framework`: core execution and orchestration primitives
- `threadpool-spring-boot-autoconfigure`: Spring Boot auto-configuration and bean registration support
- `threadpool-spring-boot-starter`: starter dependency for application integration
- `threadpool-auth-demo`: login flow demo built with `AsyncPipeline`
- `threadpool-dag-demo`: quote preview demo built with `SlotAsyncGraph`

## Documentation Portals

Agelina provides a highly detailed, bilingual (English and Chinese) documentation suite to help developers build applications and architects understand the system internals.

### Developer Guides (/docs)
* [English Portal Index](docs/README.md)
* [Chinese Portal Index (中文开发者门户)](docs/README_zh.md)

Developer guides cover step-by-step setup, linear sequential pipeline orchestration, parallel slot DAG development, Spring Boot auto-configuration post-processing, and telemetry metrics monitoring integration.

### High-Level Architecture & Design Deep-dives (/docs-architect)
* [English Architecture Index](docs-architect/README.md)
* [Chinese Architecture Index (中文系统架构门户)](docs-architect/README_zh.md)

Architecture guides cover physical thread pool isolation models, mathematical sizing calculations based on Little's Law, the internal SlotAsyncGraph compiler (symbolic table translating and CAS-based readyBits AtomicLongArray bitset dispatcher), queue sizes and backpressure overload protection (CallerRunsPolicy rationale), and C4 sequence/context diagrams using Mermaid.

## Core Concepts

### `ExecutionMode`

Each step declares where it should run:

- `IO`: blocking or latency-bound work such as DB, RPC, or remote calls
- `CPU`: compute-heavy steps
- `DIRECT`: run immediately on the current thread without extra dispatching

This lets the framework route work to the appropriate executor instead of mixing everything into the same pool.

### `AsyncStepFactory`

`AsyncStepFactory` converts a `StepDefinition` into an executable `AsyncStep` and dispatches it based on its `ExecutionMode`.

```java
AsyncStep<Ctx> step = stepFactory.create(new StepDefinition<>(
    "loadUser",
    ExecutionMode.IO,
    ctx -> ctx.withUser(repo.find(ctx.userId()))
));
```

### `AsyncPipeline`

`AsyncPipeline` is intended for straightforward sequential flows.

Characteristics:

- easy to read and assemble
- ideal for request processing with strict ordering
- later steps are skipped if an earlier step fails

### `SlotAsyncGraph`

`SlotAsyncGraph` is the high-performance DAG engine in this repository. It is intended for dependency-heavy async workflows where multiple branches can run in parallel and later converge.

Key ideas:

- shared intermediate values are stored in `slots`
- symbolic names such as `"memberLevel"` are resolved to slot indexes during build time
- runtime access uses indexed slots instead of `Map` or `ConcurrentHashMap`
- readiness is tracked with bitsets for cross-thread visibility
- topology and dependency rules are validated during `build()`

This gives developers symbolic, readable APIs at authoring time and efficient indexed access at runtime.

## Fail-Fast Validation in `SlotAsyncGraph`

The graph builder validates the DAG before runtime to catch invalid flows early.

Checks include:

- single writer per slot
- dependency closure for slot consumers
- cycle detection
- a valid terminal step as the graph exit

This avoids a large class of runtime race conditions and incomplete dependency wiring.

## Spring Integration

You can register steps through code or via annotation-based auto-registration.

Annotating a method with `@AsyncStepBean` automatically registers:

- `stepDefinition.<stepName>`
- `asyncStep.<stepName>`

Method constraints:

- exactly one parameter, usually the context object
- return type must not be `void`
- return type must be compatible with the context model being used

Example:

```java
@Component
public class UserSteps {

    @AsyncStepBean(name = "loadUser", mode = ExecutionMode.IO)
    public LoginContext loadUser(LoginContext ctx) {
        return ctx.withUser(repo.findByUsername(ctx.getUsername()));
    }
}
```

## Quick Start

### 1. Add the starter

```xml
<dependency>
  <groupId>com.yeven</groupId>
  <artifactId>threadpool-spring-boot-starter</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure thread pools

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

### 3. Build a linear pipeline

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

### 4. Build a DAG with slots

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

## Demo Applications

### `threadpool-auth-demo`

This demo shows a sequential login flow built with `AsyncPipeline`.

Default server:

- `http://localhost:8080`

Endpoint:

```http
POST /auth/login
Content-Type: application/json

{
  "username": "demo",
  "password": "123456"
}
```

The application uses MySQL. The initialization script is located at `scripts/mysql-init.sql`.

Default database settings:

- host: `localhost`
- port: `3306`
- database: `threadpool_demo`
- username: `threadpool`
- password: `threadpool`

### `threadpool-dag-demo`

This demo shows a quote preview workflow built with `SlotAsyncGraph`, where several upstream tasks run concurrently and the final response is assembled in a terminal step.

Default server:

- `http://localhost:8081`

Endpoint:

```http
POST /quotes/preview
Content-Type: application/json

{
  "userId": "u-1001",
  "sku": "sku-001",
  "quantity": 2,
  "couponCode": "SPRING"
}
```

## Build And Run

Run commands from `threadpool/`:

```bash
mvn -q test
mvn -q -DskipTests compile
```

Run a demo application:

```bash
mvn -pl threadpool-auth-demo spring-boot:run
mvn -pl threadpool-dag-demo spring-boot:run
```

## Docker Compose

The repository includes a `docker-compose.yml` that builds and starts the DAG demo and a `wrk` container for load testing.

Typical use:

```bash
docker compose up --build
```

Related helper files:

- `scripts/wrk-quote.lua`
- `reports/`

## Performance Notes

- keep `IO` and `CPU` workloads in separate pools
- use `DIRECT` only for very small operations
- prefer `SlotAsyncGraph` for hot paths with parallel dependencies
- prefer symbolic slot names while authoring; they are compiled to indexed access during build
- use patch-style multi-slot writes only when a step truly needs to publish multiple outputs

## Compatibility

- JDK 17+
- Maven 3.9+
- Spring Boot 3.3.x

## License

Apache License 2.0
