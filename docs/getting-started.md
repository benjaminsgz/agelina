# Getting Started with Agelina

Agelina is a lightweight, high-performance asynchronous orchestration framework designed for Spring Boot applications. It provides explicit execution routing, dependency-aware coordination, and low-overhead hot paths for complex backend business logic.

## Prerequisites

Before integrating Agelina, ensure your environment meets the following specifications:
* JDK 17 or higher
* Maven 3.9 or higher
* Spring Boot 3.3.x or higher

## Installation

To integrate Agelina into your Spring Boot application, add the starter dependency to your project's `pom.xml` file:

```xml
<dependency>
  <groupId>com.yeven</groupId>
  <artifactId>threadpool-spring-boot-starter</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

This starter pulls in both the core framework primitives (`threadpool-framework`) and the Spring autoconfiguration classes (`threadpool-spring-boot-autoconfigure`).

## Thread Pool Configuration

Agelina segregates execution workloads by scheduling tasks onto dedicated thread pools based on their runtime characteristics. Define these pools in your `application.yml` or `application.properties` file:

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

### Configuration Keys Explained

* `core-size`: The baseline thread count allocated to the pool.
* `max-size`: The maximum thread count limit the pool can dynamically scale up to under high load.
* `queue-capacity`: The size of the `LinkedBlockingQueue` backing the pool. Once filled, tasks invoke the configured rejection policy.
* `keep-alive-seconds`: The idle duration before scaling threads back down to the core size.
* `rejection-policy`: The handler invoked when the queue is saturated. Supported values are:
  * `CALLER_RUNS`: Executes the task directly on the caller thread, acting as a natural backpressure mechanism.
  * `ABORT`: Rejects the task with a `RejectedExecutionException`.

`DISCARD` and `DISCARD_OLDEST` are intentionally rejected at startup because silently dropped async tasks can leave callers waiting on Futures that never complete.

---

## Core Primitives

### 1. ExecutionMode

Every task or step in Agelina must explicitly declare where it should be executed. This is governed by the `ExecutionMode` enum:

* `IO`: Intended for blocking or latency-bound tasks (database queries, HTTP/RPC requests, file system accesses).
* `CPU`: Intended for computationally intensive tasks (cryptographic calculations, complex discount arithmetic, JSON conversions, validations).
* `DIRECT`: Executes instantly on the calling thread without undergoing queue scheduling, bypassing pool dispatching overhead. Use only for minor, non-blocking calculations.

### 2. StepDefinition

A `StepDefinition` wraps your lambda function or method reference along with declarative metadata such as a unique name and the intended `ExecutionMode`.

```java
StepDefinition<LoginContext> loadUserDef = new StepDefinition<>(
    "loadUser",
    ExecutionMode.IO,
    context -> context.withUser(userRepository.findByUsername(context.getUsername()))
);
```

### 3. AsyncStepFactory

`AsyncStepFactory` acts as the execution factory. It translates declarative `StepDefinition` instances into executable `AsyncStep` models, wrapping scheduling details through the autoconfigured `ExecutionDispatcher`.

```java
@Service
public class UserService {

    private final AsyncStepFactory stepFactory;
    private final UserRepository userRepository;

    public UserService(AsyncStepFactory stepFactory, UserRepository userRepository) {
        this.stepFactory = stepFactory;
        this.userRepository = userRepository;
    }

    public AsyncStep<LoginContext> buildLoadUserStep() {
        return stepFactory.create(new StepDefinition<>(
            "loadUser",
            ExecutionMode.IO,
            ctx -> ctx.withUser(userRepository.findByUsername(ctx.getUsername()))
        ));
    }
}
```

Once built, this step can be integrated into an `AsyncPipeline` (sequential) or used directly.
