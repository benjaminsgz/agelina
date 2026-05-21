# Agelina 开发者中心

欢迎来到 Agelina 异步编排框架开发者中心。本目录包含详尽的开发指南、API 参考手册以及集成教程，旨在帮助您高效构建、配置并维护基于 Agelina 的高性能异步业务流。

## English Version / 英文版本

如需查看英文原版开发者索引，请参考：
* [Agelina Developer Portal (README.md)](file:///f:/agelina/docs/README.md)

## 推荐阅读路径

根据您对 Agelina 的熟悉程度或当前的开发任务，我们推荐以下阅读路径：

### 1. 初识 Agelina
* [快速入门指南 (getting-started_zh.md)](file:///f:/agelina/docs/getting-started_zh.md)：了解如何引入框架依赖、配置 Maven 坐标、在应用 YAML 中声明隔离线程池，并掌握核心基础概念。

### 2. 实现线性顺序业务逻辑
* [AsyncPipeline 开发指南 (pipeline-guide_zh.md)](file:///f:/agelina/docs/pipeline-guide_zh.md)：学习如何构建具有严格顺序执行保障和异常熔断传播的步骤级线性业务流。

### 3. 实现复杂并行 / 依赖拓扑业务逻辑
* [SlotAsyncGraph 开发指南 (graph-guide_zh.md)](file:///f:/agelina/docs/graph-guide_zh.md)：掌握如何使用基于插槽的数据编译机制与并发执行引擎，快速组装高性能有向无环图 (DAG)。

### 4. 框架集成与自动装配
* [Spring Boot 集成手册 (spring-integration_zh.md)](file:///f:/agelina/docs/spring-integration_zh.md)：了解 Spring 自动配置原理、使用 `@AsyncStepBean` 进行步骤的声明与自动发现，以及方法签名的设计规范。

### 5. 生产环境可观测性与告警
* [指标度量与监控指南 (metrics-monitoring_zh.md)](file:///f:/agelina/docs/metrics-monitoring_zh.md)：集成 `SlotGraphMetricsRecorder` API、监控线程池活跃状态与队列延迟，并制定生产告警水位阈值。

---

## 文档注册表

| 文档名称 | 访问路径 | 核心内容概述 |
| --- | --- | --- |
| 快速入门指南 | [getting-started_zh.md](file:///f:/agelina/docs/getting-started_zh.md) | 环境安装、Maven 坐标引入、YAML 线程池属性配置以及基础组件。 |
| AsyncPipeline 线性流 | [pipeline-guide_zh.md](file:///f:/agelina/docs/pipeline-guide_zh.md) | 顺序编排机制、共享上下文可见性设计以及顺序传播的异常熔断。 |
| SlotAsyncGraph 并行 DAG | [graph-guide_zh.md](file:///f:/agelina/docs/graph-guide_zh.md) | 槽位模型、符号批处理补丁 (SymbolicSlotPatch) 与终结步骤配置。 |
| Spring Boot 自动装配 | [spring-integration_zh.md](file:///f:/agelina/docs/spring-integration_zh.md) | 自动扫描装配步骤、后置处理器原理及方法入参限制约束。 |
| 监控度量与告警指南 | [metrics-monitoring_zh.md](file:///f:/agelina/docs/metrics-monitoring_zh.md) | 延迟采集机制、队列状态监测、活跃度指标输出及报警线阈值建议。 |

如需深入了解系统架构设计决策、Little's Law 线程池大小推导数学公式、基于位图（Bitsets）就绪度检查机制的内存插槽布局以及 C4 架构模型，请访问 [架构与设计决策中心 (docs-architect/README_zh.md)](file:///f:/agelina/docs-architect/README_zh.md)。
