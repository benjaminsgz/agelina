# 指标度量与监控指南

Agelina 的设计以极致的运行时性能为核心。为了在保证高并发执行效率的同时，让开发者能够清晰地掌握并行任务在运行时的执行状况，框架通过 `SlotGraphMetricsRecorder` 提供了一种低开销的异步遥测钩子（Telemetry Hook）。

本指南将介绍监控度量系统的内部工作原理、如何实现并安装自定义的指标度量记录器、如何与主流监控系统（如 Micrometer 或 Prometheus）进行对接，并给出生产环境下的告警阈值建议。

---

## 高性能监控哲学

传统的应用性能监控（APM）和度量框架在记录数据时，往往会通过 ThreadLocal 分配内存、使用独占锁、或进行大量的系统时间戳读取（如 `System.currentTimeMillis()`），这在高吞吐场景下会引入不可忽视的延迟。

Agelina 采用以下三种设计策略来将开销降到最低：
1. **热路径零内存分配（Zero Allocations in Hot Path）**：框架直接向指标记录器传递原始基本类型数据或已存在的对象引用，不在热路径中创建临时包装对象。
2. **纳秒级高精度（Nanosecond Resolution）**：我们仅在任务调度与执行的临界点调用 `System.nanoTime()`，从而精准捕捉线程池排队等待延迟与 CPU 执行开销。
3. **零外部依赖（Decoupled Telemetry）**：核心框架不强制引入任何如 Micrometer、Prometheus 或 Spring Boot Actuator 等重量级监控框架，而是通过一个极简的函数式接口向外暴露度量能力。

---

## SlotGraphMetricsRecorder API 接口定义

要想在 DAG 运行期捕获各个节点的执行时间及状态，您需要实现 `SlotGraphMetricsRecorder` 接口：

```java
package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.ExecutionMode;

@FunctionalInterface
public interface SlotGraphMetricsRecorder {

    /**
     * 记录单次节点执行度量数据。
     *
     * @param nodeName       在图中声明的节点唯一名称
     * @param mode           调度派发时使用的执行模式（IO, CPU, DIRECT）
     * @param role           节点在图中的角色，目前为 "PATCH"（中间槽位）或 "TERMINAL"（终端步骤）
     * @param queueWaitNanos 节点从依赖就绪到线程真正开始执行，在线程池队列中排队等待的纳秒数
     * @param runNanos       节点内部 evaluator 执行以及槽位值发布阶段消耗的纳秒数
     * @param success        步骤是否执行成功（未抛出异常）
     * @param error          失败时抛出的 Throwable 实例，执行成功时为 null
     */
    void recordNode(
            String nodeName,
            ExecutionMode mode,
            String role,
            long queueWaitNanos,
            long runNanos,
            boolean success,
            Throwable error
    );

    /**
     * 获取一个空操作（No-op）的记录器。这是框架的默认行为。
     */
    static SlotGraphMetricsRecorder noop() {
        return NoopSlotGraphMetricsRecorder.INSTANCE;
    }
}
```

### 默认空操作实现

在默认情况下，Agelina 初始化时会使用 `SlotGraphMetricsRecorder.noop()`。在该模式下，空操作方法将被 JVM 深度内联并优化消除，从而确保不使用监控功能的应用程序无须承担任何额外的时间戳获取和方法调用开销。

---

## 接入自定义指标度量记录器

您可以在使用 `SlotAsyncGraphBuilder` 构建 DAG 时，手动配置或自动装配自定义的指标记录器。

### 方式 1：手动传入

```java
SlotAsyncGraph<BookingContext> graph = new SlotAsyncGraphBuilder<BookingContext>(stepFactory)
        .withMetricsRecorder(myMetricsRecorder)
        .addSlotStep("getFlight", List.of(), ExecutionMode.IO, List.of(), "flight", view -> getFlight(view.context()))
        .addTerminalStep("confirm", List.of("getFlight"), ExecutionMode.DIRECT, List.of("flight"), view -> confirm(view.context()))
        .build();
```

### 方式 2：Spring Boot 自动装配

如果您引入了 Spring Boot 集成 Starter，您只需将 `SlotGraphMetricsRecorder` 的实现声明为一个 Spring Bean。自动配置模块会自动检测到此 Bean，并在构建所有动态 DAG 实例时将其自动安装到图中：

```java
@Configuration
public class AgelinaMetricsConfiguration {

    @Bean
    public SlotGraphMetricsRecorder customMetricsRecorder() {
        return (nodeName, mode, role, queueWait, runTime, success, error) -> {
            // 在此将度量指标写入您的监控系统中
        };
    }
}
```

---

## 生产集成实践：对接 Micrometer 与 Prometheus

以下是一个将 Agelina 的遥测数据映射到 Micrometer 計量指标的完整实现。这非常适用于引入了 Spring Boot Actuator 的生产系统，可将数据上报至 Prometheus 或 Datadog。

```java
import com.yeven.thread.framework.executor.ExecutionMode;
import com.yeven.thread.framework.pipeline.SlotGraphMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class MicrometerSlotGraphMetricsRecorder implements SlotGraphMetricsRecorder {

    private final MeterRegistry registry;

    public MicrometerSlotGraphMetricsRecorder(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordNode(
            String nodeName,
            ExecutionMode mode,
            String role,
            long queueWaitNanos,
            long runNanos,
            boolean success,
            Throwable error
    ) {
        String status = success ? "SUCCESS" : "FAILURE";

        // 1. 记录节点执行时长 (Timer)
        Timer.builder("agelina.graph.node.execution")
                .description("节点单次执行的持续时间")
                .tags("node", nodeName, "mode", mode.name(), "role", role, "status", status)
                .register(registry)
                .record(runNanos, TimeUnit.NANOSECONDS);

        // 2. 记录线程池队列排队等待时长 (Timer)
        if (queueWaitNanos > 0) {
            Timer.builder("agelina.graph.node.queue.wait")
                    .description("节点在线程池任务队列中排队等待被线程拉起执行的时长")
                    .tags("node", nodeName, "mode", mode.name(), "role", role)
                    .register(registry)
                    .record(queueWaitNanos, TimeUnit.NANOSECONDS);
        }

        // 3. 记录执行调用次数计数器 (Counter)
        registry.counter("agelina.graph.node.calls", 
                "node", nodeName, "mode", mode.name(), "role", role, "status", status)
                .increment();

        // 4. 记录发生的异常类型次数计数器 (Counter)
        if (!success && error != null) {
            registry.counter("agelina.graph.node.errors", 
                    "node", nodeName, "exception", error.getClass().getSimpleName())
                    .increment();
        }
    }
}
```

---

## 生产环境监控报警水位阈值指南

仅监控微服务宏观的平均响应时间（Average Latency）往往会掩盖多并发任务链中个别任务引起的“长尾延迟”。我们强烈建议您基于以下阈值模型在 Grafana 中配置报警指标：

### 1. 调度队列排队延迟报警（Queue Wait Time）
* **监控指标**：`agelina_graph_node_queue_wait_seconds` (分位数 p95 / p99)
* **表现症状**：步骤依赖已全部就绪，但由于无法获取到空闲的工作线程，而在阻塞队列中长时间排队。
* **告警规则**：
  * **警告级（Warning）**：`ExecutionMode.IO` 节点排队延迟的 p99 超过 50ms。
  * **严重级（Critical）**：`ExecutionMode.IO` 或 `ExecutionMode.CPU` 节点排队延迟的 p99 超过 200ms。
* **应急预案**：线程池已被任务打满。请评估并调大线程池的最大线程数配置、优化任务队列上限，或优化并降低其他前置依赖任务的响应耗时。

### 2. 节点执行错误率报警
* **监控指标**：`rate(agelina_graph_node_calls_total{status="FAILURE"}[1m]) / rate(agelina_graph_node_calls_total[1m])`
* **告警规则**：
  * **警告级（Warning）**：任意节点 5 分钟内执行错误率超过 1%。
  * **严重级（Critical）**：任意节点 5 分钟内执行错误率超过 5%。
* **应急预案**：通过指标中的 `node` 标签定位到发生错误的特定步骤名称，并通过分析 `agelina_graph_node_errors_total` 中的异常类型来排查具体的下游系统中断或调用超时问题。

### 3. DIRECT 模式节点执行阻塞报警
* **监控指标**：`agelina_graph_node_execution_seconds{mode="DIRECT"}` (p99)
* **表现症状**：声明为 `DIRECT` 的任务中，包含了阻塞 I/O 或耗时严重的 CPU 密集型操作。
* **告警规则**：
  * **警告级（Warning）**：`DIRECT` 模式的节点 p99 执行时间超过 10ms。
* **应急预案**：因为 `DIRECT` 步骤直接在调度循环内或调用 `SlotAsyncGraph.join()` 的调用者线程上运行（没有上下文切换），如果在此模式下进行阻塞操作，会直接阻塞调度循环的派发。请立即将该步骤的执行模式调整为 `ExecutionMode.IO`。
