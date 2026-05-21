# Thread Pool Physical Isolation and Sizing Architecture

In modern high-performance microservices, CPU utilization and response times are heavily governed by how concurrency resources are scheduled. Agelina is built on the philosophy that shared, general-purpose thread pools are a primary source of cascading system degradation in high-throughput applications. 

This guide details the physical resource isolation architecture of Agelina (IO pool, CPU pool, and DIRECT mode), provides the mathematical theory behind thread sizing, and outlines how to tune these parameters for production workloads.

---

## The Danger of Shared Thread Pools

In typical Spring Boot or Java concurrency frameworks, developers often submit all asynchronous tasks to a single shared `ForkJoinPool` or standard `ThreadPoolExecutor`. When an application handles a mixed workload of quick computations and slow I/O (such as remote REST calls, database queries, or third-party API fetches), shared thread pools suffer from **thread starvation**.

```
Mixed Workload -> Shared Thread Pool [-------------------]
                  - Task A (I/O-bound): Blocks thread waiting for DB response. (Takes 200ms)
                  - Task B (CPU-bound): Quick JSON parsing. (Takes 2ms)
                  - Under high concurrency, Task A fills all worker threads.
                  - Task B waits in queue, causing cascading latency spikes for quick API routes.
```

If the shared thread pool is saturated with blocking I/O tasks, fast CPU-bound steps wait in task queues, resulting in high latency spikes and eventual timeout cascades.

---

## Agelina Physical Isolation Architecture

Agelina enforces strict, physical separation of execution pools. It classifies execution threads into three isolated execution modes:

```
                  +----------------------------------------------+
                  |              ExecutionDispatcher             |
                  +----------------------------------------------+
                                         |
         +-------------------------------+-------------------------------+
         |                               |                               |
         v                               v                               v
+------------------+           +------------------+            +------------------+
|     IO Pool      |           |     CPU Pool     |            |   DIRECT Mode    |
| (AsyncIOExecutor)|           | (AsyncCPUExecutor|            | (Caller Thread)  |
+------------------+           +------------------+            +------------------+
| - Blocking I/O   |           | - Computations   |            | - Lock-free ops  |
| - High Wait Ratio|           | - Low Wait Ratio |            | - Zero context-  |
| - Dynamic Sizing |           | - Core Sized     |            |   switch cost    |
+------------------+           +------------------+            +------------------+
```

### 1. The IO Pool (`ExecutionMode.IO`)
* **Designed For**: Blocking operations such as SQL queries, HTTP client requests, remote cache reads (Redis), and filesystem access.
* **Characteristics**: High thread counts, large queue bounds, and a thread-keep-alive strategy to dynamically shrink the pool size under low load.
* **Goal**: Absorb network delays and external microservice latencies without blocking CPU-bound tasks.

### 2. The CPU Pool (`ExecutionMode.CPU`)
* **Designed For**: Compute-bound processing such as cryptography, serialization/deserialization (JSON/Protocol Buffers), algorithmic checks, mathematical aggregations, and business logic execution.
* **Characteristics**: Sized strictly according to physical CPU cores, minimal queue sizes, and using abort-based immediate rejection under overflow.
* **Goal**: Ensure the OS context-switching overhead is minimized and CPU cache locality is preserved.

### 3. DIRECT Mode (`ExecutionMode.DIRECT`)
* **Designed For**: Trivial, lock-free operations such as request validation checks, symbol mapping, or assembling the final response object.
* **Characteristics**: **No thread pool allocation**. Direct execution runs immediately on the caller thread (or whichever thread completes the last dependency step).
* **Goal**: Eliminate context switching, task queue scheduling overhead, and thread hand-off latency entirely for fast steps.

---

## Mathematical Sizing Model: Little's Law

To size isolated thread pools correctly, Agelina uses a mathematical model derived from queueing theory and Little's Law.

Let $T$ represent the optimal number of threads in the target pool:

$$T = N_c \times U_t \times \left(1 + \frac{W_t}{C_t}\right)$$

Where:
* $N_c$ is the number of physical CPU cores available to the JVM container.
* $U_t$ is the target CPU utilization rate ($0 < U_t \le 1$). For safety, we usually target $U_t = 0.8$ ($80\%$) to leave overhead room for GC runs and system telemetry.
* $W_t$ is the **average waiting time** of a single task in the pool (e.g., waiting for an I/O network roundtrip, socket read, or disk read).
* $C_t$ is the **average computation time** of a single task (active CPU cycles spent calculating, serializing, or evaluating).

### Analysis of the Ratio $W_t / C_t$

The ratio of waiting time to computation time ($W_t / C_t$) represents the task blocking factor:

* **Compute-Bound Tasks**: $W_t \approx 0$ (virtually no waiting for external signals).
  Applying the formula:
  $$T \approx N_c \times 0.8 \times (1 + 0) \approx N_c \times 0.8$$
  *Recommendation*: Sizing the CPU pool at $N_c$ or $N_c + 1$ is mathematically optimal. Adding more threads will not increase throughput; it will only increase OS thread context switching cost.

* **I/O-Bound Tasks**: $W_t \gg C_t$.
  Suppose an I/O task takes 100ms in total, out of which 98ms is waiting for a database socket read, and 2ms is CPU deserialization ($W_t = 98\text{ms}, C_t = 2\text{ms}$).
  Applying the formula:
  $$T = N_c \times 0.8 \times \left(1 + \frac{98}{2}\right) = N_c \times 0.8 \times 50 = 40 \times N_c$$
  *Recommendation*: The IO pool must be scaled out significantly to maintain target throughput. A system running on 8 cores can easily support 320 to 500 threads in the IO pool.

---

## Production Sizing Configurations

Agelina allows you to customize these parameters directly through Spring Boot YAML properties, using these mathematical bounds:

```yaml
agelina:
  executor:
    cpu:
      core-size: 8              # Equal to Nc (number of CPU cores)
      max-size: 8               # Equal to core-size to avoid CPU thrashing
      queue-capacity: 1000      # Low capacity to trigger backpressure quickly
      keep-alive-seconds: 60
    io:
      core-size: 64             # Scaled dynamically according to wait ratios
      max-size: 256             # Dynamic upper bound
      queue-capacity: 5000      # Moderate queuing capacity to handle bursty spikes
      keep-alive-seconds: 30
```

---

## Execution Guidelines for Architects

To maintain the architectural integrity of Agelina's isolation system, enforce the following guidelines during peer code reviews:

1. **Direct Mode Constraints**: Steps running in `DIRECT` mode must never contain blocking code. If a step performs an HTTP request, a JDBC query, or a thread sleep, it must be declared with `ExecutionMode.IO`. Otherwise, it will steal the dispatcher or caller thread, halting graph coordination.
2. **I/O Thread Sizing Safety**: Avoid over-sizing the IO pool beyond memory boundaries. Each Java thread consumes roughly 1MB of off-heap memory for its thread stack (unless configured with custom `-Xss` limits). If memory is constrained, prefer lowering the queue bounds to trigger backpressure over scaling thread counts indefinitely.
