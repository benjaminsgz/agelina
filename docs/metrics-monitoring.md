# Metrics and Telemetry Monitoring Guide

Agelina is designed for extreme runtime efficiency. To ensure you have total visibility into parallel task execution without paying unnecessary latency penalties, the framework exposes a low-overhead telemetry hook via `SlotGraphMetricsRecorder`. 

This guide covers how the metrics subsystem works, how to implement and install a custom metrics recorder, how to integrate it with standard monitoring backends like Micrometer or Prometheus, and recommended production alerting thresholds.

---

## High-Performance Metrics Philosophy

Traditional profiling and telemetry libraries often introduce significant overhead through thread-local allocations, locking, or heavy timestamp lookups (such as `System.currentTimeMillis()`). 

Agelina mitigates this through three core design choices:
1. **Zero Allocations in the Hot Path**: The framework passes primitive values and pre-existing objects directly to the metrics sink.
2. **Nanosecond Resolution**: We use `System.nanoTime()` only around execution segments to measure CPU and queue scheduler latencies with high precision.
3. **Decoupled Telemetry**: The core framework does not pull in heavy dependency frameworks such as Micrometer, Prometheus, or Spring Boot Actuator. Instead, it defines a simple functional interface.

---

## The SlotGraphMetricsRecorder API

To receive telemetry callbacks during graph execution, implement the `SlotGraphMetricsRecorder` interface:

```java
package com.yeven.thread.framework.pipeline;

import com.yeven.thread.framework.executor.ExecutionMode;

@FunctionalInterface
public interface SlotGraphMetricsRecorder {

    /**
     * Records one node execution attempt.
     *
     * @param nodeName       the unique node name declared in the graph
     * @param mode           the execution mode used for dispatch (IO, CPU, DIRECT)
     * @param role           the node role, either "PATCH" (intermediate) or "TERMINAL" (end)
     * @param queueWaitNanos the duration spent in the executor's task queue before thread execution started
     * @param runNanos       the duration spent inside the node evaluator and slot publication
     * @param success        whether the step completed without throwing an exception
     * @param error          the Throwable instance on failure; null on success
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
     * Returns a no-op recorder. This is the default.
     */
    static SlotGraphMetricsRecorder noop() {
        return NoopSlotGraphMetricsRecorder.INSTANCE;
    }
}
```

### No-Op Default Implementation

By default, Agelina initializes with `SlotGraphMetricsRecorder.noop()`. In this mode, no-op stub calls are optimized away by the JVM, ensuring that applications that do not require telemetry do not pay any timestamp lookup overhead.

---

## Installing a Custom Metrics Recorder

You can configure a custom metrics recorder when assembling your DAG via `SlotAsyncGraphBuilder`.

### Manual Installation

```java
SlotAsyncGraph<BookingContext> graph = new SlotAsyncGraphBuilder<BookingContext>(stepFactory)
        .withMetricsRecorder(myMetricsRecorder)
        .addSlotStep("getFlight", List.of(), ExecutionMode.IO, List.of(), "flight", view -> getFlight(view.context()))
        .addTerminalStep("confirm", List.of("getFlight"), ExecutionMode.DIRECT, List.of("flight"), view -> confirm(view.context()))
        .build();
```

### Spring Boot Auto-Registration

If you are using the Spring Boot integration starter, you can expose your `SlotGraphMetricsRecorder` as a Spring bean. The starter's auto-configuration automatically detects it and configures all dynamically generated graphs with it:

```java
@Configuration
public class AgelinaMetricsConfiguration {

    @Bean
    public SlotGraphMetricsRecorder customMetricsRecorder() {
        return (nodeName, mode, role, queueWait, runTime, success, error) -> {
            // Write telemetry to your monitoring system
        };
    }
}
```

---

## Production Integration Example: Micrometer & Prometheus

Below is a complete implementation that maps Agelina telemetry to Micrometer gauges, counters, and timers. This is highly suitable for Spring Boot Actuator setups pushing to Prometheus or Datadog.

```java
import com.yeven.thread.framework.executor.ExecutionMode;
import com.yeven.thread.framework.pipeline.graph.SlotGraphMetricsRecorder;
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

        // 1. Record Execution Duration (Timer)
        Timer.builder("agelina.graph.node.execution")
                .description("Duration of node execution")
                .tags("node", nodeName, "mode", mode.name(), "role", role, "status", status)
                .register(registry)
                .record(runNanos, TimeUnit.NANOSECONDS);

        // 2. Record Queue Waiting Time (Timer)
        if (queueWaitNanos > 0) {
            Timer.builder("agelina.graph.node.queue.wait")
                    .description("Duration node spent waiting in thread pool queues")
                    .tags("node", nodeName, "mode", mode.name(), "role", role)
                    .register(registry)
                    .record(queueWaitNanos, TimeUnit.NANOSECONDS);
        }

        // 3. Record Execution Counters
        registry.counter("agelina.graph.node.calls", 
                "node", nodeName, "mode", mode.name(), "role", role, "status", status)
                .increment();

        // 4. Record Errors by Exception Type
        if (!success && error != null) {
            registry.counter("agelina.graph.node.errors", 
                    "node", nodeName, "exception", error.getClass().getSimpleName())
                    .increment();
        }
    }
}
```

---

## Production Alerting Thresholds and Guidelines

Monitoring latency at a macroscopic level often masks issues inside individual tasks. We recommend setting up standard Grafana alerts based on the following threshold models:

### 1. Scheduler Queue Delay Alert (Queue Wait Time)
* **Metric**: `agelina_graph_node_queue_wait_seconds` (percentile p95 / p99)
* **Symptom**: Step is ready to run, but cannot obtain a thread pool worker.
* **Alert Rule**:
  * **Warning**: p99 Queue Wait Time > 50ms for `ExecutionMode.IO`
  * **Critical**: p99 Queue Wait Time > 200ms for `ExecutionMode.IO` or `ExecutionMode.CPU`
* **Mitigation**: The thread pool is saturated. Review thread pool size configurations, task queue limits, or optimize down upstream dependency latencies.

### 2. Node Failure Rate Alert
* **Metric**: `rate(agelina_graph_node_calls_total{status="FAILURE"}[1m]) / rate(agelina_graph_node_calls_total[1m])`
* **Alert Rule**:
  * **Warning**: Failure rate > 1% in 5 minutes
  * **Critical**: Failure rate > 5% in 5 minutes
* **Mitigation**: Drill down into specific node names and check `agelina_graph_node_errors_total` to isolate backend failures or network timeouts.

### 3. Direct Step Blocking Warning
* **Metric**: `agelina_graph_node_execution_seconds{mode="DIRECT"}` (p99)
* **Symptom**: Tasks assigned to `DIRECT` mode are executing blocking operations.
* **Alert Rule**:
  * **Warning**: p99 execution duration > 10ms for `DIRECT` steps.
* **Mitigation**: Because `DIRECT` steps execute directly on the thread calling `SlotAsyncGraph.join()` or dispatching threads without context switching, blocking operations here will starve the dispatcher loops. Convert these steps to `ExecutionMode.IO` immediately.
