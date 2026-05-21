# Backpressure and Overload Protection Architecture

High-performance distributed systems must be resilient to sudden spikes in traffic and prolonged downstream outages. Saturated memory queues and unbound thread creation are common culprits for Out-Of-Memory (OOM) crashes and system instability. 

This guide details Agelina's proactive backpressure design, queue sizing philosophies, and the technical rationale behind its default auto-configured rejection policies.

---

## The Backpressure Core: Sizing the Queue Bounds

In a naive asynchronous architecture, thread pool queues are configured as unbound (`LinkedBlockingQueue` with a capacity of `Integer.MAX_VALUE`). Under a sustained overload:

1. Incoming request rates exceed the thread pool's execution rate.
2. Millions of tasks accumulate in the memory queue.
3. Heap usage swells, triggering intense Garbage Collection (GC) pauses (STW).
4. The system runs out of memory and crashes (OOM).

To prevent this, Agelina mandates strictly bounded task queues for all isolated pools, configured via `application.yml`:

```yaml
agelina:
  executor:
    io:
      queue-type: LINKED_BLOCKING # Safe bounded queue
      queue-capacity: 5000       # High-throughput capacity buffer
    cpu:
      queue-type: LINKED_BLOCKING
      queue-capacity: 1000       # Low capacity to trigger quick fail-fast
```

When a task queue fills to capacity, the pool enters a **saturated state**. At this point, the thread pool's `RejectedExecutionHandler` is triggered.

---

## Rejection Policies: IO Pool vs. CPU Pool

How a pool reacts to saturation depends entirely on the nature of the tasks it runs. Agelina auto-configures different default rejection handlers for the IO and CPU executors based on queuing theory.

```
Incoming Request -> [Queue Full]
                         |
         +---------------+---------------+
         |                               |
         v                               v
   [ IO Pool ]                     [ CPU Pool ]
  rejection-policy:               rejection-policy:
    CALLER_RUNS                      ABORT
         |                               |
         v                               v
Runs task on caller thread       Fails immediately with
(slowing down input loop)        RejectedExecutionException
         |                               |
  (Natural Backpressure)           (Load Shedding)
```

### 1. The IO Pool: `RejectionPolicy.CALLER_RUNS`
For I/O-bound tasks, the primary bottleneck is waiting for external resources (databases, network APIs). 

* **How it works**: When the IO queue is full, `CallerRunsPolicy` is invoked. The thread submitting the task (usually the dispatcher thread or an upstream servlet engine thread) is hijacked to execute the task immediately.
* **The Backpressure Effect**: Because the submitter thread is now busy running a slow, blocking I/O step, it cannot submit any new tasks to the queue or accept new incoming connection packets from the network interface. 
* **The Result**: The overload pressure propagates naturally up the stack. If the servlet engine's threads (e.g., Tomcat, Netty) are all busy executing rejected tasks, they stop accepting new TCP connection requests. The client experience degrades gracefully to high latency (or a 503 response from the gateway/reverse proxy), preventing the JVM from exhausting memory.

### 2. The CPU Pool: `RejectionPolicy.ABORT`
For CPU-bound tasks, the bottleneck is physical processor cycles. The CPU is already running at $100\%$ capacity.

* **How it works**: When the CPU queue is full, the executor immediately throws a `RejectedExecutionException`.
* **Why not CALLER_RUNS?**: If a caller thread attempts to execute a heavy compute-bound task in a CPU-saturated environment, it only adds to thread contention. The OS kernel will spend valuable cycles switching thread contexts between competing worker threads on the same physical core, severely degrading overall system throughput.
* **The Load Shedding Effect**: By throwing `RejectedExecutionException` (known as **Load Shedding**), Agelina terminates the overloaded request instantly. This allows existing, partially completed tasks in the CPU queue to complete successfully without being starved out by a deluge of new tasks.

---

## Resiliency Guidelines for Architects

To design highly resilient microservices using Agelina, apply these production guidelines:

### 1. Set Conservative Queue Boundaries
Do not configure excessively large queue capacities. A queue that is too deep hides latency issues. If your p99 response time SLA is 500ms, and your IO pool can process 10,000 tasks/second, a queue size of 5,000 provides a 500ms buffer. Anything larger means tasks will sit in the queue until they have already exceeded their client-side timeout SLA, rendering execution useless.

### 2. Configure Downstream Circuit Breakers
While `CALLER_RUNS` protects the memory of your local JVM, it can transfer overload stress downstream. If the database is struggling, running database calls on the caller thread continues to put pressure on the database. Combine Agelina with downstream circuit breakers (e.g., Resilience4j) to fail-fast on external connections.

### 3. Graceful Shutdown Integrations
During application updates, the JVM must terminate cleanly. Agelina's `GracefulShutdown` bean hooks into the Spring lifecycle, allowing active tasks inside the queues up to 30 seconds to drain and finish before forcing a `shutdownNow()`. This guarantees no mid-execution transaction losses for outstanding parallel pipelines.
