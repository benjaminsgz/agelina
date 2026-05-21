# Agelina 快速入门指南

Agelina 是一个专为 Spring Boot 应用程序设计的轻量级、高性能异步编排框架。它为复杂的后端业务逻辑提供了显式的执行路径路由、依赖感知协调和极低开销的执行热路径。

## 前提条件

在引入 Agelina 之前，请确保您的开发环境满足以下要求：
* JDK 17 或更高版本
* Maven 3.9 或更高版本
* Spring Boot 3.3.x 或更高版本

## 框架安装

要将 Agelina 集成到您的 Spring Boot 项目中，请将以下 starter 依赖添加到您项目的 `pom.xml` 文件中：

```xml
<dependency>
  <groupId>com.yeven</groupId>
  <artifactId>threadpool-spring-boot-starter</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

该依赖项将自动引入核心原语模块 (`threadpool-framework`) 和 Spring 自动配置模块 (`threadpool-spring-boot-autoconfigure`)。

## 线程池配置

Agelina 根据任务的运行特性，将不同的执行负载调度到专用的隔离线程池中。您需要在 `application.yml` 或 `application.properties` 中定义这些线程池参数：

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

### 配置键深度解析

* `core-size`：分配给线程池的基准常驻核心线程数。
* `max-size`：线程池在高负载下允许动态扩展的最大线程数上限。
* `queue-capacity`：支持线程池的 `LinkedBlockingQueue` 队列容量大小。一旦满载，新提交的任务将触发配置的拒绝策略。
* `keep-alive-seconds`：非核心空闲线程在被销毁前可以存活的时间（秒）。
* `rejection-policy`：当工作队列满且线程数达到最大值时的拒绝处理策略。支持以下枚举值：
  * `CALLER_RUNS`：直接在提交任务的调用者线程中执行该任务。这起到了天然的背压（Backpressure）限流作用，防止应用内存溢出。
  * `ABORT`：立即抛出 `RejectedExecutionException` 异常拒绝执行。
  * `DISCARD`：静默丢弃当前被拒绝的任务，不抛出异常。
  * `DISCARD_OLDEST`：丢弃工作队列中最旧的未处理任务，以便为新任务腾出空间。

---

## 核心原语

### 1. ExecutionMode (执行模式)

Agelina 中的每个步骤或任务都必须显式声明其运行时应当分配给哪类执行资源。这通过 `ExecutionMode` 枚举定义：

* `IO`：适用于阻塞性或高延迟的任务（如数据库读写、HTTP/RPC 远程调用、文件系统读写等）。
* `CPU`：适用于计算密集型或非阻塞的高频计算任务（如加解密运算、复杂的折扣逻辑、JSON 序列化反序列化、格式校验等）。
* `DIRECT`：直接在当前调用线程中同步执行，绕过任何工作队列和线程池调度的开销。仅适用于极其轻量、非阻塞的极速运算。

### 2. StepDefinition (步骤定义)

`StepDefinition` 负责将您的 Lambda 表达式或方法引用包装起来，并附带诸如步骤唯一标识名称、`ExecutionMode` 等声明式元数据。

```java
StepDefinition<LoginContext> loadUserDef = new StepDefinition<>(
    "loadUser",
    ExecutionMode.IO,
    context -> context.withUser(userRepository.findByUsername(context.getUsername()))
);
```

### 3. AsyncStepFactory (异步步骤工厂)

`AsyncStepFactory` 是执行链的生成引擎。它负责将声明式的 `StepDefinition` 转换为真正可执行的 `AsyncStep` 实例，并将具体的底层线程调度逻辑封装在自动装配的 `ExecutionDispatcher` 中。

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

构建出 `AsyncStep` 后，可以非常方便地装配进入线性串行的 `AsyncPipeline`，或者作为并行有向无环图 `SlotAsyncGraph` 的数据源。
