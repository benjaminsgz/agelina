# C4 系统架构蓝图与时序图谱

理解并发有向无环图（DAG）的执行流程离不开清晰的多层次可视化模型。本指南整理了 Agelina 异步编排框架基于 C4 架构模型标准的结构蓝图，以及详细的 Mermaid 时序图，用以直观展现有向图初始化、Spring 扫描、编译和运行时并发调度流转的完整生命周期。

---

## 1. 系统上下文蓝图 (C4 Level 1)

本图展示了 Agelina 编排引擎在 Spring Boot 微服务应用中的宏观边界，以及开发人员、客户端与下游外部依赖系统之间的交互关系。

```mermaid
graph TD
    Client["客户端 / 上游网关"]
    Developer["开发人员"]
    
    subgraph SpringBootApp["Spring Boot 微服务应用"]
        Agelina["Agelina 异步编排框架"]
        BusinessLogic["业务服务逻辑 (Service)"]
    end
    
    Database["外部数据库 (SQL / JDBC)"]
    ExternalAPI["下游第三方 Web API / RPC"]
    
    Developer -->|"1. 声明式定义异步步骤与有向图"| Agelina
    Client -->|"2. 触发 API 请求调用"| BusinessLogic
    BusinessLogic -->|"3. 委托异步编排流转"| Agelina
    Agelina -->|"4. 读写外部阻塞数据"| Database
    Agelina -->|"5. 调用接口拉取数据"| ExternalAPI
```

---

## 2. 容器设计蓝图 (C4 Level 2)

本图深入展现了 Agelina 内部的容器级模块边界，展示了 Spring 上下文、配置注册表、调度派发器与物理隔离线程池是如何协同工作的。

```mermaid
graph TB
    subgraph SpringContainer["Spring 容器上下文"]
        Properties["ThreadPoolProperties (配置属性集)"]
        Registry["ExecutorRegistry (线程池注册表)"]
        Dispatcher["DefaultExecutionDispatcher (默认派发器)"]
        Factory["AsyncStepFactory (步骤构建工厂)"]
        PostProcessor["AsyncStepBeanPostProcessor (Bean 后置处理器)"]
    end
    
    subgraph IsolatedPools["物理隔离的线程池实体"]
        IOPool["IO 线程池 (AsyncIOExecutor)"]
        CPUPool["CPU 线程池 (AsyncCPUExecutor)"]
    end
    
    PostProcessor -->|"扫描并自动包裹注册"| Factory
    Properties -->|"提供队列与线程容量参数"| IOPool
    Properties -->|"提供队列与线程容量参数"| CPUPool
    
    Registry -->|"统一持有映射"| IOPool
    Registry -->|"统一持有映射"| CPUPool
    
    Dispatcher -->|"向注册表获取物理线程池"| Registry
    Factory -->|"向派发器提交可执行任务"| Dispatcher
```

---

## 3. 组件级设计蓝图 (C4 Level 3)

本图详细描绘了运行中的 `SlotAsyncGraph` 的内部关键组件，展示了 Builder 阶段如何将字符串符号符号解析为数组槽位 ID，以及运行时如何利用位掩码进行高能控制。

```mermaid
graph TD
    subgraph SlotAsyncGraphBuilder["SlotAsyncGraphBuilder (静态编译阶段)"]
        SymbolTable["SlotSymbolTable (符号/整型 ID 互译表)"]
        DFSEngine["DFS 检测引擎 (循环依赖检测)"]
        Validator["契约与传递闭包校验器"]
    end
    
    subgraph SlotAsyncGraphRuntime["SlotAsyncGraph (运行期执行引擎)"]
        SlotsArray["slots = Object[] 数组 (极速内存数据槽存储介质)"]
        ReadyBits["readyBits = AtomicLongArray (基于高并发 Long 数组的位图就绪器)"]
        DispatcherLoop["调度分发循环 (基于位运算状态的无锁异步派发)"]
    end
    
    SymbolTable -->|"1. 翻译槽位符号为连续数组下标"| SlotsArray
    DFSEngine -->|"2. 计算并生成拓扑执行序列"| DispatcherLoop
    Validator -->|"3. 建立槽位前置就绪比特掩码"| ReadyBits
```

---

## 4. 有向图并发调度运行时序图

本时序图描绘了当业务服务线程发起一次 `SlotAsyncGraph` 执行调用时，并发步骤是如何被分派给不同的物理隔离池，如何通过无锁 CAS 修改位图状态，以及主线程如何最终安全 Join 聚合结果的完整生命历程。

```mermaid
sequenceDiagram
    autonumber
    participant Caller as 业务调用者线程 (Caller)
    participant Graph as SlotAsyncGraph 并发图实例
    participant State as SlotState (slots 数组与 readyBits 位图)
    participant IOExec as IO 隔离线程池
    participant CPUExec as CPU 隔离线程池

    Caller->>Graph: execute(context)
    activate Graph
    Graph->>State: 初始化 Object[] slots 空间并清空 AtomicLongArray 状态位
    
    Note over Graph, State: 拓扑排序计算指出没有任何前置依赖的起始节点
    
    Graph->>IOExec: 分派 "validateRequest" 步骤 (以 DIRECT 模式立即就地同步执行)
    IOExec->>State: writeSlot(0, 已验证的上下文) & markReady(0)
    
    Note over State: 比特位 0 成功置 1。检查发现该槽位关联的下游子步骤可以被激活...
    
    par 并发分支执行
        Graph->>IOExec: 异步分派 "loadInventory" 任务 (依赖槽位 0 就绪)
        Graph->>IOExec: 异步分派 "loadMemberProfile" 任务 (依赖槽位 0 就绪)
    end
    
    activate IOExec
    IOExec->>State: "loadInventory" 算完。writeSlot(1, 库存详情) & markReady(1)
    IOExec->>State: "loadMemberProfile" 算完。writeSlot(2, 会员档案) & markReady(2)
    deactivate IOExec
    
    Note over State: 比特位 1 和 2 成功置 1。调度引擎识别出可以激活折扣计算逻辑。
    
    Graph->>CPUExec: 异步分派 "calculateDiscount" 任务 (依赖槽位 1 和 2)
    activate CPUExec
    CPUExec->>State: 计算折扣完毕。writeSlot(3, 折扣计算结果) & markReady(3)
    deactivate CPUExec
    
    Note over State: 比特位 3 成功就绪。终端步骤 "confirmBooking" 满足执行条件。
    
    Graph->>Caller: 在调用者主线程上同步执行终端逻辑 (DIRECT)
    Graph-->>Caller: 返回最终聚合并更新完的共享上下文实例
    deactivate Graph
```

---

## 5. 系统架构规约审查对照

为了确保微服务在生产环境下的极高稳定性，系统的可视化架构设计契合以下三条核心架构规约：

1. **单一写入契约（Single-Writer Contract）**：虽然多个并发的异步节点可以同时从 `slots` 数组中并行读取任何已就绪的槽位数据，但有且仅有一个确定的步骤拥有向该槽位写入的特权。这彻底杜绝了并发多线程的写覆盖与指令重排冲突，使数据在读取者眼中具有绝对的不变性。
2. **零临界区阻塞（Lock-Free Dispatching）**：调度引擎的核心循环在检查节点就绪度时，只会在 CPU Cache 层面进行高频的 `readyBits` 原子位图检索，不需要获取任何操作系统级别的互斥排它锁（Mutex Locks）。这保证了多核心环境下的极限高吞吐。
3. **有界队列红线（No Unbound Queues）**：生产环境部署时必须为 `IOExec` 和 `CPUExec` 线程池配置明确的 `queue-capacity` 阻塞容量限制，确保这套背压防护机制在面对突发流量冲击时随时可用。
