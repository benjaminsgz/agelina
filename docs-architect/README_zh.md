# 系统架构设计门户

欢迎来到 Agelina 异步编排框架架构设计门户。本目录收录了关于框架底层执行引擎、高性能编译器设计、数学容量评估模型及核心隔离机制的深度技术剖析文档。

该门户主要面向系统架构师、框架核心贡献者，以及希望深入理解 Agelina 底层高性能并发模型和微调细节的资深开发人员。

---

## 框架架构设计哲学

Agelina 致力于从根本上解决传统响应式框架（如 Project Reactor, RxJava）的学习曲线陡峭、调试排查极其困难，以及手动配置和共用线程池时常导致瀑布式雪崩的痛点。整个框架的设计围绕以下三大基石展开：

1. **物理资源彻底隔离**：对计算密集型（CPU）、阻塞式 I/O（IO）及无锁直连调度（DIRECT）资源进行严格的物理隔离，杜绝级联式资源饥饿。
2. **编译期预优化**：将开发人员声明的“符号式”步骤依赖关系，在有向无环图（DAG）启动时一次性编译为无锁、零对象分配的“整型数组槽位索引”操作。
3. **高可靠过载保护**：为系统并发过载提供确定性的排队大小阈值评估、弹性扩容计算，以及安全兜底的线程拒绝策略设计。

---

## 文档索引清单

您可以阅读以下深度文档来掌握 Agelina 的内部架构设计：

### 1. 线程池物理隔离与容量评估
* [线程物理隔离指南 (threadpool-isolation_zh.md)](file:///f:/agelina/docs-architect/threadpool-isolation_zh.md)：剖析工作线程池彻底物理隔离（IO 线程池、CPU 线程池及 DIRECT 模式）的底层哲学。结合 Little's Law（利特尔法则）提供高精准的工作线程数计算模型。

### 2. SlotAsyncGraph 编译器与无锁 Bitset 派发器
* [有向图编译与无锁派发内幕 (slot-async-graph-compiler_zh.md)](file:///f:/agelina/docs-architect/slot-async-graph-compiler_zh.md)：详解 `SlotAsyncGraphBuilder` 的 DAG 编译和构建过程。包括符号解析表 `SlotSymbolTable` 机制、基于 Bitset 位掩码 `readyBits` 的无锁 CAS 派发，以及循环依赖 DFS 检测、单写入约束和依赖完整闭包等三道静态强校验防线。

### 3. 背压管控与过载保护机制
* [背压过载保护指南 (backpressure-overload_zh.md)](file:///f:/agelina/docs-architect/backpressure-overload_zh.md)：阐述框架在应对系统饱和状态时的吞吐管控机制。详细说明了任务阻塞队列容量的设计模型，以及自动装配的拒绝执行策略配置 rationale（IO 任务使用 CALLER_RUNS，CPU 任务使用 ABORT）。

### 4. C4 系统架构蓝图与时序图谱
* [C4 架构与时序图谱 (c4-architecture-models_zh.md)](file:///f:/agelina/docs-architect/c4-architecture-models_zh.md)：基于 C4 模型标准的系统架构蓝图。内置完整的 Mermaid 时序图、系统边界上下文图与组件级数据流图，清晰展现从 Spring Bean 后置处理、图定义编译到运行时并发拓扑驱动的完整生命周期。

---

## 双语文档对照

Agelina 架构设计门户下的每一篇文档均提供中英双语的完全对等维护：

| 英文原版 | 中文翻译 | 文档职责概述 |
|---|---|---|
| [threadpool-isolation.md](file:///f:/agelina/docs-architect/threadpool-isolation.md) | [threadpool-isolation_zh.md](file:///f:/agelina/docs-architect/threadpool-isolation_zh.md) | 线程池隔离机制与容量计算公式 |
| [slot-async-graph-compiler.md](file:///f:/agelina/docs-architect/slot-async-graph-compiler.md) | [slot-async-graph-compiler_zh.md](file:///f:/agelina/docs-architect/slot-async-graph-compiler_zh.md) | 槽位图编译器设计与位掩码调度器 |
| [backpressure-overload.md](file:///f:/agelina/docs-architect/backpressure-overload.md) | [backpressure-overload_zh.md](file:///f:/agelina/docs-architect/backpressure-overload_zh.md) | 阻塞排队容量与生产背压控制 |
| [c4-architecture-models.md](file:///f:/agelina/docs-architect/c4-architecture-models.md) | [c4-architecture-models_zh.md](file:///f:/agelina/docs-architect/c4-architecture-models_zh.md) | Mermaid 时序图与 C4 模型可视化 |
