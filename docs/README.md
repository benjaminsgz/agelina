# Agelina Developer Portal

Welcome to the Agelina Async Orchestrator Developer Portal. This directory contains detailed guides, API manuals, and integration tutorials to help you build, configure, and maintain high-performance asynchronous workflows using Agelina.

## Chinese Version / 中文版本

For the Chinese translation of this developer index, please refer to:
* [Agelina 开发者中心首页 (README_zh.md)](README_zh.md)

## Reading Paths

Depending on your current task or familiarity with Agelina, we recommend the following reading paths:

### 1. New to Agelina
* [Getting Started Guide (getting-started.md)](getting-started.md): Install the framework, configure your Maven dependencies, declare thread pools in your application YAML, and understand core primitives.

### 2. Implementing Sequential Business Logic
* [AsyncPipeline Guide (pipeline-guide.md)](pipeline-guide.md): Build sequential, step-by-step business workflows with strict execution ordering and exception propagation.

### 3. Implementing Parallel / Dependency-Heavy Logic
* [SlotAsyncGraph Guide (graph-guide.md)](graph-guide.md): Construct high-performance Directed Acyclic Graphs (DAGs) using slot-based data compilation and parallel execution.

### 4. Integration & Auto-configuration
* [Spring Boot Integration Manual (spring-integration.md)](spring-integration.md): Understand autoconfigured beans, discover steps automatically using `@AsyncStepBean`, and integrate steps seamlessly.

### 5. Production Observability & Alerting
* [Metrics and Monitoring Guide (metrics-monitoring.md)](metrics-monitoring.md): Set up the `SlotGraphMetricsRecorder` API, monitor thread pools, track queue latency, and set up production alerts.

---

## Documentation Registry

| Document Name | Path | Description |
| --- | --- | --- |
| Getting Started | [getting-started.md](getting-started.md) | Initial setup, Maven coordinates, YAML structure, and core concepts. |
| AsyncPipeline Manual | [pipeline-guide.md](pipeline-guide.md) | Sequential orchestration, context visibility, and linear flow error handling. |
| SlotAsyncGraph Manual | [graph-guide.md](graph-guide.md) | Slot-based DAGs, SymbolicSlotPatch, single slot writers, and terminal configurations. |
| Spring Boot Auto-config | [spring-integration.md](spring-integration.md) | Auto-registration of steps, post-processing rules, and method signatures. |
| Metrics and Alerting | [metrics-monitoring.md](metrics-monitoring.md) | Metric capture, queue monitoring, active count metrics, and threshold guides. |

For deep technical insights into system architecture, Little's Law thread pool sizing equations, compiled slot memory layouts, and C4 models, please visit the [Architecture Portal (docs-architect/README.md)](../docs-architect/README.md).
