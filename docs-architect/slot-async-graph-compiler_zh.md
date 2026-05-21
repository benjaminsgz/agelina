# SlotAsyncGraph 编译器与位掩码调度器内幕

Agelina 异步有向图编排引擎的核心设计目标，是把开发人员编写的声明式拓扑结构，在启动时静态编译成极速、无锁（Lock-Free）、且在执行热路径中无任何内存分配的整型数组寻址运行模型。

本指南将深入揭秘 `build()` 编译期的整个优化管道、符号表的转换机制、基于 `AtomicLongArray` 位图的无锁 CAS 调度状态机，以及框架如何通过三道 fail-fast 静态拓扑校验屏障确保运行期的数据安全。

---

## DAG 静态编译管道

当开发人员调用 `SlotAsyncGraphBuilder.build()` 时，框架会在启动阶段触发一套精细的多阶段编译器管道：

```
[有向图 Builder 节点定义输入]
             |
             v
1. 符号解析翻译 (Symbol Resolution)：解析符号名称，通过 SlotSymbolTable 静态影射为连续的整型数组槽位 ID。
             |
             v
2. 拓扑合法性检验：校验节点依赖的实体是否存在，利用 DFS 算法计算全局拓扑执行顺序。
             |
             v
3. 循环依赖检测：若发现节点依赖之间构成闭环，立即阻断并抛出异常。
             |
             v
4. 单写入者契约校验：校验并杜绝两个 PATCH 节点同时声明写入同一个槽位的情况。
             |
             v
5. 传递依赖闭包校验：若节点 B 要读取节点 A 发布的槽位数据，强制校验节点 A 必须是节点 B 的直接或间接前驱节点。
             |
             v
[生成完全编译好的 SlotAsyncGraph 实例]
```

---

## 符号表槽位索引翻译机制 (`SlotSymbolTable`)

传统的反应式并发图框架通常依赖并发哈希表（如 `ConcurrentHashMap`）或动态字典在节点之间传递中间计算结果。但在高吞吐场景下，频繁的哈希计算、链表/红黑树检索、扩容以及临时包装对象的分配，会成为阻碍系统压榨性能的巨大 CPU 瓶颈。

Agelina 通过引入 `SlotSymbolTable` 符号表彻底打碎了这一瓶颈。在 Builder 构建阶段，框架将所有声明的字符串符号名称（例如 `"validatedContext"`, `"inventoryDetails"`）分配给一个唯一的、从 0 开始连续递增的整型 ID：

```java
public final class SlotSymbolTable {
    private final Map<String, Integer> symbolToId = new LinkedHashMap<>();
    private final List<String> idToSymbol = new ArrayList<>();

    public synchronized int acquire(String symbol) {
        return symbolToId.computeIfAbsent(symbol, s -> {
            int id = idToSymbol.size();
            idToSymbol.add(s);
            return id;
        });
    }

    public String describe(int id) {
        return id + " (" + idToSymbol.get(id) + ")";
    }
    
    public int size() {
        return idToSymbol.size();
    }
}
```

在有向图真正运转的 hot path 中，任务读取和写入中间值仅需要进行最简单的 $O(1)$ 常数级数组整数寻址寻址操作（`slots[slotId]`），实现了零哈希开销与零字典查询成本。

---

## 无锁 Bitset 状态机调度器 (`AtomicLongArray readyBits`)

为了实现并发任务调度的无锁化（不使用任何重量级 synchronized 锁或排它锁），Agelina 在私有静态内部类 `SlotState` 中基于 `AtomicLongArray` 实现了一个极致轻量的无锁并发位图调度状态机：

```java
private static final class SlotState {
    private final Object[] slots;
    private final AtomicLongArray readyBits;

    private SlotState(int slotCount) {
        this.slots = new Object[slotCount];
        this.readyBits = new AtomicLongArray((slotCount + 63) >>> 6); // 将槽位数向上对齐，每 64 个槽占用一个 Long 元素
    }
}
```

### 1. 快速位图寻址计算
* **Long 数组字索引（Word Index）计算**：由于 Java 中一个 `long` 类型包含 64 位，因此我们可以通过无符号右移运算符 `slotId >>> 6` 极速定位槽位对应的 Long 元素索引（相当于除以 64）。
* **字内位掩码（Bit Position Mask）计算**：通过按位与运算 `1L << (slotId & 63)` 来计算该槽位在对应 Long 元素中的具体比特位遮罩（相当于 `slotId % 64`）。

### 2. 极速无锁就绪检查 (`hasSlot`)
要检查某个前置槽位是否已就绪、值是否已发布，无须查表，只需进行无锁的按位与检查：

```java
private boolean hasSlot(int slotId) {
    int wordIndex = slotId >>> 6;
    long mask = 1L << (slotId & 63);
    return (readyBits.get(wordIndex) & mask) != 0L;
}
```

### 3. 基于 CAS 的原子状态发布 (`markReady`)
当某个并发步骤执行完毕并向某个槽位发布值时，需要将其对应的比特位置为 1（标记就绪）。由于多个工作线程可能同时向同一个 Long 元素包含的不同比特位写入就绪标记，直接赋值会产生严重的写丢失覆盖。

Agelina 使用高并发下开销极低且线程安全的原子比较并交换（Compare-And-Swap）自旋循环：

```java
private void markReady(int slotId) {
    int wordIndex = slotId >>> 6;
    long mask = 1L << (slotId & 63);
    while (true) {
        long current = readyBits.get(wordIndex);
        long next = current | mask; // 将目标比特位设为 1
        if (current == next || readyBits.compareAndSet(wordIndex, current, next)) {
            return;
        }
    }
}
```

---

## 编译期 fail-fast 静态拓扑校验

为保证有向无环图在运行期绝不会因为死锁、数据依赖缺失或竞态条件在中途崩塌，Agelina 在 Builder 静态编译期设置了三道严密的拓扑强校验防线：

### 1. 深度优先循环依赖检测 (DFS Cycle Detection)
Agelina 对所有已注册的节点进行深度优先搜索（DFS）拓扑排序。它使用三色标记法对节点状态进行管理（未访问、访问中、已访问）。一旦在递归遍历某条依赖链时撞上处于“访问中（VISITING）”的节点，说明拓扑结构中存在循环环路，编译器将立即输出环路路径并抛出致命异常：

```
发现拓扑环路: [validateRequest] -> [loadInventory] -> [loadMember] -> [validateRequest]
```

### 2. 单写入者契约校验 (`validateSlotContracts`)
Agelina 强制约束每个槽位数据的单一职责原则。在图编译阶段，编译器会对所有中间步骤（`PATCH` 节点）声明的所有输出槽位进行全局唯一性校验：

```java
for (SlotAsyncGraphNodeDefinition<C> definition : definitions.values()) {
    for (int slotId : definition.getDeclaredWriteSlots()) {
        String existing = producerBySlot.putIfAbsent(slotId, definition.getName());
        if (existing != null) {
            throw new IllegalStateException(
                "槽位写入冲突：" + symbolTable.describe(slotId)
                + "。已存在的写入节点：" + existing + "，新的写入节点：" + definition.getName()
            );
        }
    }
}
```

若存在两个不同的步骤试图发布同一个槽位数据，图编译阶段将当场熔断阻断启动。这杜绝了共享数据在运行期可能发生的多线程写覆盖或脏读竞态。

### 3. 传递依赖闭包检验 (`validateReadSlotDependencyClosure`)
这是 Agelina 最核心的安全控制特性。如果节点 B 声明读取槽位 X，而槽位 X 是由节点 A 负责写入发布的，编译器将强制校验：**节点 A 必须是节点 B 的直接或间接前驱节点（即存在一条 A 到 B 的显式 node 依赖链路）**。

```
合法拓扑依赖链路:
节点 A (负责写入 X) <--- 节点 B (声明依赖 A, 并安全读取槽位 X)

非法的未申明依赖链路:
节点 A (负责写入 X)  \
                     ---> 节点 C (没有显式声明依赖 A, 却试图读取槽位 X)
节点 B (Dummy 节点)  /
```

如果允许节点 C 不显式依赖节点 A 而去强行读取槽位 X，调度器为了获取 X 就必须在运行期对 C 进行挂起并轮询等待 A 的完成，这会破坏无锁状态机的极速调度机制。通过这道闭包检验，Agelina 确保了**当任何一个任务开始在工作线程中拉起执行时，它声明读取的所有前置槽位中的值已经百分之百存放在内存中**，保证图的无阻塞极速流转。
