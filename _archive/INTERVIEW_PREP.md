# Java & Spring Boot Interview Prep

> Topics sourced from real interview experience (Mastercard SDE III).  
> Format: Q → Concept → Follow-ups → Answers

---

## 📦 SECTION 1: Core Java Deep Dive

---

### Q1. What does "effectively final" really mean?

#### ✅ Core Answer

A variable is **effectively final** if it is **never reassigned after its initial assignment** — even without the `final` keyword.

Introduced in **Java 8** alongside lambdas.

```java
// Explicitly final — always worked
final int x = 10;
Runnable r1 = () -> System.out.println(x); // ✅

// Effectively final — Java 8+
int y = 20; // never reassigned → effectively final
Runnable r2 = () -> System.out.println(y); // ✅

// NOT effectively final → compile error
int z = 30;
z = 40; // reassigned!
Runnable r3 = () -> System.out.println(z); // ❌ COMPILE ERROR
```

---

#### 🧠 Why does this rule exist?

Lambda expressions and anonymous inner classes **capture local variables by VALUE** (a copy), not by reference.

If the variable could change after being captured, the lambda would be holding a **stale copy** — leading to unpredictable bugs.

Java enforces this at compile time to prevent that inconsistency.

```
Stack Frame:
  int y = 20   ←── lambda captures a COPY of y (value = 20)
  
  Later: y = 99  ← stack changes, but lambda still holds 20
                    → data inconsistency → Java forbids this
```

---

#### 🔍 Follow-up Q&As

**Q: What is the difference between `final` and `effectively final`?**

| | `final` | Effectively Final |
|---|---|---|
| Keyword required | ✅ Yes | ❌ No |
| Reassignment | Compile error | Never done by programmer |
| Used in lambda | ✅ | ✅ |
| Introduced | Java 1.0 | Java 8 |

Both are treated the **same** by the compiler when used in lambdas.

---

**Q: Does this rule apply to instance variables?**

**No.** This rule only applies to **local variables** (variables on the stack).  
Instance variables (fields) are on the **heap** and are accessible via `this` reference in lambdas — no copy is made.

```java
class Demo {
    int counter = 0; // instance variable

    Runnable r = () -> System.out.println(counter); // ✅ — no restriction
    // counter can change freely, lambda always reads from heap
}
```

---

**Q: Can method parameters be effectively final?**

**Yes.** Method parameters follow the same rule.

```java
void process(int value) {   // 'value' is a parameter
    Runnable r = () -> System.out.println(value); // ✅ if never reassigned
    // value = 99; ← this would break effectively-final guarantee
}
```

---

**Q: What about arrays or objects — can their contents change?**

**Yes, they can.** Effectively final applies to the **reference**, not the object's state.

```java
int[] arr = {1, 2, 3};
Runnable r = () -> System.out.println(arr[0]); // ✅ — reference is final
arr[0] = 99; // ✅ allowed — content change, not reference change
arr = new int[]{4, 5}; // ❌ — this would break effectively-final
```

This is a common **gotcha** — you can mutate captured objects/arrays through lambdas.

---

#### 🎯 One-liner for Interview

> *"Effectively final means the variable is never reassigned after its first assignment. Java 8 introduced this so lambdas can safely capture local variables by value — ensuring the captured copy stays consistent with the original."*

---

### Q2. Explain Garbage Collection Internals — Minor GC, Major GC, Full GC

#### ✅ Core Concept: Heap Structure First

Before understanding GC types, you must know how the **heap is structured**:

```
┌─────────────────────────────────────────────────────────────┐
│                         HEAP                                │
│                                                             │
│  ┌──────────────────────────────┐   ┌─────────────────────┐│
│  │        Young Generation      │   │   Old Generation    ││
│  │                              │   │   (Tenured Space)   ││
│  │  ┌────────┐ ┌────┐ ┌────┐   │   │                     ││
│  │  │  Eden  │ │ S0 │ │ S1 │   │   │  Long-lived objects ││
│  │  │(new obj│ │    │ │    │   │   │                     ││
│  │  │ born   │ │Surv│ │Surv│   │   │                     ││
│  │  │  here) │ │ivor│ │ivor│   │   │                     ││
│  │  └────────┘ └────┘ └────┘   │   │                     ││
│  └──────────────────────────────┘   └─────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

- **Eden**: Where ALL new objects are born
- **S0 / S1 (Survivor spaces)**: Objects that survive one GC cycle
- **Old Gen (Tenured)**: Objects that have survived multiple GC cycles

---

#### 🟡 Minor GC — Young Generation Cleanup

**Triggered when**: Eden space fills up.

**What happens**:
1. All live objects in Eden are copied to one Survivor space (say S0)
2. Objects already in S0 (from previous Minor GC) are copied to S1 (or promoted to Old Gen if old enough)
3. Eden and the old Survivor space are completely **wiped**

```
Before Minor GC:
  Eden: [A][B][C][D]   S0: [X]   S1: empty   Old Gen: [Z]

After Minor GC (A, C are dead):
  Eden: empty          S0: empty  S1: [B][D][X]   Old Gen: [Z]
```

**Key traits**:
- Very **fast** (milliseconds)
- Uses **Stop-The-World (STW)** — but very brief
- Happens **frequently**

---

#### 🔴 Major GC — Old Generation Cleanup

**Triggered when**: Old Gen fills up.

**What happens**:
- GC scans and cleans **only the Old Generation**
- Objects with no live references are removed

**Key traits**:
- Much **slower** than Minor GC (Old Gen is larger)
- Longer **Stop-The-World** pause
- Less frequent than Minor GC

> ⚠️ Note: "Major GC" is not a formal JVM term — it's informal. The JVM spec only defines Minor GC and Full GC.

---

#### 🔵 Full GC — Entire Heap Cleanup

**Triggered when**:
- Old Gen is nearly full and promotion fails
- `System.gc()` is called (hint, not a guarantee)
- Metaspace (class metadata) fills up
- GC algorithm decides it's needed

**What happens**:
- Cleans **entire heap**: Young Gen + Old Gen + Metaspace
- Also compacts memory (reduces fragmentation)

**Key traits**:
- **Slowest** — can take seconds on large heaps
- Longest **Stop-The-World** pause
- Should be **minimized** in production

---

#### 🔄 Object Lifecycle / Promotion Flow

```
new Object()
    ↓
  Eden (born)
    ↓ [Minor GC — survives]
  Survivor S0
    ↓ [Minor GC — survives again, age=2]
  Survivor S1
    ↓ [survives N times (default age threshold = 15)]
  Old Generation (promoted)
    ↓ [Major/Full GC eventually]
  Collected
```

The **age threshold** is controlled by `-XX:MaxTenuringThreshold` (default 15).

---

#### 🔍 Follow-up Q&As

**Q: What is Stop-The-World (STW)?**

All application threads are **paused** while GC runs. This is necessary because GC needs a consistent snapshot of object references — if threads kept modifying objects, GC could miss live objects or misidentify dead ones.

---

**Q: What is object promotion?**

When an object survives enough Minor GCs (reaches the tenuring threshold), it gets **promoted** (moved) from Young Gen to Old Gen.

Also, if an object is **too large** to fit in Eden, it's allocated directly in Old Gen — called **direct allocation**.

---

**Q: Why is Minor GC fast if it also does STW?**

Because:
1. Young Gen is **small** relative to total heap
2. Most objects die young (**Weak Generational Hypothesis**) — very few objects survive, so very little copying work is needed
3. The algorithm (copy-collect) is efficient for sparse live data

---

**Q: What is the Weak Generational Hypothesis?**

> *"Most objects die young."*

This is the core insight behind generational GC. In typical applications, most objects (temp variables, request-scoped objects, etc.) become garbage very quickly. Only a few objects live long (caches, singleton services, etc.).

Generational GC exploits this by cleaning Young Gen frequently (cheap) and Old Gen rarely (expensive).

---

#### 🎯 One-liner for Interview

> *"Minor GC cleans Young Gen — fast and frequent. Major GC cleans Old Gen — slower, less frequent. Full GC cleans the entire heap including Metaspace — slowest, should be rare. All three use Stop-The-World pauses, with Full GC causing the longest."*

---

### Q3. Types of GC Algorithms — Serial, Parallel, CMS, G1

> All GC algorithms do the same job (reclaim memory), but differ in **how they balance throughput vs. pause time vs. CPU usage**.

---

#### The Core Trade-off

```
        Low Pause Time  ←────────────────→  High Throughput
              │                                    │
         CMS / G1                          Serial / Parallel
     (concurrent, less STW)              (stop-the-world, batch)
```

---

#### 🔵 Serial GC (`-XX:+UseSerialGC`)

- **Single-threaded** — uses only 1 CPU core for GC
- Completely **Stop-The-World** for both Minor and Full GC
- Simple, low overhead

```
App Threads:  ████████░░░░░░░░░████████
GC Thread:             ████████        ← one thread doing all the work
```

**When to use**: Small apps, single-core machines, microcontainers with tiny heaps  
**Avoid**: Multi-core servers, latency-sensitive apps

---

#### 🟡 Parallel GC (`-XX:+UseParallelGC`) — Default before Java 9

- **Multi-threaded GC** — uses multiple CPU cores in parallel
- Still **Stop-The-World**, but STW pause is shorter due to parallel threads
- Optimizes for **throughput** (max work done per unit time)

```
App Threads:  ████████░░░░░████████
GC Threads:           ████           ← multiple threads working together
                       ████
                        ████
```

**When to use**: Batch processing, background jobs, apps where throughput > latency  
**Avoid**: Real-time/interactive systems needing consistent low latency

---

#### 🟠 CMS — Concurrent Mark Sweep (`-XX:+UseConcMarkSweepGC`) — Deprecated in Java 14

- **Concurrent** — most GC work runs **alongside app threads** (no STW for most of it)
- Only has STW for 2 brief phases: **Initial Mark** and **Remark**
- Does **NOT compact** memory → can cause fragmentation over time

```
App Threads:  ████████████████████████████
GC Thread:         ░░░░░░░░░░░░░░░         ← runs concurrently
              │STW│              │STW│      ← only 2 brief pauses
```

**Phases**:
1. **Initial Mark** (STW) — find GC roots
2. **Concurrent Mark** — trace live objects (concurrent)
3. **Remark** (STW) — catch any objects missed during concurrent phase
4. **Concurrent Sweep** — reclaim dead objects (concurrent)

**Problem**: No compaction → memory fragmentation → eventually triggers Full GC anyway  
**Deprecated** in Java 9, **removed** in Java 14

---

#### 🟢 G1 GC (`-XX:+UseG1GC`) — Default since Java 9

- **"Garbage First"** — designed to replace CMS
- Divides heap into **equal-sized regions** (not fixed Young/Old areas)
- Collects regions with **most garbage first** (hence the name)
- **Concurrent + compacting** — avoids fragmentation problem of CMS

```
Heap divided into regions:
┌───┬───┬───┬───┬───┬───┬───┬───┐
│ E │ S │ O │ E │ O │ H │ E │ S │   E=Eden, S=Survivor, O=Old, H=Humongous
└───┴───┴───┴───┴───┴───┴───┴───┘
        ↑ G1 picks the regions with most garbage to collect first
```

**Key feature**: You set a **pause time goal** and G1 tries to meet it:
```
-XX:MaxGCPauseMillis=200   ← tell G1: "keep pauses under 200ms"
```

**When to use**: Large heaps (>4GB), latency-sensitive apps, most modern production apps  
**Default**: Java 9 onwards

---

#### 🔍 Follow-up Q&As

**Q: What is a "Humongous" object in G1?**

Objects larger than **50% of a G1 region size** are called Humongous objects. They get their own dedicated region(s) and bypass the normal Eden → Survivor → Old flow. Lots of humongous objects can hurt G1 performance.

---

**Q: What GC would you use for a low-latency trading system?**

**ZGC** (`-XX:+UseZGC`, Java 15+) or **Shenandoah** — both offer sub-millisecond pauses regardless of heap size. G1 is also acceptable if pause goal is tuned well. Never Parallel GC.

---

**Q: Serial vs Parallel — what's the actual difference?**

Both are Stop-The-World. Serial uses **1 GC thread**. Parallel uses **N GC threads** (N = number of CPU cores by default). Parallel is faster during the pause but uses more CPU.

---

#### 📊 Quick Comparison Table

| GC | Threads | STW | Compacts | Best For |
|---|---|---|---|---|
| Serial | 1 | Full | ✅ | Small/single-core |
| Parallel | Many | Full | ✅ | High throughput, batch |
| CMS | Mixed | Partial | ❌ | Low pause (deprecated) |
| G1 | Mixed | Partial | ✅ | Large heap, balanced |
| ZGC | Mixed | Near-zero | ✅ | Ultra low-latency |

---

#### 🎯 One-liner for Interview

> *"Serial uses one thread and fully pauses. Parallel uses many threads but still fully pauses — for throughput. CMS runs concurrently to reduce pauses but doesn't compact. G1, the modern default, divides the heap into regions and collects the most garbage-heavy ones first — balancing throughput and low pause time with compaction."*

---



#### Deep Dive: ZGC vs Shenandoah

Both solve the same problem: G1 still pauses 100-200ms on large heaps - too slow for real-time systems.

**ZGC** (Oracle, Java 15+):
- Uses **Colored Pointers** - stores GC metadata inside the pointer bits itself (no extra memory per object)
- Load barrier fires on every reference read - self-heals stale pointers transparently
- Handles heaps from 8MB to 16TB, pauses < 1ms

**Shenandoah** (Red Hat, Java 15+):
- Uses **Brooks Forwarding Pointers** - adds an extra word inside every object pointing to its new location when moved
- Read + Write barriers redirect app threads to moved objects transparently
- +8 bytes memory overhead per object

| | ZGC | Shenandoah |
|---|---|---|
| By | Oracle | Red Hat |
| Technique | Colored pointers | Forwarding pointers |
| Memory overhead | None per object | +8 bytes per object |
| Best for | Very large heaps (>16GB) | Red Hat / OpenShift |

Who decides which GC to use? The developer/DevOps team via JVM flags at startup. Java defaults to G1 on server-class machines since Java 9.

---

#### Deep Dive: How Objects Are Actually Destroyed

GC never "deletes" objects one by one. It tracks the LIVING and bulk-reclaims the dead.

**Young Gen (Copy Collector):**
- Mark  -> find live objects
- Copy  -> move live objects to Survivor space
- Wipe  -> entire Eden region bulk-cleared in ONE operation. Dead objects are never explicitly deleted.

**Old Gen (Mark-Sweep-Compact):**
- Mark    -> identify live objects
- Sweep   -> flag dead objects' memory slots as "available" (NOT zeroed out)
- Compact -> slide live objects together, eliminate fragmentation gaps

**When are bytes actually overwritten?**
When a new object is allocated into those freed memory slots - at ALLOCATION TIME, not at GC time.

finalize() existed for pre-destruction cleanup but was deprecated in Java 9, removed in Java 18. Modern replacement: Cleaner API or try-with-resources.

---

#### Deep Dive: GC Roots and Tri-Color Marking

**GC Roots** (always considered alive by definition):
- Active thread stack variables (local variables in running methods)
- Static fields
- JNI references (native code)
- Active Thread objects
- Class objects in Metaspace
- Synchronized monitor locks

**Tri-Color Marking Algorithm:**
- WHITE = Not yet visited (assumed dead)
- GREY  = Discovered, children not yet scanned
- BLACK = Fully scanned (alive, all children processed)

**Process:**
1. Seed GREY set from GC Roots
2. Pick a GREY object, scan its fields, mark unvisited fields GREY, mark itself BLACK
3. Repeat until no GREY objects remain
4. Everything still WHITE = garbage, reclaim

**Island of Isolation (circular reference gotcha):**
```java
Node a = new Node();  Node b = new Node();
a.other = b;  b.other = a;  // circular reference
a = null;  b = null;        // stack variables cleared
// Object#1 and Object#2 reference each other BUT no root points to them
// -> both WHITE -> both GARBAGE
```

This is why Java GC is superior to reference counting (Python) - reference counting CANNOT collect circular references. Tri-color marking handles it perfectly.

**City Hall analogy:**
GC starts at City Hall (roots) and follows roads. Object#1 and Object#2 have roads between each other, but no road leads FROM City Hall TO them. Unreachable = invisible to GC = garbage.

---

### Q4. How to Handle OutOfMemoryError

#### Types of OutOfMemoryError

OutOfMemoryError is NOT a regular Exception - it is an Error, meaning the JVM itself is in a critical state.

| OOM Type | Cause |
|---|---|
| Java heap space | Heap full - memory leak or heap too small |
| GC overhead limit exceeded | GC spending >98% time recovering <2% heap |
| Metaspace | Too many classes loaded, ClassLoader leak |
| Unable to create native thread | OS-level thread limit exceeded |
| Direct buffer memory | Off-heap NIO buffer exhausted |

---

#### Java Heap Space - Most Common

**Causes:**
1. Memory leak - objects referenced but never released (static collections, unbounded caches)
2. Heap too small for the workload
3. Burst traffic - too many objects created at once

**How to diagnose:**
```bash
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/app-heapdump.hprof
# Analyze with: Eclipse MAT, VisualVM, IntelliJ
```

**Common fix - bounded cache with eviction:**
```java
// LEAK: unbounded static map
static Map<String, ProductResponse> cache = new HashMap<>();

// FIX: Caffeine bounded cache
Cache<String, ProductResponse> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build();
```

---

#### GC Overhead Limit Exceeded

JVM detects it is spending >98% time on GC but recovering <2% heap. It gives up early as a fail-fast signal rather than thrashing forever. Fix: find the leak or increase heap.

---

#### Metaspace OOM

Caused by ClassLoader leak - common in hot-deploy, Spring CGLIB proxies, Hibernate. 
Fix: -XX:MaxMetaspaceSize=256m to fail fast, then investigate ClassLoader leak.

---

#### Follow-up Q&As

**Q: Can you catch OutOfMemoryError?**

Technically yes (it is a Throwable) but almost never should. By the time OOM is thrown, JVM is in an inconsistent state. Correct response: let the app crash, generate heap dump, restart.

**Q: Difference between -Xms and -Xmx?**
```bash
-Xms512m   # Initial heap size
-Xmx2g     # Maximum heap size
# Best practice in production: Xms = Xmx to avoid dynamic heap growth overhead
-Xms2g -Xmx2g
```

**Q: In Blinkit, how would you prevent OOM during a flash sale?**
1. Bounded caches (Caffeine) with TTL eviction
2. Pagination - never load all products into memory at once
3. Heap monitoring alerts at 85% heap usage (Prometheus/Grafana)
4. Circuit breakers - reject new requests if heap is critically high
5. -XX:+HeapDumpOnOutOfMemoryError for post-mortem analysis
6. Load test with realistic object counts before the sale

---

#### One-liner for Interview

> "OutOfMemoryError means the JVM cannot allocate more memory. Most common cause is a memory leak - objects referenced but never released. Fix: enable heap dumps on OOM, analyze with MAT or VisualVM to find what is holding references, then fix the leak or add bounded caches with eviction."

---


### Q5. What is Rehashing in HashMap?

#### How HashMap Works Internally

A HashMap stores data in an array of buckets. The index is determined by the key's hash:
  key -> hashCode() -> compress -> bucket index -> store Entry(key, value)

Default capacity = 16, Load Factor = 0.75, Rehash threshold = 16 x 0.75 = 12
When 13th entry is inserted -> REHASHING TRIGGERED -> new capacity = 32

---

#### What Happens During Rehash

1. Allocate new array (double the size)
2. For EVERY existing entry: recalculate index using new capacity
      newIndex = key.hashCode() & (newCapacity - 1)
3. Place entry at new index
4. Discard old array

Why do ALL positions change?
  hash("milk") = 1234567
  capacity=16:  1234567 & 15 = 7    <- old index
  capacity=32:  1234567 & 31 = 23   <- new index after rehash

This is why ALL n entries must be rehashed, not just the new one.

---

#### Performance Cost

Normal put()  -> O(1)
Rehash event  -> O(n)  <- all n entries must be reindexed

This causes a latency spike on the one insert that triggers rehashing.

---

#### How to Avoid Rehashing - Pre-size the Map

```java
// BAD - starts at 16, rehashes many times as it grows
Map<String, Product> map = new HashMap<>();

// GOOD - pre-size to avoid rehashing
// Formula: expectedSize / loadFactor + 1
int expectedSize = 10_000;
Map<String, Product> map = new HashMap<>((int)(expectedSize / 0.75) + 1);
// capacity = 13,334 -> threshold = 10,000 -> no rehash needed
```

---

#### Follow-up Q&As

**Q: What happens to collisions during rehash?**
Entries in the same bucket (linked list or tree) are re-distributed. Some may move to different buckets - the chain naturally splits.

**Q: What changed in Java 8 regarding HashMap internals?**
When a single bucket has >= 8 entries due to collisions, the linked list is converted to a Red-Black Tree.
Lookup improves from O(n) to O(log n) for that bucket.
Reverts back to linked list when entries drop to <= 6.

**Q: Is HashMap thread-safe?**
No. Concurrent rehashing caused an infinite loop in Java 7 (circular linked list bug). Fixed in Java 8 but still NOT thread-safe. Use ConcurrentHashMap for multithreaded access.

---

#### One-liner for Interview

> "Rehashing is triggered when the number of entries exceeds capacity x load factor (default 0.75). The internal array doubles in size and every entry is reindexed using the new capacity. It is O(n) and causes a latency spike. Pre-size your HashMap with expectedSize / 0.75 + 1 to avoid it in performance-critical code."

---


### Q6. How Does an Object Get Promoted to Old Gen?

#### The 3 Promotion Paths

**1. Age Threshold (most common)**
Every object has an age counter in its mark word (object header), incremented each Minor GC survival.
Default threshold = 15 (controlled by -XX:MaxTenuringThreshold=15).
Why 15 max? Age is stored in 4 bits in the mark word = max value 15.

  Eden (age=0) -> Survivor (age=1) -> Survivor (age=2) -> ... -> Old Gen (age=15)

**2. Survivor Space Overflow (Premature Promotion)**
If Survivor space is too small to hold all surviving objects after Minor GC,
overflow objects are promoted directly to Old Gen regardless of their age.

  Blinkit Flash Sale: 100k Response objects survive Minor GC
  Survivor capacity: 50k objects
  First 50k -> Survivor, remaining 50k -> promoted to Old Gen (too young!)
  -> Old Gen fills with short-lived objects -> premature Full GC

Fix: Increase Survivor space with -XX:SurvivorRatio

**3. Large Object / Humongous Allocation**
Objects too large to fit in Eden are allocated directly in Old Gen, skipping Young Gen.
In G1 GC: object > 50% of region size -> Humongous -> straight to Old Gen.

---

#### Why Premature Promotion Is Bad

Young Gen GC = fast, frequent, cheap
Old Gen GC   = slow, infrequent, expensive

Premature promotion = short-lived objects filling Old Gen
                    = more frequent Major/Full GC
                    = longer STW pauses
                    = production slowdowns during high traffic

---

#### Follow-up Q&As

**Q: How do you detect premature promotion?**
GC logs show frequent Full GCs even with low long-lived object count.
Tools: GCViewer, GCEasy.io, JVM GC logs with -Xlog:gc*

**Q: How to prevent it in Blinkit?**
1. Increase Survivor space ratio (-XX:SurvivorRatio)
2. Reduce object creation in hot paths (object pooling)
3. Use primitives instead of boxed types (int vs Integer)
4. Profile with async-profiler to find allocation hotspots

---

#### One-liner for Interview

> "Objects promote to Old Gen by surviving enough Minor GCs (default age 15), by overflowing Survivor space regardless of age, or by being too large for Eden. Premature promotion is the main cause of unexpected Full GCs in production."

---


### Q7. How does @Transactional handle rollback internally? Pitfalls?

#### How It Works — Proxy Mechanism

Spring wraps your @Service class in a proxy at startup.
When you call a @Transactional method, the proxy intercepts it:

  You -> [Spring Proxy] -> [Your Service]
  
  Proxy logic:
    beginTransaction()
    try { your method }
    catch(RuntimeException) { rollback }
    else { commit }

Your class is never modified. The proxy manages the transaction boundary.

---

#### Rollback Rules — Most Important Detail

DEFAULT behavior:
  ROLLS BACK on:     RuntimeException, Error
  DOES NOT rollback: checked Exception (IOException, SQLException etc.)

@Transactional
public void placeOrder() throws IOException {
    saveOrder();
    throw new IOException(); // ← COMMITS despite exception! Silent bug.
}

Fix:
  @Transactional(rollbackFor = Exception.class)          // all exceptions
  @Transactional(rollbackFor = IOException.class)        // specific checked
  @Transactional(noRollbackFor = StockWarning.class)     // exclude specific

---

#### The 4 Major Pitfalls

**Pitfall 1 — Self-invocation (most common)**
Calling a @Transactional method from within the same class bypasses the proxy.
'this.placeOrder()' calls the real object, not the proxy -> @Transactional ignored.

Fix: inject self (@Autowired OrderService self; self.placeOrder())
     OR move to a separate service class.

**Pitfall 2 — Private method**
Proxy cannot override private methods -> @Transactional silently ignored.
Always use public (or protected) for @Transactional methods.

**Pitfall 3 — Checked exception not rolling back**
Default only rolls back RuntimeException.
Blinkit example: placeOrder throws PaymentException (checked) -> order saved, payment failed. Silent data corruption.
Fix: @Transactional(rollbackFor = PaymentException.class)

**Pitfall 4 — Exception swallowed internally**
If you catch and swallow the exception inside @Transactional method,
Spring proxy never sees it -> commits even on failure.
Fix: re-throw, or call TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()

---

#### External Calls Cannot Be Rolled Back

Email/SMS/Kafka messages sent inside a transaction cannot be undone if transaction rolls back.
Fix: use @TransactionalEventListener(phase = AFTER_COMMIT) to send notifications
     only AFTER the transaction successfully commits.

---

#### One-liner for Interview

> "@Transactional works via Spring proxy wrapping your method in begin/commit/rollback. Key pitfalls: only rolls back RuntimeException by default, silently ignored on self-invocation and private methods, and external calls like emails cannot be rolled back — use @TransactionalEventListener(AFTER_COMMIT) for those."

---



---

### Q8. Transaction Propagation — REQUIRED vs REQUIRES_NEW vs NESTED

#### REQUIRED (Default)

Join existing transaction if one exists. Create a new one if none exists.

  placeOrder() [@Transactional REQUIRED]
    └─ notifyWarehouse() [@Transactional REQUIRED]
           ↑
           Both run in THE SAME transaction.
           If notifyWarehouse() throws → entire transaction rolls back (including placeOrder work).

Use when: the called method MUST be part of the caller's unit of work.

---

#### REQUIRES_NEW

Always create a brand new transaction. Suspend the existing one (if any).

  placeOrder() [@Transactional REQUIRED]
    └─ auditLog() [@Transactional REQUIRES_NEW]
           ↑
           auditLog runs in its OWN transaction.
           If placeOrder rolls back → auditLog is NOT rolled back (already committed).
           If auditLog fails → placeOrder is NOT affected.

Use when: the operation MUST commit independently — audit logs, notifications, metrics.

---

#### NESTED

Create a savepoint within the existing transaction. Partial rollback is possible.

  placeOrder() [@Transactional REQUIRED]
    └─ applyDiscount() [@Transactional NESTED]
           ↑
           Savepoint created before applyDiscount().
           If applyDiscount() throws → rolls back to savepoint (only discount work is undone).
           placeOrder() can catch the exception and continue.
           If placeOrder() rolls back → the ENTIRE thing rolls back (including nested savepoint).

Use when: you want to attempt an optional sub-operation and recover if it fails.

---

#### Quick Comparison Table

  Propagation    | Existing Tx? | New Tx? | Rollback Scope
  ---------------|--------------|---------|------------------------------------------
  REQUIRED       | Joins it     | Creates | Entire shared transaction
  REQUIRES_NEW   | Suspends it  | Always  | Only its own new transaction
  NESTED         | Savepoint    | Never   | Back to savepoint (parent can continue)

---

#### Blinkit Example

  @Transactional                              // REQUIRED — main order tx
  public void placeOrder(Order order) {
      inventoryService.reserve(order);        // REQUIRED — same tx
      auditService.log(order);                // REQUIRES_NEW — committed even if order fails
      try {
          discountService.apply(order);       // NESTED — rolls back discount only if fails
      } catch (DiscountException e) {
          log.warn("Discount failed, continuing");  // order still places
      }
      orderRepo.save(order);
  }

---

#### One-liner for Interview

> "REQUIRED joins the existing transaction (default). REQUIRES_NEW always creates its own independent transaction — useful for audit logs that must survive rollbacks. NESTED creates a savepoint inside the current transaction — allowing partial rollback of a sub-operation while the parent continues."

---


### Q9. How do you implement distributed locking across microservices?

#### Why Distributed Locking?

In a single JVM: synchronized / ReentrantLock works fine.
Across multiple service instances: JVM locks are local — each instance has its own memory.

Blinkit flash sale: 3 inventory-service pods running. 
User A on Pod-1 and User B on Pod-2 both try to buy last item simultaneously.
Each pod's JVM lock is independent → both threads pass → overselling.

Need a lock stored OUTSIDE the JVM — shared by all instances.

---

#### Solution 1: Redis Distributed Lock (Redisson)

**The Core Mechanism — SET NX PX**

  SET lock:product:42 "pod-1-thread-99" NX PX 5000

  NX  = Set only if Not eXists (atomic check-and-set)
  PX  = Expire in 5000 milliseconds (auto-release if crash)

This is a single atomic Redis command — no race condition possible.

**Redisson RLock API**

  RLock lock = redissonClient.getLock("lock:product:" + productId);

  boolean acquired = lock.tryLock(
      3,                   // wait up to 3s to acquire
      10,                  // auto-release after 10s (lease time)
      TimeUnit.SECONDS
  );

  if (acquired) {
      try {
          // critical section — only one pod executes this at a time
          deductInventory(productId);
      } finally {
          if (lock.isHeldByCurrentThread()) {
              lock.unlock();  // safe unlock — only release if WE hold it
          }
      }
  } else {
      throw new RuntimeException("Could not acquire lock, try again");
  }

**Watchdog Thread (Redisson auto-extend)**

  If you call lock.lock() WITHOUT a lease time → Redisson starts a watchdog.
  Every 10s, it auto-extends the TTL (default 30s) as long as the thread is alive.
  Prevents lock expiry during legitimate long operations.
  
  lock.lock() → use watchdog (no expiry until unlock or thread death)
  lock.tryLock(wait, lease, unit) → fixed TTL, no watchdog

---

#### Solution 2: SELECT FOR UPDATE (Database Row Lock)

  @Transactional
  public void deductInventory(Long productId, int qty) {
      Inventory inv = em.createQuery(
          "SELECT i FROM Inventory i WHERE i.productId = :id",
          Inventory.class)
          .setLockMode(LockModeType.PESSIMISTIC_WRITE)  // SELECT FOR UPDATE
          .setParameter("id", productId)
          .getSingleResult();

      if (inv.getStock() >= qty) {
          inv.setStock(inv.getStock() - qty);
      } else {
          throw new InsufficientStockException();
      }
  }

The DB row is locked for the duration of the transaction.
No other transaction can read (with FOR UPDATE) or write that row until commit.

---

#### Redis vs SELECT FOR UPDATE — When to Use Which

  Criterion               | SELECT FOR UPDATE         | Redis (Redisson)
  ------------------------|---------------------------|---------------------------
  Infrastructure          | No extra infra (uses DB)  | Requires Redis
  Scope                   | DB row lock only          | Any resource (API, file)
  External calls inside   | ANTI-PATTERN (holds conn) | Fine — no DB conn held
  Cross-service locking   | DB must be shared         | Works across all services
  Performance             | Slower (DB round-trip)    | Faster (Redis in-memory)
  Complexity              | Simple                    | More moving parts

---

#### Key Anti-Pattern: External Calls Inside @Transactional

  @Transactional
  public void placeOrder(Order order) {
      Inventory inv = inventoryRepo.findByIdForUpdate(id);  // holds DB connection
      paymentService.charge(order);                          // slow external call — 2000ms
      inv.deduct();                                          // finally releases
  }

  Problem: DB connection held for entire 2000ms payment call.
  Under load: HikariCP pool exhausted waiting for connections.
  This is a CODING FLAW (anti-pattern) — NOT a flaw of SELECT FOR UPDATE itself.

  Fix: Release DB transaction before calling external services.
       Use Redis lock if you need to protect a critical section that includes external calls.

---

#### ReentrantLock vs RLock (API similarity)

  ReentrantLock (JVM)          | RLock (Redis via Redisson)
  -----------------------------|----------------------------
  lock.lock()                  | lock.lock()
  lock.tryLock(timeout, unit)  | lock.tryLock(wait, lease, unit)
  lock.unlock()                | lock.unlock()
  lock.isHeldByCurrentThread() | lock.isHeldByCurrentThread()
  
  Same API, completely different mechanism.
  ReentrantLock: JVM monitor object in heap memory.
  RLock: Redis key with TTL — survives process crashes, works across pods.

---

#### One-liner for Interview

> "For distributed locking I use Redisson RLock — it uses Redis SET NX PX for atomic lock acquisition with a TTL for crash safety. SELECT FOR UPDATE is a valid alternative for pure DB row locking, but holding a DB connection during external calls is an anti-pattern regardless of locking strategy. Redis lock is preferred when the critical section includes external service calls, since it doesn't hold a DB connection."

---


---

## Q10. Hibernate L1 vs L2 Cache � Invalidation, Multi-Pod Problem, and Redis

### Why Caching Exists
50,000 requests hit the same product row in a flash sale window.
Without caching: 50,000 DB round-trips. With L1: same EntityManager session reuses it. With L2: all sessions share it.

---

### L1 Cache � EntityManager Scoped (Always ON)

- Tied to a single EntityManager / Session � lives and dies with the transaction.
- Java's identity guarantee: two em.find() calls in the same transaction return the **same object reference**.
- Also drives **dirty checking**: Hibernate snapshots the entity at load time, compares at flush, issues UPDATE only if changed.

`java
@Transactional
public void demo(Long id) {
    Product p1 = em.find(Product.class, id); // ? DB hit
    Product p2 = em.find(Product.class, id); // ? L1 hit, NO DB call
    System.out.println(p1 == p2);            // true � same reference
}
// EntityManager closes ? L1 evicted
`

---

### L2 Cache � SessionFactory Scoped (OFF by Default)

- Shared across all EntityManagers / threads / requests within the same JVM.
- Must explicitly opt in with @Cache annotation + config.

`java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Category { ... }
`

`properties
# application.properties
spring.jpa.properties.hibernate.cache.use_second_level_cache=true
spring.jpa.properties.hibernate.cache.region.factory_class=org.hibernate.cache.jcache.JCacheCacheRegionFactory
`

#### CacheConcurrencyStrategy

| Strategy              | Use Case                                      |
|-----------------------|-----------------------------------------------|
| READ_ONLY             | Static/reference data (country codes)         |
| READ_WRITE            | Normal entities � lock-based consistency      |
| NONSTRICT_READ_WRITE  | Rare writes � brief stale window acceptable   |
| TRANSACTIONAL         | Strict � JTA transactions needed              |

---

### What NOT to Cache

| Field   | Why                                              |
|---------|--------------------------------------------------|
| stock | Changes on every order ? stale within ms        |
| price | Flash sale can change it every second           |

Rule: **High-write, low-read-latency-tolerance data should not be in L2.**

---

### Cache Invalidation Scenarios

| Scenario                        | What Hibernate Does                        | What You Must Do              |
|---------------------------------|--------------------------------------------|-------------------------------|
| save() / merge() via JPA    | Auto-evicts the affected entity from L2 ?  | Nothing                       |
| Bulk @Modifying @Query (JPQL) | Bypasses entity lifecycle � L2 NOT evicted | @CacheEvict or clearAutomatically=true |
| Cross-service REST write        | Hibernate has no knowledge of it           | Redis TTL or event-driven eviction |
| DBA direct SQL update           | Hibernate has no knowledge of it           | Debezium CDC ? Kafka ? evict   |

`java
// Bulk update � must manually evict
@Modifying
@Query("UPDATE Product p SET p.price = :price WHERE p.category = :cat")
@CacheEvict(value = "products", allEntries = true)
int updatePriceByCategory(String cat, BigDecimal price);
`

---

### Multi-Pod Problem

L2 cache is **JVM-local**. In a Kubernetes deployment with 3 pods:

`
Pod 1 (updates + evicts L2) ? Pod 2 L2 still stale ? Pod 3 L2 still stale
`

Pod 1's eviction is invisible to Pods 2 and 3. All three pods serve potentially different values.

---

### Solutions

#### 1. Clustered L2 � Infinispan
- Replicate/invalidate L2 across pods via cluster protocol.
- Complex setup, tight coupling between app and cache topology.

#### 2. Redis as Shared Cache (Production Standard)
`java
@Cacheable(value = "products", key = "#id")
public Product getProduct(Long id) { return repo.findById(id).orElseThrow(); }

@CacheEvict(value = "products", key = "#product.id")
public Product updateProduct(Product product) { return repo.save(product); }
`
- Single Redis cluster ? all pods read/write the same cache.
- TTL-based safety net: even if eviction is missed, data expires automatically.

#### 3. Debezium CDC + Kafka (Bulletproof for Uncontrolled Writes)
`
MySQL binlog ? Debezium ? Kafka topic: db.products
                                ?
                 All pods consume ? evict their local cache
`
- Works for DBA direct SQL, legacy batch jobs, any writer that bypasses Hibernate.
- "If you can't control the writer, watch the DB itself."

---

### One-liner for Interview

> "Hibernate auto-evicts L2 on managed saves. Bulk JPQL and cross-service writes need manual @CacheEvict or a TTL. For multi-pod, L2 is JVM-local � Redis is the production-standard shared cache. For writes that bypass Hibernate entirely (DBA SQL, legacy jobs), Debezium reads the binlog and publishes Kafka events that all pods consume to evict their cache."

---

---

## Q11. N+1 Problem in JPA

**One-liner:** N+1 happens when JPA fires 1 query to load a list, then N more queries to lazily load an association for each item — fix it with JOIN FETCH, @EntityGraph, or @BatchSize.

### Why Lazy Loading Exists
JPA defaults @OneToMany and @ManyToMany to LAZY because:
- You may never need the association (e.g., only need orders.size())
- Loading 1M orders with all their items/customers eagerly would be catastrophic
- "Don't fetch unless asked" — caller decides what it needs

### The Problem
```java
List<Order> orders = orderRepo.findAll();       // Query 1
for (Order o : orders) {
    o.getCustomer().getName();                   // Query 2..N (one per order)
}
```
500 orders = 501 queries. 10,000 orders = 10,001 queries.

### How to Detect
```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        generate_statistics: true
```
Look for repeated `SELECT * FROM customer WHERE id = ?` with different IDs.
In production: use p6spy or datasource-proxy to count queries per request.

### Fix 1 — JOIN FETCH (best for known fetch needs)
```java
@Query("SELECT o FROM Order o JOIN FETCH o.customer")
List<Order> findAllWithCustomer();
```
One query with a JOIN. Use when you always need the association.

### Fix 2 — @EntityGraph (flexible, no JPQL)
```java
@EntityGraph(attributePaths = {"customer", "items"})
List<Order> findAll();
```
Spring Data generates the join. Useful to vary per repository method without writing JPQL.

### Fix 3 — @BatchSize (fallback for collections)
```java
@OneToMany(fetch = FetchType.LAZY)
@BatchSize(size = 25)
List<OrderItem> items;
```
Hibernate groups selects: `SELECT * FROM order_items WHERE order_id IN (?, ?, ..., ?)`
500 queries -> 20 queries. Not a JOIN but dramatically better.

### When to Use Which
| Scenario                                    | Fix           |
|---------------------------------------------|---------------|
| Always need the association                 | JOIN FETCH    |
| Need to vary per repository method          | @EntityGraph  |
| Deep nested collection, can't rewrite query | @BatchSize    |
| Legacy code, quick win                      | @BatchSize    |

---

## Q11. N+1 Problem in JPA

**One-liner:** N+1 happens when JPA fires 1 query to load a list, then N more queries to lazily load an association for each item — fix it with JOIN FETCH, @EntityGraph, or @BatchSize.

### Why Lazy Loading Exists
JPA defaults @OneToMany and @ManyToMany to LAZY because:
- You may never need the association (e.g., only need orders.size())
- Loading 1M orders with all their items/customers eagerly would be catastrophic
- "Don't fetch unless asked" — caller decides what it needs

### The Problem
```java
List<Order> orders = orderRepo.findAll();       // Query 1
for (Order o : orders) {
    o.getCustomer().getName();                   // Query 2..N (one per order)
}
```
500 orders = 501 queries. 10,000 orders = 10,001 queries.

### How to Detect
```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        generate_statistics: true
```
Look for repeated `SELECT * FROM customer WHERE id = ?` with different IDs.
In production: use p6spy or datasource-proxy to count queries per request.

### Fix 1 — JOIN FETCH (best for known fetch needs)
```java
@Query("SELECT o FROM Order o JOIN FETCH o.customer")
List<Order> findAllWithCustomer();
```
One query with a JOIN. Use when you always need the association.

### Fix 2 — @EntityGraph (flexible, no JPQL)
```java
@EntityGraph(attributePaths = {"customer", "items"})
List<Order> findAll();
```
Spring Data generates the join. Useful to vary per repository method without writing JPQL.

### Fix 3 — @BatchSize (fallback for collections)
```java
@OneToMany(fetch = FetchType.LAZY)
@BatchSize(size = 25)
List<OrderItem> items;
```
Hibernate groups selects: `SELECT * FROM order_items WHERE order_id IN (?, ?, ..., ?)`
500 queries -> 20 queries. Not a JOIN but dramatically better.

### When to Use Which
| Scenario                                    | Fix           |
|---------------------------------------------|---------------|
| Always need the association                 | JOIN FETCH    |
| Need to vary per repository method          | @EntityGraph  |
| Deep nested collection, can't rewrite query | @BatchSize    |
| Legacy code, quick win                      | @BatchSize    |
