# Building Parallel Workflows with SlotAsyncGraph

`SlotAsyncGraph` is a high-performance Directed Acyclic Graph (DAG) execution engine in Agelina. It is designed for complex, concurrent workflows where multiple tasks run in parallel based on explicit data dependencies, converging eventually to build a final response.

## Why SlotAsyncGraph?

In traditional DAG engines, intermediate values are shared using `ConcurrentHashMap` or context objects containing generic map lookups. This introduces significant lookup and locking overhead on hot execution paths.

`SlotAsyncGraph` solves this by introducing **Slot-Based Data Compilation**:
* **Symbolic Authoring**: Developers define steps using clean, symbolic slot names (e.g., `"memberLevel"`, `"validatedRequest"`).
* **Compile-Time Sizing**: During `build()`, symbolic names are resolved and compiled to a fixed-size `Object[] slots` array, assigning each symbol a unique integer index (slot ID).
* **Lock-Free Runtime**: During runtime, tasks access data via integer-indexed lookups (`slots[slotId]`) and publish status updates using bit-mask operations on an `AtomicLongArray` (`readyBits`). This is completely lock-free and map-lookup-free.
* **Fail-Fast Validation**: Topologies, single-writer contracts, cycle detection, and dependency paths are validated before running.

---

## Core Operations of SlotAsyncGraphBuilder

When building a graph, you can configure three types of steps:

### 1. Single-Slot Steps
An `addSlotStep` produces a single output slot. It declares its dependencies (nodes that must complete first) and read slots (data slots it needs to read from).

```java
builder.addSlotStep(
    "loadInventory",                       // Unique Node Name
    List.of("validateRequest"),            // Node Dependencies
    ExecutionMode.IO,                      // Schedular Mode
    List.of("validatedContext"),           // Read Slot Symbols
    "availableStock",                      // Write Slot Symbol
    view -> inventoryGateway.getAvailableStock(
        view.slotAs("validatedContext", QuoteContext.class).getSku()
    )
);
```

### 2. Multi-Slot Symbolic Patch Steps
A step may generate multiple outputs (e.g., calling a microservice that returns a product's name, unit price, and base amount). Use `addSymbolicPatchStep` and return a `SymbolicSlotPatch` to write to multiple slots:

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

### 3. Terminal Steps
Every graph requires exactly one terminal step configured via `addTerminalStep`. This step reads all necessary slots and returns the final consolidated context.

```java
builder.addTerminalStep(
    "buildQuote",
    List.of("loadInventory", "loadProductInfo"), // Wait for these nodes
    ExecutionMode.DIRECT,
    List.of("validatedContext", "availableStock", "baseAmount"), // Read slots
    this::assembleFinalQuote
);
```

---

## Complete Example: Quote Preview Workflow

The following code illustrates how to set up the quote preview DAG where stock checks, member profiling, and product catalog requests are fired in parallel:

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
            // 1. Validate request (runs on direct caller thread)
            .addSlotStep("validateRequest", List.of(), ExecutionMode.DIRECT, List.of(), "validatedContext",
                view -> validate(view.context()))
            
            // 2. Fetch inventory (runs in parallel IO pool)
            .addSlotStep("loadInventory", List.of("validateRequest"), ExecutionMode.IO,
                List.of("validatedContext"), "availableStock",
                view -> inventoryGateway.getAvailableStock(
                    view.slotAs("validatedContext", QuoteContext.class).getSku()
                ))
            
            // 3. Fetch member tier (runs in parallel IO pool)
            .addSlotStep("loadMemberProfile", List.of("validateRequest"), ExecutionMode.IO,
                List.of("validatedContext"), "memberLevel",
                view -> memberProfileGateway.getByUserId(
                    view.slotAs("validatedContext", QuoteContext.class).getUserId()
                ).getLevel())
            
            // 4. Fetch product catalog & multiply base price (multi-write)
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
            
            // 5. Calculate member discount (runs in CPU pool, depends on loaded values)
            .addSlotStep("calculateMemberDiscount", List.of("loadProductAndBaseAmount", "loadMemberProfile"),
                ExecutionMode.CPU,
                List.of("baseAmount", "memberLevel"),
                "memberDiscount",
                view -> {
                    BigDecimal base = view.slotAs("baseAmount", BigDecimal.class);
                    Integer level = view.slotAs("memberLevel", Integer.class);
                    return base.multiply(BigDecimal.valueOf(level * 0.05)); // 5% discount per level
                })
            
            // 6. Terminal consolidation
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
            throw new IllegalArgumentException("SKU is required");
        }
        return context;
    }
}
```

---

## Developer Best Practices

* **Prefer Symbolic Allocation**: Initialize `SlotAsyncGraphBuilder` using `new SlotAsyncGraphBuilder<>(stepFactory)`. Let the builder dynamically allocate slot IDs to avoid manual integer collision problems.
* **Keep Direct Steps Fast**: Only declare `ExecutionMode.DIRECT` for quick operations such as validation or terminal response assembly. Any operation involving I/O must run on `ExecutionMode.IO` to prevent blocking the dispatcher threads.
* **Avoid Multi-Writer Configurations**: The builder will throw an exception if multiple nodes attempt to write to the same slot symbol, protecting the system from race conditions.
