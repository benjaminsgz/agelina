# Architecture and System Design Portal

Welcome to the Agelina Async Orchestrator Architecture Portal. This directory houses deep-dives into the high-level system architecture, core design principles, mathematical sizing models, and internal compiler mechanics of the framework.

This portal is intended for system architects, core framework contributors, and senior developers looking to understand the low-level execution models of Agelina.

---

## Architectural Philosophy

Agelina was built to solve the performance and complexity trade-offs of traditional reactive programming frameworks (e.g., Project Reactor, RxJava) and manual thread pool configurations. It is designed around three pillars:

1. **Physical Resource Isolation**: Strictly separating compute, blockable I/O, and non-blocking scheduling resources to prevent cascading resource starvation.
2. **Compile-Time Optimization**: Translating user-defined symbolic dependencies into lock-free, allocation-free array slot index operations at graph boot time.
3. **Robust Overload Protection**: Applying proactive backpressure and queue limits with fail-safe rejection execution policies.

---

## Document Index

Please follow the guides below to understand the internal architecture of Agelina:

### 1. Thread Pool Isolation Philosophy
* [Thread Pool Isolation Guide (threadpool-isolation.md)](threadpool-isolation.md): Deep-dive into the physical separation of threads (IO pool, CPU pool, and DIRECT mode). Explains why traditional shared thread pools fail under heavy load and details the mathematical formula based on Little's Law for calculating perfect thread pool sizing.

### 2. SlotAsyncGraph Compiler and Bitset Dispatcher
* [Slot Compiler Internals (slot-async-graph-compiler.md)](slot-async-graph-compiler.md): Mechanics of `SlotAsyncGraphBuilder` compile phase. Learn how symbolic slot names are translated into direct integer array index operations (`SlotSymbolTable`), the lock-free bitwise ready bitmask calculations (`readyBits`), and static fail-fast validation rules (DFS cycle detection, single-writer validation, and dependency closure checks).

### 3. Backpressure and Overload Protection
* [Backpressure and Queue Sizing (backpressure-overload.md)](backpressure-overload.md): Explains how the framework manages system saturation. Details how task queues are sized and configured, and the rationale behind auto-configured fallback rejection handlers (CALLER_RUNS vs ABORT).

### 4. C4 Architecture & Sequence Models
* [C4 Architectural Models (c4-architecture-models.md)](c4-architecture-models.md): Complete system architectural blueprints modeled under C4 standards (Context, Container, Component). Contains high-fidelity Mermaid sequence, context, and structural flow diagrams mapping out graph initialization, step post-processing, and parallel runtime execution dispatch loops.

---

## Language Parity

Every document in the Agelina Architecture Portal is fully maintained in both English and Chinese:

| English Deep-dive | Chinese Translation | Description |
|---|---|---|
| [threadpool-isolation.md](threadpool-isolation.md) | [threadpool-isolation_zh.md](threadpool-isolation_zh.md) | Thread Pool Sizing and Isolation |
| [slot-async-graph-compiler.md](slot-async-graph-compiler.md) | [slot-async-graph-compiler_zh.md](slot-async-graph-compiler_zh.md) | Slot Graph Compilation and Dispatcher |
| [backpressure-overload.md](backpressure-overload.md) | [backpressure-overload_zh.md](backpressure-overload_zh.md) | Backpressure and Sizing |
| [c4-architecture-models.md](c4-architecture-models.md) | [c4-architecture-models_zh.md](c4-architecture-models_zh.md) | System Visual Blueprints |
