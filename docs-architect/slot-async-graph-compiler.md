# SlotAsyncGraph Compiler and Bitset Dispatcher Internals

The core execution engine of Agelina relies on translating a high-level developer-declared topology into an extremely fast, lock-free, allocation-free execution model. 

This guide details the internals of the compile phase (`build()`), how symbolic names are resolved, the lock-free `AtomicLongArray` bitset state machine, and the fail-fast validations that guarantee topological safety before graph boot time.

---

## The Compilation Pipeline

When a developer calls `.build()` on a `SlotAsyncGraphBuilder`, the framework runs a multi-phase compiler pipeline:

```
[Builder Declaration]
         |
         v
1. Symbol Resolution: Translate slot strings to continuous integer array indexes via SlotSymbolTable.
         |
         v
2. Topology Validation: Verify node references, build topological sorting via DFS.
         |
         v
3. Cycle Detection: Throw if any cycles exist in node dependencies.
         |
         v
4. Single-Writer Contract Check: Throw if two PATCH nodes declare the same write slot.
         |
         v
5. Transitive Closure Check: Throw if Node B reads Slot X from Node A without depending on Node A.
         |
         v
[Executable SlotAsyncGraph Compiled]
```

---

## Symbol Table Resolution (`SlotSymbolTable`)

Traditional reactive graphs pass intermediate data downstream through hash maps or dynamic concurrent dictionaries. At high throughputs, map hashing, bucket lookups, and resizing allocations become a major CPU bottleneck.

Agelina bypasses this by introducing the `SlotSymbolTable`. During building, the builder maps every declared symbolic slot name (e.g., `"validatedContext"`, `"inventoryDetails"`) to a sequential, zero-indexed integer:

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

During graph execution, reading and writing values is a simple direct array indexing operation (`slots[slotId]`), which is executed in $O(1)$ time with absolute zero lookup overhead.

---

## Lock-Free Bitset State Machine (`AtomicLongArray readyBits`)

To orchestrate parallel execution without a single lock or synchronized block, Agelina implements a highly optimized lock-free bitset inside the private `SlotState` class using a thread-safe `AtomicLongArray`:

```java
private static final class SlotState {
    private final Object[] slots;
    private final AtomicLongArray readyBits;

    private SlotState(int slotCount) {
        this.slots = new Object[slotCount];
        this.readyBits = new AtomicLongArray((slotCount + 63) >>> 6); // Sized in 64-bit words
    }
}
```

### 1. Bitmask Address Calculations
* **Word Index Lookup**: Because each `long` in Java is 64 bits, we calculate the target word index using an unsigned bit shift: `slotId >>> 6` (equivalent to dividing by 64).
* **Bit Position Mask**: We determine the exact bit mask inside that word using the modulo mask: `1L << (slotId & 63)` (equivalent to `slotId % 64`).

### 2. Lock-Free Verification (`hasSlot`)
To check if a dependency slot is published and ready to read, we run a lock-free bitwise AND check:

```java
private boolean hasSlot(int slotId) {
    int wordIndex = slotId >>> 6;
    long mask = 1L << (slotId & 63);
    return (readyBits.get(wordIndex) & mask) != 0L;
}
```

### 3. Atomic CAS Publication (`markReady`)
When a node finishes executing and publishes a new value, we must mark that slot as ready. Because multiple worker threads can write to different slots belonging to the same word concurrently, a standard write is subject to lost updates.

Agelina handles this using an atomic Compare-And-Swap (CAS) loop:

```java
private void markReady(int slotId) {
    int wordIndex = slotId >>> 6;
    long mask = 1L << (slotId & 63);
    while (true) {
        long current = readyBits.get(wordIndex);
        long next = current | mask; // Set target bit to 1
        if (current == next || readyBits.compareAndSet(wordIndex, current, next)) {
            return;
        }
    }
}
```

---

## Fail-Fast Topology Validations

To guarantee that a graph will never fail mid-execution due to data races, missing variables, or infinite loops, Agelina enforces three strict static topology checks at boot time.

### 1. Cycle Detection (DFS Path Tracking)
Agelina performs a Deep First Search (DFS) topological sort of all nodes. It maintains a three-state visitation register (Unvisited, Visiting, Visited). If it hits a node marked as `VISITING` during recursion, it immediately isolates the circular dependency path and throws a descriptive cycle error:

```
[validateRequest] -> [loadInventory] -> [loadMember] -> [validateRequest] (Cycle Detected)
```

### 2. Single-Writer Contract Validation (`validateSlotContracts`)
Agelina enforces single-responsibility for all data slots. During compilation, the compiler parses the output slots of all intermediate `PATCH` nodes:

```java
for (SlotAsyncGraphNodeDefinition<C> definition : definitions.values()) {
    for (int slotId : definition.getDeclaredWriteSlots()) {
        String existing = producerBySlot.putIfAbsent(slotId, definition.getName());
        if (existing != null) {
            throw new IllegalStateException(
                "Slot write collision on " + symbolTable.describe(slotId)
                + ". Existing writer=" + existing + ", new writer=" + definition.getName()
            );
        }
    }
}
```

If two different nodes declare that they write to the same slot, compilation aborts instantly. This guarantees there are no write-collisions or race conditions on shared data.

### 3. Transitive Dependency Closure Validation (`validateReadSlotDependencyClosure`)
This is the most critical safety feature. If Node B reads Slot X, and Slot X is produced by Node A, the compiler strictly enforces that **Node A must be a transitive ancestor of Node B**. 

```
Correct Topology:
Node A (Writes X) <--- Node B (Depends on A, reads X)

Illegal Topology:
Node A (Writes X)  \
                    ---> Node C (No dependency on A, attempts to read X)
Node B (Dummy)     /
```

If Node C attempted to read Slot X without explicitly depending on Node A, the framework would have to perform blocking checks or wait on data publication, breaking the lock-free architecture. By enforcing this closure validation, Agelina guarantees that by the time a task starts, all of its input slots are already populated and fully available in memory.
