# 使用 SlotAsyncGraph 构建并行依赖拓扑业务流

`SlotAsyncGraph` 是 Agelina 中自主研发的高性能有向无环图 (DAG) 异步执行引擎。它专门针对复杂的并行核心业务设计：多个关联任务需要根据明确的数据依赖关系并发执行，并在最终汇聚节点组装出完整的响应结果。

## 为什么选择 SlotAsyncGraph？

在传统的 DAG 并行执行引擎中，多任务之间的中间值传递通常依赖 `ConcurrentHashMap`，或者通过承载了各种 Map 读写的共享上下文。这种做法在频繁调度的性能热路径上会引入明显的 CPU 查表开销、哈希冲突以及锁开销。

`SlotAsyncGraph` 采用了一种革新的**基于插槽的数据编译机制 (Slot-Based Data Compilation)**：
* **符号式开发体验**：开发者在编写代码时，使用直观的、具有明确业务含义的符号槽名（如 `"memberLevel"`，`"validatedRequest"`）。
* **编译期计算与降维**：在调用 `build()` 方法时，构建器会对整个图进行静态分析，将所有符号槽编译并固定为一个数组：`Object[] slots`，并将每个符号解析为唯一的数组整数索引（Slot ID）。
* **无锁极速运行时**：在运行时，任务读取数据仅进行极速的数组整数寻址 (`slots[slotId]`)。状态就绪度使用基于位图的 `AtomicLongArray`（`readyBits`）进行位运算检查。这在整个执行热路径上实现了完全的无锁、无哈希查表设计。
* **启动期 Fail-Fast 校验**：图的拓扑闭包、循环依赖、单写者限制等均在系统启动期完成静态校验，极大地提升了系统的运行期稳定性。

---

## SlotAsyncGraphBuilder 的核心步骤配置

构建一个 DAG 时，系统支持配置以下三种类型的步骤：

### 1. 单插槽步骤 (Single-Slot Step)
使用 `addSlotStep` 产生一个单一的中间输出值。它需要声明依赖的前置节点以及需要读取的数据插槽。

```java
builder.addSlotStep(
    "loadInventory",                       // 节点唯一名称
    List.of("validateRequest"),            // 依赖的前置节点名称
    ExecutionMode.IO,                      // 调度执行模式
    List.of("validatedContext"),           // 需要读取的插槽符号
    "availableStock",                      // 产生的写入插槽符号
    view -> inventoryGateway.getAvailableStock(
        view.slotAs("validatedContext", QuoteContext.class).getSku()
    )
);
```

### 2. 多插槽符号补丁步骤 (Multi-Slot Step)
一个步骤可能会产生多个中间输出值（例如调用一个微服务，该服务同时返回商品的名称、单价以及基础总金额）。此时，您需要使用 `addSymbolicPatchStep` 并返回 `SymbolicSlotPatch` 来实现对多个插槽的安全原子写入：

```java
builder.addSymbolicPatchStep(
    "loadProductInfo",
    List.of("validateRequest"),
    ExecutionMode.IO,
    List.of("validatedContext"),
    List.of("productName", "unitPrice", "baseAmount"),
    view -> {
        QuoteContext ctx = view.slotAs("validatedContext", QuoteContext.class);
        ProductInfo info = productGateway.getBySku(ctx.getSku());
        BigDecimal base = info.getUnitPrice().multiply(BigDecimal.valueOf(ctx.getQuantity()));
        
        return SymbolicSlotPatch.from(
            new String[]{"productName", "unitPrice", "baseAmount"},
            new Object[]{info.getProductName(), info.getUnitPrice(), base}
        );
    }
);
```

### 3. 终结汇聚步骤 (Terminal Step)
每个有向无环图有且仅能包含一个终结步骤（通过 `addTerminalStep` 配置）。该步骤负责读取之前所有并行的中间结果插槽，并组装返回最终的业务响应对象。

```java
builder.addTerminalStep(
    "buildQuote",
    List.of("loadInventory", "loadProductInfo"), // 保证这些节点执行完毕
    ExecutionMode.DIRECT,
    List.of("validatedContext", "availableStock", "baseAmount"), // 声明需要读取的插槽
    this::assembleFinalQuote
);
```

---

## 完整代码范例：报价预览 DAG 编排

以下代码展示了如何组装一个报价预览的 DAG 并行编排流。在该流程中，校验请求、查询库存、查询会员画像和查询商品目录三个 IO 密集型操作将完全并行触发：

```java
@Service
public class QuoteDAGService {

    private final AsyncStepFactory stepFactory;
    private final InventoryGateway inventoryGateway;
    private final MemberProfileGateway memberProfileGateway;
    private final ProductCatalogGateway productCatalogGateway;

    public QuoteDAGService(
            AsyncStepFactory stepFactory,
            InventoryGateway inventoryGateway,
            MemberProfileGateway memberProfileGateway,
            ProductCatalogGateway productCatalogGateway
    ) {
        this.stepFactory = stepFactory;
        this.inventoryGateway = inventoryGateway;
        this.memberProfileGateway = memberProfileGateway;
        this.productCatalogGateway = productCatalogGateway;
    }

    public SlotAsyncGraph<QuoteContext> buildQuoteGraph() {
        return new SlotAsyncGraphBuilder<QuoteContext>(stepFactory)
            // 1. 同步校验入参 (在当前主线程直接同步执行)
            .addSlotStep("validateRequest", List.of(), ExecutionMode.DIRECT, List.of(), "validatedContext",
                view -> validate(view.context()))
            
            // 2. 并发拉取库存 (在独立 IO 线程池并发执行)
            .addSlotStep("loadInventory", List.of("validateRequest"), ExecutionMode.IO,
                List.of("validatedContext"), "availableStock",
                view -> inventoryGateway.getAvailableStock(
                    view.slotAs("validatedContext", QuoteContext.class).getSku()
                ))
            
            // 3. 并发拉取会员等级 (在独立 IO 线程池并发执行)
            .addSlotStep("loadMemberProfile", List.of("validateRequest"), ExecutionMode.IO,
                List.of("validatedContext"), "memberLevel",
                view -> memberProfileGateway.getByUserId(
                    view.slotAs("validatedContext", QuoteContext.class).getUserId()
                ).getLevel())
            
            // 4. 并发拉取商品详情及计算基础总价 (多插槽原子写入)
            .addSymbolicPatchStep("loadProductAndBaseAmount", List.of("validateRequest"), ExecutionMode.IO,
                List.of("validatedContext"),
                List.of("productName", "unitPrice", "baseAmount"),
                view -> {
                    QuoteContext c = view.slotAs("validatedContext", QuoteContext.class);
                    ProductInfo p = productCatalogGateway.getBySku(c.getSku());
                    BigDecimal baseAmount = p.getUnitPrice().multiply(BigDecimal.valueOf(c.getQuantity()));
                    return SymbolicSlotPatch.from(
                        new String[]{"productName", "unitPrice", "baseAmount"},
                        new Object[]{p.getProductName(), p.getUnitPrice(), baseAmount}
                    );
                })
            
            // 5. 计算会员折扣 (依赖商品基础价格和会员等级，并在 CPU 密集型线程池执行)
            .addSlotStep("calculateMemberDiscount", List.of("loadProductAndBaseAmount", "loadMemberProfile"),
                ExecutionMode.CPU,
                List.of("baseAmount", "memberLevel"),
                "memberDiscount",
                view -> {
                    BigDecimal base = view.slotAs("baseAmount", BigDecimal.class);
                    Integer level = view.slotAs("memberLevel", Integer.class);
                    return base.multiply(BigDecimal.valueOf(level * 0.05)); // 每级会员打 95 折
                })
            
            // 6. 终结汇聚：组装返回最终报价 (在最后一个完成任务的线程中 DIRECT 即时拼装)
            .addTerminalStep("buildQuote",
                List.of(
                    "validateRequest",
                    "loadInventory",
                    "loadMemberProfile",
                    "loadProductAndBaseAmount",
                    "calculateMemberDiscount"
                ),
                ExecutionMode.DIRECT,
                List.of(
                    "validatedContext", "availableStock", "memberLevel",
                    "productName", "unitPrice", "baseAmount", "memberDiscount"
                ),
                view -> {
                    QuoteContext initial = view.slotAs("validatedContext", QuoteContext.class);
                    int stock = view.slotAs("availableStock", Integer.class);
                    BigDecimal base = view.slotAs("baseAmount", BigDecimal.class);
                    BigDecimal disc = view.slotAs("memberDiscount", BigDecimal.class);
                    
                    initial.setStockStatus(stock > 0 ? "IN_STOCK" : "OUT_OF_STOCK");
                    initial.setFinalAmount(base.subtract(disc));
                    return initial;
                })
            .build();
    }

    private QuoteContext validate(QuoteContext context) {
        if (context.getSku() == null || context.getSku().isBlank()) {
            throw new IllegalArgumentException("SKU 编码不能为空");
        }
        return context;
    }
}
```

---

## 最佳实践与避坑指南

* **推荐使用符号自动分配**：初始化 `SlotAsyncGraphBuilder` 时，推荐使用 `new SlotAsyncGraphBuilder<>(stepFactory)`。这会由构建器动态自动映射唯一的 Slot ID，有效避免因手动指定 Slot 数组索引导致的下标冲突问题。
* **DIRECT 模式严禁阻塞**：仅将 `DIRECT`（即时执行）模式分配给极轻量且绝无外部 IO 依赖的任务（如纯入参判空、内存数据字段拼装等）。若任务包含任何 RPC、数据库查表、本地文件读取等，必须强制声明为 `ExecutionMode.IO`，否则会直接卡死引擎底层的任务调度线程。
* **避免多写者并发冲突**：系统强制执行单写者约束。如果尝试配置两个节点向同一个槽名写入数据，在调用 `build()` 时构建器将直接抛出异常，防止运行期发生严重的数据覆盖与竞态死锁。
