# C4 Architectural Blueprints and Sequence Models

Understanding the flow of parallel DAG execution requires clear, multi-tiered visual models. This guide compiles the Agelina structural blueprint mapped under the C4 Architecture model standard, along with high-fidelity Mermaid sequence diagrams detailing graph initialization, post-processing, and parallel runtime dispatching loops.

---

## 1. System Context (C4 Level 1)

This diagram shows the high-level boundary of the Agelina orchestrator within a Spring Boot microservice and its interactions with developers, clients, and external downstream dependencies.

```mermaid
graph TD
    Client["Client / Upstream Gateway"]
    Developer["Developer"]
    
    subgraph SpringBootApp["Spring Boot Microservice"]
        Agelina["Agelina Async Orchestrator Framework"]
        BusinessLogic["Business Service Logic"]
    end
    
    Database["Database (JDBC / SQL)"]
    ExternalAPI["Downstream Web APIs"]
    
    Developer -->|"1. Declares steps and DAGs"| Agelina
    Client -->|"2. Triggers API Request"| BusinessLogic
    BusinessLogic -->|"3. Delegates orchestration"| Agelina
    Agelina -->|"4. Reads/Writes I/O"| Database
    Agelina -->|"5. Fetches data"| ExternalAPI
```

---

## 2. Container Design (C4 Level 2)

This diagram drills down into the core container boundaries of Agelina, highlighting how the Spring context, configuration registries, execution dispatchers, and physically isolated thread pools cooperate.

```mermaid
graph TB
    subgraph SpringContainer["Spring Application Context"]
        Properties["ThreadPoolProperties"]
        Registry["ExecutorRegistry"]
        Dispatcher["DefaultExecutionDispatcher"]
        Factory["AsyncStepFactory"]
        PostProcessor["AsyncStepBeanPostProcessor"]
    end
    
    subgraph IsolatedPools["Physical Thread Pools"]
        IOPool["IO Pool (AsyncIOExecutor)"]
        CPUPool["CPU Pool (AsyncCPUExecutor)"]
    end
    
    PostProcessor -->|"Scans and registers"| Factory
    Properties -->|"Configures queue & thread sizes"| IOPool
    Properties -->|"Configures queue & thread sizes"| CPUPool
    
    Registry -->|"Hooks up"| IOPool
    Registry -->|"Hooks up"| CPUPool
    
    Dispatcher -->|"Queries active executors"| Registry
    Factory -->|"Submits runnable tasks"| Dispatcher
```

---

## 3. Component Level Layout (C4 Level 3)

This diagram showcases the internal components of a running `SlotAsyncGraph`, detailing how symbolic slot definitions are compiled into lock-free array slot references and governed by bitmask trackers.

```mermaid
graph TD
    subgraph SlotAsyncGraphBuilder["SlotAsyncGraphBuilder (Compile Phase)"]
        SymbolTable["SlotSymbolTable (Name -> Integer ID)"]
        DFSEngine["DFS Solver (Cycle Detection)"]
        Validator["Closure & Contract Validator"]
    end
    
    subgraph SlotAsyncGraphRuntime["SlotAsyncGraph (Runtime Phase)"]
        SlotsArray["slots = Object[] Array (Direct address data store)"]
        ReadyBits["readyBits = AtomicLongArray (High-speed bitset tracker)"]
        DispatcherLoop["Dispatcher Loop (Lock-free status poll)"]
    end
    
    SymbolTable -->|"1. Map symbols to integer indexes"| SlotsArray
    DFSEngine -->|"2. Check topological order"| DispatcherLoop
    Validator -->|"3. Verify dependency contracts"| ReadyBits
```

---

## 4. Graph Execution Sequence Model

This timeline details how a client service triggers a `SlotAsyncGraph` execution, how parallel steps are dispatched to isolated thread pools, how they update the atomic bitset upon completion, and how the caller thread joins and fetches the finalized output.

```mermaid
sequenceDiagram
    autonumber
    participant Caller as Caller Service Thread
    participant Graph as SlotAsyncGraph Execution Instance
    participant State as SlotState (slots & readyBits)
    participant IOExec as IO Thread Pool
    participant CPUExec as CPU Thread Pool

    Caller->>Graph: execute(context)
    activate Graph
    Graph->>State: Initialize Object[] slots & zero-out AtomicLongArray readyBits
    
    Note over Graph, State: Compile topological order indicates which steps have zero dependencies
    
    Graph->>IOExec: Submit "validateRequest" (DIRECT execution immediately)
    IOExec->>State: writeSlot(0, validatedContext) & markReady(0)
    
    Note over State: Bit 0 is marked ready. Check dependencies for downstream nodes...
    
    par Parallel Task Branch
        Graph->>IOExec: Submit "loadInventory" (Requires Slot 0)
        Graph->>IOExec: Submit "loadMemberProfile" (Requires Slot 0)
    end
    
    activate IOExec
    IOExec->>State: "loadInventory" completes. writeSlot(1, inventoryDetails) & markReady(1)
    IOExec->>State: "loadMemberProfile" completes. writeSlot(2, memberProfile) & markReady(2)
    deactivate IOExec
    
    Note over State: Bits 1 and 2 are marked ready. Trigger calculateDiscount step.
    
    Graph->>CPUExec: Submit "calculateDiscount" (Requires Slot 1 & 2)
    activate CPUExec
    CPUExec->>State: compute discount. writeSlot(3, discountContext) & markReady(3)
    deactivate CPUExec
    
    Note over State: Bit 3 ready. Terminal step "confirmBooking" ready.
    
    Graph->>Caller: execute terminal block on caller thread (DIRECT)
    Graph-->>Caller: returns fully aggregated context
    deactivate Graph
```

---

## 5. Design Rule Validation Verification

To ensure system reliability, the visual design model aligns with three architectural constraints:

1. **The Single-Writer Contract**: Multiple nodes can read from any slot in the `slots` array, but only a single designated node can write to it. This prevents thread write-collisions and keeps data immutable for readers.
2. **Context-Free Dispatching**: Dispatcher threads query the bitwise state of `readyBits` in CPU cache without obtaining standard mutex locks. This achieves massive scale-up throughput.
3. **No Unbound Queues**: Standard microservices should deploy with configured `queue-capacity` limits for the `IOExec` and `CPUExec` pools, ensuring that the backpressure boundaries shown in these diagrams are always active.
