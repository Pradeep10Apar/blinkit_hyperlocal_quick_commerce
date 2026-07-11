# 📚 Interview Learning Notes: Garbage Collection Internals

> **Topic:** Core Java Deep Dive - Garbage Collection  
> **Date:** May 2026  
> **Related Project:** Blinkit Phase 1 (Quick Commerce)

---

## 📋 Table of Contents

1. [JVM Heap Memory Structure](#1-jvm-heap-memory-structure)
2. [Three Types of GC](#2-three-types-of-gc)
3. [Understanding GC Logs](#3-understanding-gc-logs)
4. [Why GC Triggers at Specific Memory](#4-why-gc-triggers-at-specific-memory)
5. [Real-World Scenarios in Blinkit](#5-real-world-scenarios-in-blinkit)
6. [Handling Flash Sale GC Pressure](#6-handling-flash-sale-gc-pressure)
7. [GC Algorithms Comparison](#7-gc-algorithms-comparison)
8. [Interview Questions & Answers](#8-interview-questions--answers)
9. [JVM Flags Reference](#9-jvm-flags-reference)
10. [Tools for Memory Analysis & GC Monitoring](#10-tools-for-memory-analysis--gc-monitoring)
11. [Eclipse MAT Deep Dive](#11-eclipse-mat-deep-dive)
12. [Generating Heap Dumps](#12-generating-heap-dumps)
13. [OutOfMemoryError Types (All 9 Types)](#13-outofmemoryerror-types)
14. [StackOverflowError vs OutOfMemoryError](#14-stackoverflowerror-vs-outofmemoryerror)
15. [Memory Cleanup After JVM Crash](#15-memory-cleanup-after-jvm-crash)
16. [OutOfMemoryError: Metaspace Deep Dive](#16-outofmemoryerror-metaspace-deep-dive)
17. [ClassLoader Isolation: The Design Rationale](#17-classloader-isolation-the-design-rationale)

---

## 1. JVM Heap Memory Structure

### 1.1 Visual Representation

```
┌─────────────────────────────────────────────────────────────────┐
│                         JVM HEAP                                │
├─────────────────────────────┬───────────────────────────────────┤
│      YOUNG GENERATION       │         OLD GENERATION            │
│  (where new objects live)   │   (long-lived objects)            │
├───────┬───────┬─────────────┼───────────────────────────────────┤
│ Eden  │  S0   │     S1      │           Tenured Space           │
│ Space │(From) │   (To)      │                                   │
│       │       │             │                                   │
│ 80%   │ 10%   │   10%       │                                   │
└───────┴───────┴─────────────┴───────────────────────────────────┘
         Survivor Spaces
```

### 1.2 Memory Areas Explained

| Area | Purpose | Typical Size |
|------|---------|--------------|
| **Eden** | Where ALL new objects are born | ~80% of Young Gen |
| **Survivor (S0, S1)** | Holds objects that survived at least one GC | ~10% each |
| **Old/Tenured Gen** | Long-lived objects promoted from Young Gen | Larger than Young Gen |

### 1.3 Default Ratios

```
Given: Total Heap = 256 MB (with default settings)

NewRatio = 2 (means Old:Young = 2:1)
├── Young Gen = 256 ÷ 3 = ~85 MB
└── Old Gen   = 256 × 2 ÷ 3 = ~170 MB

SurvivorRatio = 8 (means Eden:S0:S1 = 8:1:1)
├── Eden = 85 × (8/10) = ~68 MB
├── S0   = 85 × (1/10) = ~8.5 MB
└── S1   = 85 × (1/10) = ~8.5 MB
```

---

## 2. Three Types of GC

### 2.1 Minor GC (Young Generation GC)

**What triggers it?** Eden space is full.

**What happens?**
1. All live objects in Eden are identified (reachability analysis from GC roots)
2. Live objects are copied to one Survivor space (e.g., S0 → S1)
3. Eden is completely cleared
4. Objects that survived multiple Minor GCs get promoted to Old Gen

**Characteristics:**
- ✅ **Fast** (Young Gen is small)
- ✅ **Frequent** (objects are created constantly)
- ✅ **Stop-the-world** but very short pause (milliseconds)

```
BEFORE Minor GC:
┌─────────────────────────────────────────┐
│ Eden: [A][B][C][D][E]  (FULL!)          │
│ S0:   [X][Y]           (From)           │
│ S1:   [empty]          (To)             │
└─────────────────────────────────────────┘

AFTER Minor GC:
┌─────────────────────────────────────────┐
│ Eden: [empty]          (cleared!)       │
│ S0:   [empty]          (now "To")       │
│ S1:   [A][C][X]        (live objects)   │
│                                         │
│ [B][D][E][Y] → GARBAGE (unreachable)    │
└─────────────────────────────────────────┘
```

### 2.2 Major GC (Old Generation GC)

**What triggers it?** Old Generation is filling up.

**What happens?**
1. Scans the **Old Generation only**
2. Identifies and reclaims dead objects
3. May compact memory (depending on GC algorithm)

**Characteristics:**
- ⚠️ **Slower** than Minor GC (Old Gen is larger)
- ⚠️ **Less frequent**
- ⚠️ **Longer pause times**

### 2.3 Full GC (Entire Heap)

**What triggers it?**
- Old Gen is nearly full and can't accept promotions
- Explicit `System.gc()` call (avoid this!)
- Metaspace/PermGen is full
- Concurrent GC fails to keep up

**What happens?**
1. **Stop-the-world** pause
2. Scans **ENTIRE heap** (Young + Old + Metaspace)
3. Reclaims all garbage
4. Often compacts memory

**Characteristics:**
- 🔴 **Slowest** GC type
- 🔴 **Longest pauses** (can be seconds!)
- 🔴 **Should be rare** in a healthy application

### 2.4 Comparison Table

| Aspect | Minor GC | Major GC | Full GC |
|--------|----------|----------|---------|
| **Scope** | Young Gen only | Old Gen only | Entire heap |
| **Trigger** | Eden full | Old Gen filling | Emergency / explicit |
| **Frequency** | Very frequent | Less frequent | Rare (ideally) |
| **Pause Time** | ~1-50 ms | ~100-500 ms | ~1-10+ seconds |
| **Algorithm** | Copy collection | Mark-Sweep-Compact | Mark-Sweep-Compact |
| **Impact** | Low | Medium | **High** |

---

## 3. Understanding GC Logs

### 3.1 How to Enable GC Logging

```bash
# Modern JVM (Java 9+)
java -Xlog:gc*:file=gc.log:time,uptime,level,tags -jar app.jar

# Or to stdout
java -Xlog:gc*:stdout:time -jar app.jar

# Older JVM (Java 8)
java -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:gc.log -jar app.jar
```

### 3.2 Reading a GC Log Line

```
[0.156s][info][gc] GC(0) Pause Young (Normal) 24M->1M(128M) 2.341ms
```

**Breakdown:**

| Component | Value | Meaning |
|-----------|-------|---------|
| `[0.156s]` | 0.156 seconds | GC happened 156ms after JVM started |
| `[info]` | Info level | Log severity |
| `[gc]` | GC category | This is a garbage collection log |
| `GC(0)` | Event #0 | First GC event (0-indexed) |
| `Pause Young` | Young Gen GC | This is a **Minor GC** |
| `(Normal)` | Normal trigger | Eden was full (not emergency) |
| `24M` | 24 MB | Heap used **BEFORE** GC |
| `1M` | 1 MB | Heap used **AFTER** GC |
| `(128M)` | 128 MB | Total heap size |
| `2.341ms` | 2.341 milliseconds | **Stop-the-world pause time** |

### 3.3 Visual Representation

```
BEFORE GC (24M used):
┌─────────────────────────────────────────────────────────────────┐
│ HEAP (128M total)                                               │
├──────────────────────┬──────────────────────────────────────────┤
│████████████████████░░│░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│
│◄──── 24M used ─────► │◄──────────── 104M free ────────────────►│
└──────────────────────┴──────────────────────────────────────────┘

                    ⬇️  Minor GC runs (2.341ms pause)  ⬇️

AFTER GC (1M used):
┌─────────────────────────────────────────────────────────────────┐
│ HEAP (128M total)                                               │
├───┬─────────────────────────────────────────────────────────────┤
│███│░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│
│1M │◄────────────────────── 127M free ─────────────────────────►│
└───┴─────────────────────────────────────────────────────────────┘

📈 Reclaimed: 24M - 1M = 23M of garbage collected!
```

### 3.4 Common GC Log Patterns

```
# ✅ Healthy Minor GC (big drop = most objects were garbage)
GC(0) Pause Young (Normal) 24M->1M(128M) 2.341ms

# ⚠️ Memory Leak Warning (small drop = objects surviving)
GC(5) Pause Young (Normal) 50M->48M(128M) 5.123ms

# 🔴 Full GC (long pause!)
GC(10) Pause Full (Ergonomics) 120M->45M(128M) 850.234ms
```

---

## 4. Why GC Triggers at Specific Memory

### 4.1 Key Insight

> **Minor GC triggers when EDEN is full — NOT when the entire heap is full!**

### 4.2 Heap Breakdown Example

With `-Xmx128m` and default JVM settings:

```
TOTAL HEAP = 128 MB
     │
     ├── OLD GENERATION (Tenured)
     │      └── ~85 MB  (2/3 of heap, NewRatio=2)
     │
     └── YOUNG GENERATION
            └── ~43 MB  (1/3 of heap)
                  │
                  ├── EDEN SPACE
                  │      └── ~34 MB  (80% of Young Gen)
                  │
                  ├── Survivor S0
                  │      └── ~4.3 MB (10% of Young Gen)
                  │
                  └── Survivor S1
                         └── ~4.3 MB (10% of Young Gen)
```

### 4.3 Why 24MB Triggered GC on 128MB Heap?

Several factors reduce the *effective* Eden size:

| Factor | Explanation |
|--------|-------------|
| **TLABs** | Each thread reserves a chunk of Eden (~1-2MB each) |
| **Object headers** | Each object has 12-16 bytes overhead |
| **Alignment** | Objects are aligned to 8-byte boundaries |
| **G1 regions** | G1 GC divides heap into fixed-size regions |
| **JVM internals** | Class metadata, JIT compiled code, etc. |

With G1 GC (default in Java 9+), Eden starts small and grows dynamically based on allocation patterns.

---

## 5. Real-World Scenarios in Blinkit

### 5.1 Object Allocation Hotspots

```
┌─────────────────────────────────────────────────────────────────┐
│                BLINKIT - OBJECT ALLOCATION HOTSPOTS             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  🔴 HIGH ALLOCATION (GC Pressure)                               │
│  ├── Bulk Upload Service - CSV parsing, entity creation         │
│  ├── Search Service - Large result sets from Elasticsearch      │
│  ├── Kafka Consumer - Processing message bursts                 │
│  └── Cart → Order conversion - Creating many OrderItemEntity    │
│                                                                 │
│  🟡 MEDIUM ALLOCATION                                           │
│  ├── Cart toResponse() - Loading products, creating DTOs        │
│  └── JSON serialization/deserialization                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 Scenario: Bulk Product Upload

**Code from Blinkit:**
```java
@Transactional
public BulkUploadResponse upload(MultipartFile file) throws Exception {
    var productsBatch = new ArrayList<ProductEntity>(200);
    var outboxBatch = new ArrayList<ProductOutboxEvent>(200);

    for (CSVRecord r : records) {
        ProductCsvRow row = toRow(r);           // NEW object
        ProductEntity p = new ProductEntity();   // NEW object
        String payload = objectMapper.writeValueAsString(...);  // NEW String
        outboxBatch.add(ProductOutboxEvent.builder()...build()); // NEW object
        
        // ✅ GOOD: Batch and clear!
        if (productsBatch.size() >= 200) {
            inserted += flushBatch(productsBatch, outboxBatch);
        }
    }
}

private int flushBatch(...) {
    productRepository.saveAll(products);
    outboxRepository.saveAll(outbox);
    
    em.flush();
    em.clear();    // ← CRITICAL! Detaches entities from persistence context
    
    products.clear();  // ← Allows GC to reclaim
    outbox.clear();    // ← Allows GC to reclaim
    return count;
}
```

**Memory with Batching vs Without:**

```
WITH BATCHING (200 items/batch):
Heap   ╱╲    ╱╲    ╱╲    ╱╲
Usage ╱  ╲  ╱  ╲  ╱  ╲  ╱  ╲    ✅ Smooth sawtooth
     ╱    ╲╱    ╲╱    ╲╱    ╲
        GC   GC   GC   GC

WITHOUT BATCHING (100K items):
Heap                        ████████████
Usage                    ████            ████
                      ████                    → OOM! 🔴
                   ████
```

### 5.3 Scenario: Flash Sale Order Burst

**Normal Day vs Flash Sale:**

```
NORMAL DAY:
- Cart has 5-10 items
- ~10 objects created per order
- 100 orders/minute = 1000 objects/minute
- GC handles easily ✅

FLASH SALE (100x traffic):
- 10,000 orders/minute
- 100,000+ objects/minute
- Objects created faster than GC can clean
- Minor GC every few seconds
- Eventually: Full GC pauses (site becomes slow!) ⚠️
```

### 5.4 GC Impact Summary for Blinkit

| Scenario | GC Impact | Risk Level | Mitigation |
|----------|-----------|------------|------------|
| **Bulk Upload** | High - many objects | 🔴 High | Batching + `em.clear()` |
| **Large Search** | High - huge response | 🔴 High | Limit page size |
| **Kafka Burst** | High - message backlog | 🔴 High | Back-pressure, batch consume |
| **Order Placement** | Medium - per-item objects | 🟡 Medium | Acceptable with tuning |
| **Cart toResponse** | Low - short-lived | 🟢 Low | Perfect for Young Gen GC |

---

## 6. Handling Flash Sale GC Pressure

### 6.1 Solution: Pre-size Collections

```java
// ❌ BEFORE: ArrayList resizes internally
List<OrderItemEntity> orderItems = new ArrayList<>();

// ✅ AFTER: Pre-allocate with known size
List<OrderItemEntity> orderItems = new ArrayList<>(cart.items().size());
```

### 6.2 Solution: JVM Tuning

```bash
# Flash Sale JVM Configuration
java \
  -Xms2g -Xmx2g \                    # Same min/max = no resize pauses
  -XX:+UseG1GC \                      # G1 for large heaps
  -XX:MaxGCPauseMillis=50 \           # Target 50ms max pause
  -XX:InitiatingHeapOccupancyPercent=35 \  # Start GC earlier
  -XX:+AlwaysPreTouch \               # Pre-touch memory at startup
  -jar blinkit.jar
```

### 6.3 Solution: Rate Limiting

```java
@Service
public class OrderService {
    private final RateLimiter rateLimiter = RateLimiter.create(1000); // 1000/sec max

    public OrderResponse placeOrder(String cartId) {
        if (!rateLimiter.tryAcquire()) {
            throw new TooManyRequestsException("Server busy, please try again");
        }
        // ... rest of order logic
    }
}
```

### 6.4 Solution: Async Processing (Best for Flash Sales!)

```java
// ✅ FAST: Just validate and queue
public OrderAcceptedResponse placeOrderAsync(String cartId) {
    CartResponse cart = cartService.getCart(cartId);
    String orderId = UUID.randomUUID().toString();
    
    // Send to Kafka (non-blocking!)
    kafka.send("order-requests", orderId, mapper.writeValueAsString(request));
    
    // Return immediately!
    return new OrderAcceptedResponse(orderId, "PROCESSING");
}

// Separate consumer processes at controlled rate
@KafkaListener(topics = "order-requests", concurrency = "5")
public void processOrder(String payload) {
    // Heavy processing happens here, at controlled pace
}
```

### 6.5 Flash Sale Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     FLASH SALE ARCHITECTURE                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Users (10,000/min) → NGINX (rate limit) → Load Balancer      │
│                              │                                  │
│              ┌───────────────┼───────────────┐                 │
│              ▼               ▼               ▼                 │
│          [App 1]         [App 2]         [App 3]               │
│          -Xmx2g          -Xmx2g          -Xmx2g                │
│              │               │               │                 │
│              └───────────────┼───────────────┘                 │
│                              ▼                                  │
│                     ┌─────────────┐                            │
│                     │    KAFKA    │  ← Buffer 100K+ orders     │
│                     └──────┬──────┘                            │
│                            ▼ (controlled rate)                 │
│                     ┌─────────────┐                            │
│                     │Order Workers│  ← Batch processing        │
│                     └─────────────┘                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 7. GC Algorithms Comparison

### 7.1 Quick Reference

| Feature | Serial | Parallel | CMS | G1 |
|---------|--------|----------|-----|-----|
| **Threads** | 1 | N | N + app | N + app |
| **Pause Type** | Full STW | Full STW | Short STW | Predictable |
| **Goal** | Simplicity | Throughput | Low latency | Balanced |
| **Heap Size** | <100MB | Medium-Large | Large | Large |
| **Java Default** | - | Java 8 | Deprecated | Java 9+ |

### 7.2 When to Use Which

```java
// Batch Job (throughput focused)
java -XX:+UseParallelGC -Xmx4g -jar bulk-upload.jar

// API Server (balanced)
java -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -Xmx2g -jar api.jar

// Flash Sale (low latency)
java -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -Xmx4g -jar api.jar
```

---

## 8. Interview Questions & Answers

### Q1: "Why is Minor GC fast?"
> Because Young Gen is small, and it uses a **copy collector** — only live objects are copied, dead objects are simply abandoned.

### Q2: "Why is Full GC dangerous?"
> It's a complete **stop-the-world** event that scans the entire heap. In large heaps (10GB+), this can mean multi-second pauses, causing timeouts and degraded user experience.

### Q3: "When does an object get promoted?"
> After surviving a configurable number of Minor GCs (default: 15, controlled by `-XX:MaxTenuringThreshold`), OR if Survivor space can't hold it.

### Q4: "How to minimize Full GC?"
> - Right-size your heap
> - Tune `-Xmx`, `-Xms`, `-XX:NewRatio`
> - Use appropriate GC algorithm (G1 for large heaps)
> - Avoid memory leaks
> - Never call `System.gc()`

### Q5: "What does 24M->1M mean in a GC log?"
> Heap had 24MB before GC, only 1MB survived after. The 23MB difference was garbage collected. A large drop indicates most objects were short-lived (good!). A small drop might indicate a memory leak.

### Q6: "Why did GC trigger at 24MB on a 128MB heap?"
> Because Minor GC triggers when **Eden space** is full, not the entire heap. Eden is only ~25-30% of total heap with default settings.

### Q7: "How would you handle a flash sale GC problem?"
> 1. **Immediate:** Tune JVM (larger heap, G1GC with low pause target)
> 2. **Short-term:** Add rate limiting to prevent cascade failure
> 3. **Best practice:** Make order placement **async** — queue to Kafka, process at controlled rate
> 4. **Scale:** Horizontal scaling with load balancer

---

## 9. JVM Flags Reference

### 9.1 Heap Sizing

```bash
-Xms512m              # Initial heap size
-Xmx2g                # Maximum heap size
-XX:NewRatio=2        # Old:Young ratio (2:1)
-XX:SurvivorRatio=8   # Eden:Survivor ratio (8:1:1)
-XX:MaxTenuringThreshold=15  # GCs before promotion
```

### 9.2 GC Algorithm Selection

```bash
-XX:+UseSerialGC      # Serial GC
-XX:+UseParallelGC    # Parallel GC
-XX:+UseG1GC          # G1 GC (default Java 9+)
-XX:+UseZGC           # ZGC (low latency)
-XX:+UseShenandoahGC  # Shenandoah (low latency)
```

### 9.3 G1 Tuning

```bash
-XX:MaxGCPauseMillis=200        # Target max pause
-XX:G1HeapRegionSize=16m        # Region size
-XX:InitiatingHeapOccupancyPercent=35  # When to start marking
-XX:+AlwaysPreTouch             # Pre-allocate memory
```

### 9.4 GC Logging

```bash
# Java 9+
-Xlog:gc*:stdout:time
-Xlog:gc*:file=gc.log:time,uptime,level,tags

# Java 8
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-Xloggc:gc.log
```

---

## 📝 Quick Revision Checklist

- [ ] Can explain heap structure (Eden, Survivor, Old Gen)
- [ ] Know the difference between Minor, Major, and Full GC
- [ ] Can read and interpret GC logs
- [ ] Understand why GC triggers at specific memory thresholds
- [ ] Know real-world scenarios that cause GC pressure
- [ ] Can recommend solutions for flash sale scenarios
- [ ] Familiar with JVM tuning flags
- [ ] Can choose appropriate GC algorithm for use case
- [ ] Know how to use Eclipse MAT for leak detection
- [ ] Understand difference between Java Heap Space vs GC Overhead OOM
- [ ] Can generate and analyze heap dumps

---

## 10. Tools for Memory Analysis & GC Monitoring

### 10.1 Overview: Which Tool When?

| Scenario | Best Tool | Notes |
|----------|-----------|-------|
| **Quick live monitoring** | JConsole, VisualVM | Built-in / Free download |
| **Analyze GC log file** | GCViewer, GCEasy.io | Generates graphs like production monitoring |
| **Find actual leak source** | Eclipse MAT | **Gold standard** for leak detection |
| **Production monitoring** | Prometheus + Grafana | Dashboards with alerts |
| **Quick heap snapshot** | `jmap` + MAT | Command line + analysis |

### 10.2 JConsole (Built into JDK)

```bash
# Launch JConsole (available in JDK)
"C:\Program Files\Java\jdk-17\bin\jconsole.exe"

# Connect to remote app (need JMX enabled)
jconsole hostname:port
```

**Enable JMX for your app:**
```bash
java -Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=9010 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     -jar blinkit.jar
```

**JConsole shows:**
- Memory usage graphs (heap & non-heap)
- Thread monitoring
- GC activity
- MBeans

### 10.3 VisualVM (Separate Download - Not in JDK 9+)

> ⚠️ **Note:** VisualVM was removed from JDK starting with Java 9. Download separately from: https://visualvm.github.io/

**Features:**
- Live heap graph
- CPU & memory profiling
- Thread dumps
- Heap dumps

### 10.4 GCViewer (GC Log Analysis)

Analyzes GC log files and generates graphs showing memory leak patterns.

```bash
# 1. Generate GC logs
java -Xlog:gc*:file=gc.log:time,uptime,level,tags -jar blinkit.jar

# 2. Download GCViewer: https://github.com/chewiebug/GCViewer
# 3. Open gc.log in GCViewer → See graphs!
```

### 10.5 GCEasy.io (Free Online Tool)

Upload your GC log and get instant analysis with beautiful graphs!

```bash
# 1. Run with GC logging
java -Xlog:gc*:file=gc.log:time -jar blinkit.jar

# 2. Go to https://gceasy.io/
# 3. Upload gc.log
# 4. Get graphs + recommendations!
```

### 10.6 Understanding Memory Leak Patterns in Graphs

**✅ Healthy Application (Sawtooth with STABLE baseline):**
```
Heap   ╱╲    ╱╲    ╱╲    ╱╲    ╱╲    ╱╲
      ╱  ╲  ╱  ╲  ╱  ╲  ╱  ╲  ╱  ╲  ╱  ╲
     ╱    ╲╱    ╲╱    ╲╱    ╲╱    ╲╱    ╲
    ─────────────────────────────────────── ← Baseline STABLE!
     GC drops to same level every time ✅
```

**🔴 Memory Leak (Sawtooth with CLIMBING baseline):**
```
Heap                              ╱╲  ╱╲
                            ╱╲  ╱╲  ╲╱  ╲───── Max heap!
                       ╱╲  ╱  ╲╱           
                  ╱╲  ╱  ╲╱                    
             ╱╲  ╱  ╲╱                         
        ╱╲  ╱  ╲╱                              
   ╱╲  ╱  ╲╱                                   
  ╱  ╲╱    ← Each GC reclaims LESS! 🔴
  
Key indicator: After GC, memory doesn't drop to same level!
```

---

## 11. Eclipse MAT Deep Dive

### 11.1 Why Eclipse MAT is the Best for Leak Detection

| Feature | What It Does |
|---------|--------------|
| **Leak Suspects Report** | Automatically identifies probable leaks! |
| **Dominator Tree** | Shows what's holding memory hostage |
| **Histogram** | Object counts by class |
| **Path to GC Roots** | Shows WHY an object can't be garbage collected |
| **OQL** | SQL-like queries on heap objects |

### 11.2 Download & Install

1. Go to: https://eclipse.dev/mat/downloads.php
2. Download **Windows (x86_64)** standalone version
3. Extract and run `MemoryAnalyzer.exe`

### 11.3 Key Features Explained

#### Leak Suspects Report (Most Important!)

When MAT opens a dump, select "Leak Suspects Report":

```
┌─────────────────────────────────────────────────────────────────────┐
│  LEAK SUSPECTS REPORT                                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Problem Suspect 1                                                  │
│  ════════════════                                                   │
│  The thread "main" keeps local variables with total size            │
│  156,234,567 bytes (78.5% of heap!)                                │
│                                                                     │
│  Biggest Objects:                                                   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ java.util.ArrayList @ 0x7f8a2b3c                            │   │
│  │   └── Object[] (100,000 elements)                           │   │
│  │         └── com.blinkit.product.ProductEntity (×100,000)    │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  💡 Hint: This ArrayList is never cleared!                          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

#### Dominator Tree (Who's holding memory?)

```
Retained Heap    | Class
─────────────────┼────────────────────────────────────
156 MB (78%)     | java.util.ArrayList
 └── 100 MB      |   └── Object[]
      └── 95 MB  |        └── ProductEntity[] (100K objects!)

Retained Heap = Memory freed if THIS object was garbage collected
```

#### Path to GC Roots (Why can't it be collected?)

Right-click any object → **Path to GC Roots** → **exclude weak references**

```
ProductEntity @ 0x7f8a2b3c
  ↑
  └── Object[] @ 0x7f8a1234
        ↑
        └── ArrayList @ 0x7f8a0001
              ↑
              └── leakyCache (static field)    ← HERE'S YOUR LEAK!
                    ↑
                    └── ProductBulkUploadService (class)
```

### 11.4 MAT Cheat Sheet

| Action | How |
|--------|-----|
| **Find biggest objects** | Dominator Tree (toolbar icon) |
| **Find leak suspects** | Run Leak Suspects Report |
| **Count objects by class** | Histogram (toolbar icon) |
| **Why can't object be GC'd?** | Right-click → Path to GC Roots |
| **Find all instances of class** | Histogram → Right-click class → List Objects |
| **Query objects** | OQL: `SELECT * FROM com.blinkit.product.ProductEntity` |

---

## 12. Generating Heap Dumps

### 12.1 Methods to Generate Heap Dump

#### Option A: Automatic on OutOfMemoryError (Best for Production)

```bash
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=C:\heapdumps\ \
     -jar blinkit.jar
```

#### Option B: Manual Dump (While App is Running)

```bash
# Find your Java process ID
jps -l

# Output example:
# 12345 com.blinkit.phase1.BlinkitPhase1Application

# Generate heap dump
jmap -dump:format=b,file=C:\heapdumps\blinkit.hprof 12345
```

#### Option C: From JConsole

1. Open JConsole → Connect to your app
2. Go to **MBeans** tab
3. Navigate to `com.sun.management` → `HotSpotDiagnostic` → `Operations`
4. Call `dumpHeap` with filename

### 12.2 Heap Dump Location

| Scenario | Default Location |
|----------|-----------------|
| **No path specified** | Current working directory |
| **`-XX:HeapDumpPath=<dir>`** | Your specified directory |
| **OOM without flag** | **NO DUMP CREATED!** |

### 12.3 Important Note

> ⚠️ Heap dumps are **ONLY created on OutOfMemoryError** when using `-XX:+HeapDumpOnOutOfMemoryError`. If your code catches OOM or stops before OOM, no dump is generated!

---

## 13. OutOfMemoryError Types

### 13.0 All 9 Types of OutOfMemoryError

JVM can throw **9 distinct OutOfMemoryError** messages:

| # | Error Message | Memory Area | Common Cause |
|---|---------------|-------------|--------------|
| 1 | **Java heap space** | Heap | Objects can't be allocated; heap is full |
| 2 | **GC overhead limit exceeded** | Heap | GC using >98% CPU time, recovering <2% memory |
| 3 | **Requested array size exceeds VM limit** | Heap | Array allocation larger than heap allows |
| 4 | **Metaspace** | Metaspace | Too many classes loaded (Java 8+) |
| 5 | **PermGen space** | PermGen | Too many classes loaded (Java 7 and earlier) |
| 6 | **Unable to create new native thread** | Native | OS thread limit reached |
| 7 | **Kill process or sacrifice child** | Native | Linux OOM Killer terminated JVM |
| 8 | **reason stack_trace_with_native_method** | Native | JNI/native code allocation failure |
| 9 | **Direct buffer memory** | Direct Memory | `ByteBuffer.allocateDirect()` limit exceeded |

```
┌─────────────────────────────────────────────────────────────┐
│                        JVM Memory                           │
├─────────────────────┬───────────────────────────────────────┤
│       HEAP          │            NON-HEAP                   │
├─────────────────────┼───────────────────────────────────────┤
│ • Java heap space   │ • Metaspace / PermGen                 │
│ • GC overhead limit │ • Unable to create native thread      │
│ • Array size limit  │ • Direct buffer memory                │
│                     │ • Native method (JNI)                 │
│                     │ • Kill process (OS-level)             │
└─────────────────────┴───────────────────────────────────────┘
```

**JVM Flags to Control Each:**

```bash
# 1. Java heap space
-Xmx4g -Xms4g

# 2. GC overhead limit
-XX:+UseGCOverheadLimit  # (enabled by default)
-XX:-UseGCOverheadLimit  # disable

# 3. Array size - no direct flag, limited by heap

# 4. Metaspace (Java 8+)
-XX:MaxMetaspaceSize=512m

# 5. PermGen (Java 7 and earlier)
-XX:MaxPermSize=256m

# 6. Native threads
ulimit -u 65535  # OS level

# 7. OOM Killer - OS level
echo -17 > /proc/<pid>/oom_adj  # discourage killing

# 8. Native/JNI - depends on native code

# 9. Direct buffer memory
-XX:MaxDirectMemorySize=1g
```

### 13.1 Java Heap Space vs GC Overhead Limit

Two different OOM errors with different root causes:

#### Type 1: `OutOfMemoryError: Java heap space`

**Meaning:** "I tried to allocate an object, but there's NO SPACE left!"

```
HEAP (128 MB):
┌─────────────────────────────────────────────────────────────────┐
│████████████████████████████████████████████████████████████████│ 100% FULL!
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
        new byte[10_000_000]  →  ❌ No room! OOM!
```

**Characteristics:**
- **Fast failure** - Immediate when allocation can't fit
- Heap is completely full
- GC ran but couldn't free enough

**Common causes:**
- Memory leak
- Heap too small (`-Xmx` set too low)
- Large single allocation

#### Type 2: `OutOfMemoryError: GC overhead limit exceeded`

**Meaning:** "I'm spending 98%+ of time doing GC, but only recovering <2% memory!"

```
TIME ────────────────────────────────────────────────────────────────→

     GC    GC    GC    GC    GC    GC    GC    GC    GC    GC
     ↓     ↓     ↓     ↓     ↓     ↓     ↓     ↓     ↓     ↓
Heap █████████████████████████████████████████████████████████████
     98%   97%   98%   97%   98%   97%   98%   97%   98%   97%
     
     Each GC only frees 1-2%!
     GC is running 98% of the time!
     
     → JVM: "This is pointless, throwing GC overhead limit exceeded"
```

**Threshold:**
```
IF (time spent in GC > 98%) 
   AND (memory recovered < 2%)
   FOR multiple consecutive GC cycles
THEN throw "GC overhead limit exceeded"
```

**Characteristics:**
- **Slow death** - App thrashes before crashing
- CPU at 100% (GC consuming all resources)
- App becomes extremely slow, then crashes

### 13.2 Comparison Table

| Aspect | Java Heap Space | GC Overhead Limit |
|--------|-----------------|-------------------|
| **What failed?** | Allocation | Nothing (GC ran fine) |
| **Heap state** | 100% full | ~98% full, stuck |
| **GC behavior** | Ran, couldn't free enough | Running constantly |
| **Speed of death** | **Fast** - immediate | **Slow** - thrashes first |
| **CPU during issue** | Normal | **100%** (all GC) |
| **User experience** | App crashes | App freezes, then crashes |

### 13.3 Code Example: Trigger Both

```java
// Type 1: Java Heap Space (immediate failure)
// Run with: java -Xmx64m OOMDemo heap
static void triggerHeapSpaceOOM() {
    byte[] huge = new byte[100 * 1024 * 1024]; // 100MB > 64MB heap
    // java.lang.OutOfMemoryError: Java heap space
}

// Type 2: GC Overhead Limit (slow thrashing death)
// Run with: java -Xmx64m OOMDemo overhead
static void triggerGCOverheadOOM() {
    Map<Integer, String> map = new HashMap<>();
    int i = 0;
    while (true) {
        map.put(i++, "value-" + i + "-" + System.nanoTime());
        // Heap fills gradually... GC runs constantly... 
        // but can't free anything (all objects still referenced!)
        // java.lang.OutOfMemoryError: GC overhead limit exceeded
    }
}
```

### 13.4 JVM Flag to Disable GC Overhead Check

```bash
# NOT recommended - just delays the inevitable
java -XX:-UseGCOverheadLimit -jar app.jar
```

---

## 📝 Quick Revision Checklist (Updated)

- [ ] Can explain heap structure (Eden, Survivor, Old Gen)
- [ ] Know the difference between Minor, Major, and Full GC
- [ ] Can read and interpret GC logs
- [ ] Understand why GC triggers at specific memory thresholds
- [ ] Know real-world scenarios that cause GC pressure
- [ ] Can recommend solutions for flash sale scenarios
- [ ] Familiar with JVM tuning flags
- [ ] Can choose appropriate GC algorithm for use case
- [ ] **Know tools: JConsole, VisualVM, GCViewer, GCEasy.io, Eclipse MAT**
- [ ] **Can use Eclipse MAT to find memory leaks**
- [ ] **Know how to generate heap dumps**
- [ ] **Understand Java Heap Space vs GC Overhead Limit OOM**

---

> **Next Topic:** GC Algorithms Deep Dive (Serial, Parallel, CMS, G1)

---

## 14. StackOverflowError vs OutOfMemoryError

### 14.1 Class Hierarchy - They Are SIBLINGS, Not Parent-Child!

```
java.lang.Throwable
└── java.lang.Error
    └── java.lang.VirtualMachineError
        ├── java.lang.OutOfMemoryError    ← 9 types
        ├── java.lang.StackOverflowError  ← SEPARATE class
        ├── java.lang.InternalError
        └── java.lang.UnknownError
```

**Key Point:** `StackOverflowError` is NOT a type of `OutOfMemoryError`!

### 14.2 Stack vs Heap: What Lives Where

```
STACK (per thread)              HEAP (shared)
┌─────────────────────┐        ┌─────────────────────┐
│ • Method frames     │        │ • Object instances  │
│ • Local primitives  │        │ • Arrays            │
│ • Object references │───────►│ • Strings           │
│ • Method parameters │        │ • Class instances   │
│ • Return addresses  │        │                     │
└─────────────────────┘        └─────────────────────┘
```

**Each method call pushes a STACK FRAME:**

```
┌─────────────────────────────────┐
│         STACK FRAME             │
├─────────────────────────────────┤
│  • Local primitive variables    │
│  • References (pointers) to     │
│    objects (objects are on heap)│
│  • Method parameters            │
│  • Return address               │
│  • Intermediate calculations    │
└─────────────────────────────────┘
```

### 14.3 Visual Example

```java
public void a() {
    int x = 10;        // x goes on stack
    String s = "hi";   // reference on stack, "hi" on heap
    b();               // new frame pushed
}

public void b() {
    int y = 20;
    c();               // another frame pushed
}

public void c() {
    int z = 30;
    a();               // keeps pushing... 💥 StackOverflowError
}
```

```
STACK (grows down)           HEAP
┌─────────────┐              ┌─────────────┐
│ a(): x=10   │───reference──│  "hi"       │
├─────────────┤              └─────────────┘
│ b(): y=20   │              
├─────────────┤              
│ c(): z=30   │              
├─────────────┤              
│ a(): x=10   │  ← recursion
├─────────────┤              
│ b(): y=20   │              
├─────────────┤              
│    ...      │              
├─────────────┤              
│   💥 BOOM   │ ← Stack limit reached
└─────────────┘              
```

### 14.4 Key Differences

| Aspect | StackOverflowError | OutOfMemoryError |
|--------|-------------------|------------------|
| **Cause** | Too deep recursion | Can't allocate memory |
| **Memory area** | Thread stack | Heap, Metaspace, Native, etc. |
| **What fills up** | Method call frames | Objects |
| **Typical fix** | Fix recursion / increase `-Xss` | Increase heap / fix leak |
| **JVM Flag** | `-Xss1m` (stack size per thread) | `-Xmx`, `-XX:MaxMetaspaceSize`, etc. |

### 14.5 The Subtle Nuance

| Scenario | Error Thrown |
|----------|-------------|
| Thread's stack depth exceeds limit (deep recursion) | `StackOverflowError` |
| JVM can't allocate stack for a **new thread** | `OutOfMemoryError: unable to create new native thread` |
| JVM can't **expand** existing stack (dynamic expansion) | Could be either! |

### 14.6 Code Example

```java
// StackOverflowError - NOT an OOM (fills stack with frames)
public void infiniteRecursion() {
    infiniteRecursion(); // 💥 StackOverflowError
}

// OutOfMemoryError - actual OOM (fills heap with objects)
public void heapExhaustion() {
    List<byte[]> list = new ArrayList<>();
    while (true) {
        list.add(new byte[1_000_000]); // 💥 OOM: Java heap space
    }
}
```

**Key Insight:**
> **Stack = Method call depth (frames)**  
> **Heap = Object storage (instances)**

---

## 15. Memory Cleanup After JVM Crash

### 15.1 Who Clears Memory When JVM Terminates?

**The Operating System clears everything when the JVM process terminates.**

JVM is just a process to the OS. When it dies → OS reclaims ALL its memory.

### 15.2 Visual Explanation

```
┌─────────────────────────────────────────────────────────────┐
│                   OPERATING SYSTEM                          │
│                                                             │
│   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│   │ JVM Process │  │ Chrome      │  │ VS Code     │        │
│   │ (your app)  │  │ Process     │  │ Process     │        │
│   │             │  │             │  │             │        │
│   │ ┌─────────┐ │  │             │  │             │        │
│   │ │ Stack   │ │  │             │  │             │        │
│   │ │ Heap    │ │  │             │  │             │        │
│   │ │ Native  │ │  │             │  │             │        │
│   │ └─────────┘ │  │             │  │             │        │
│   └──────┬──────┘  └─────────────┘  └─────────────┘        │
│          │                                                  │
│          │ 💥 StackOverflow / OOM                          │
│          ▼                                                  │
│   Process terminates                                        │
│          │                                                  │
│          ▼                                                  │
│   OS RECLAIMS ALL MEMORY assigned to that process          │
│   (Stack, Heap, Native - EVERYTHING)                       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 15.3 How OS Memory Management Works

```
BEFORE CRASH:
┌────────────────────────────────────────────┐
│              PHYSICAL RAM                  │
├──────────┬──────────┬──────────┬──────────┤
│ OS Kernel│ JVM      │ Chrome   │  FREE    │
│          │ (4 GB)   │ (2 GB)   │          │
└──────────┴──────────┴──────────┴──────────┘

AFTER JVM CRASH:
┌────────────────────────────────────────────┐
│              PHYSICAL RAM                  │
├──────────┬──────────────────────┬──────────┤
│ OS Kernel│        FREE          │ Chrome   │
│          │   (JVM's 4 GB now    │ (2 GB)   │
│          │    available)        │          │
└──────────┴──────────────────────┴──────────┘
```

### 15.4 The Mechanism

| Step | What Happens |
|------|-------------|
| 1 | JVM throws `StackOverflowError` or `OutOfMemoryError` |
| 2 | Error is uncaught → JVM process exits |
| 3 | OS receives process termination signal |
| 4 | OS updates **page tables** - marks JVM's memory pages as FREE |
| 5 | Memory is now available for other processes |

This is called **Virtual Memory Management**.

### 15.5 Virtual Address Space

Each process gets its own **virtual address space** (isolated from others):

```
JVM Process sees:          Chrome Process sees:
0x0000 ┌──────────┐        0x0000 ┌──────────┐
       │ Stack    │               │ Stack    │
       │ Heap     │               │ Heap     │
       │ Code     │               │ Code     │
0xFFFF └──────────┘        0xFFFF └──────────┘

Both think they have full memory space!
OS maps virtual → physical memory via PAGE TABLES
```

### 15.6 Process Termination Cleanup

When a process dies, OS does:
```
1. Close all open file handles
2. Release all memory pages
3. Terminate all threads
4. Notify parent process (exit code)
5. Clean up kernel resources (sockets, locks, etc.)
```

### 15.7 What If JVM Keeps Running After OOM?

| Scenario | Who Cleans Up |
|----------|---------------|
| JVM terminates | **OS** reclaims all memory |
| JVM keeps running after OOM (caught) | **GC** cleans unreachable objects |
| Normal execution | **GC** runs periodically |

```java
// OOM scenario that JVM might recover from
try {
    List<byte[]> list = new ArrayList<>();
    while (true) {
        list.add(new byte[1_000_000]);
    }
} catch (OutOfMemoryError e) {
    // Caught! JVM still running
    // list goes out of scope → eligible for GC
    System.out.println("Recovered!");
    // GC can now clean up the list
}
```

---

## 16. OutOfMemoryError: Metaspace Deep Dive

### 16.1 What's Stored in Metaspace?

```
┌─────────────────────────────────────────────────────────────┐
│                      METASPACE                              │
├─────────────────────────────────────────────────────────────┤
│  • Class metadata (class definitions)                       │
│  • Method bytecode                                          │
│  • Constant pool (per class)                                │
│  • Annotations                                              │
│  • Static variables (references, not objects)               │
│  • Method counters (for JIT)                                │
└─────────────────────────────────────────────────────────────┘
```

### 16.2 When Does Metaspace OOM Occur?

| Cause | Example |
|-------|---------|
| **Too many classes loaded** | Large frameworks, many dependencies |
| **Class loader leak** | Webapp redeployments in Tomcat |
| **Dynamic class generation** | Heavy use of reflection, proxies, CGLIB, Javassist |
| **Large frameworks** | Spring, Hibernate generate runtime proxies |
| **Groovy/Scala** | Create classes dynamically at runtime |

### 16.3 Why `new Person()` Won't Cause Metaspace OOM

```java
// This will NEVER cause Metaspace OOM!
List<Person> people = new ArrayList<>();
while (true) {
    people.add(new Person());  // 💥 OOM: Java HEAP space (not Metaspace!)
}
```

**Because:**

| What You Do | What Happens | Where |
|-------------|--------------|-------|
| `new Person()` | Creates new **object instance** | HEAP |
| Load `Person.class` | Loads class **definition** (happens ONCE) | METASPACE |

```
When you write: Person p1 = new Person();
                Person p2 = new Person();
                Person p3 = new Person();

┌─────────────────────────────────────────────────────────────────┐
│                         METASPACE                               │
├─────────────────────────────────────────────────────────────────┤
│   ┌─────────────────────────────────────┐                      │
│   │  Person.class (LOADED ONLY ONCE!)   │                      │
│   └─────────────────────────────────────┘                      │
│   No matter how many `new Person()` you call,                  │
│   Person.class is loaded ONCE.                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                            HEAP                                 │
├─────────────────────────────────────────────────────────────────┤
│   ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐          │
│   │ Person  │  │ Person  │  │ Person  │  │ Person  │   ...    │
│   │ (p1)    │  │ (p2)    │  │ (p3)    │  │ (p4)    │          │
│   └─────────┘  └─────────┘  └─────────┘  └─────────┘          │
│   Each `new Person()` creates a new object HERE                │
│   💥 Eventually: OOM: Java heap space                          │
└─────────────────────────────────────────────────────────────────┘
```

**Key Insight:**
> **Class = Blueprint (Metaspace)** - loaded once  
> **Object = Instance from blueprint (Heap)** - created many times

### 16.4 Sample Code to Trigger Metaspace OOM

```java
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Run with: java -XX:MaxMetaspaceSize=50m MetaspaceOOMDemo
 * This will trigger: OutOfMemoryError: Metaspace
 */
public class MetaspaceOOMDemo {

    public static void main(String[] args) {
        List<Class<?>> classes = new ArrayList<>();
        int counter = 0;
        
        try {
            while (true) {
                // Dynamically create new classes using Proxy
                // Each proxy creates a NEW class definition in Metaspace
                Class<?> proxyClass = Proxy.getProxyClass(
                    MetaspaceOOMDemo.class.getClassLoader(),
                    new Class<?>[] { DummyInterface.class }
                );
                
                // Keep reference to prevent unloading
                classes.add(proxyClass);
                counter++;
                
                if (counter % 1000 == 0) {
                    System.out.println("Loaded " + counter + " classes");
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("💥 " + e.getMessage());
            System.out.println("Total classes loaded: " + counter);
        }
    }
    
    interface DummyInterface {
        void doSomething();
    }
}
```

### 16.5 Metaspace Usage Breakdown in Real Apps

```
┌─────────────────────────────────────────────────────────────────┐
│                    Metaspace Usage Breakdown                    │
├─────────────────────────────────────────────────────────────────┤
│  Your Code            ██░░░░░░░░░░░░░░░░░░  (5-10%)            │
│  Spring Framework     ██████░░░░░░░░░░░░░░  (15-20%)           │
│  Hibernate/JPA        ████░░░░░░░░░░░░░░░░  (10-15%)           │
│  3rd Party Libs       ████████░░░░░░░░░░░░  (20-30%)           │
│  JDK Classes          ██████████░░░░░░░░░░  (25-35%)           │
│  Dynamic Proxies      ████░░░░░░░░░░░░░░░░  (varies wildly)    │
└─────────────────────────────────────────────────────────────────┘
```

### 16.6 How to Fix Metaspace OOM

#### Solution 1: Increase Metaspace Size

```bash
# For large Spring Boot apps
java -XX:MaxMetaspaceSize=512m -XX:MetaspaceSize=128m -jar app.jar
```

| Flag | Purpose |
|------|---------|
| `-XX:MetaspaceSize=128m` | Initial size (triggers GC when exceeded) |
| `-XX:MaxMetaspaceSize=512m` | Hard limit |

#### Solution 2: Enable Class Unloading

```bash
java -XX:+UseG1GC \
     -XX:+ClassUnloadingWithConcurrentMark \
     -jar app.jar
```

#### Solution 3: Spring-Specific Optimizations

```yaml
# application.yml - Reduce Spring proxy generation
spring:
  aop:
    proxy-target-class: false  # Use JDK proxies instead of CGLIB
  jpa:
    properties:
      hibernate:
        enable_lazy_load_no_trans: false
```

```java
// Use @Lazy to defer bean creation
@Service
@Lazy
public class HeavyService {
    // Class only loaded when first accessed
}
```

#### Solution 4: Decision Flowchart

```
Metaspace OOM?
      │
      ▼
┌─────────────────────┐
│ Is it growing       │
│ continuously?       │
└─────────┬───────────┘
          │
    ┌─────┴─────┐
    │           │
   YES          NO
    │           │
    ▼           ▼
┌─────────┐  ┌─────────────────────┐
│ You have│  │ Just need more      │
│ a LEAK  │  │ initial space       │
└────┬────┘  └──────────┬──────────┘
     │                  │
     ▼                  ▼
┌─────────────┐  ┌─────────────────────┐
│ Find leak:  │  │ Increase:           │
│ • Heap dump │  │ -XX:MaxMetaspaceSize│
│ • ClassLoader│  │ -XX:MetaspaceSize   │
│   analysis  │  └─────────────────────┘
└─────────────┘
```

### 16.7 Typical Metaspace Values

| App Size | MaxMetaspaceSize | MetaspaceSize |
|----------|------------------|---------------|
| Small (few deps) | 128m | 64m |
| Medium (typical) | 256m | 128m |
| Large (many libs) | 512m | 256m |
| Monolith | 1g+ | 512m |

---

## 17. ClassLoader Isolation: The Design Rationale

### 17.1 Why Does Same Class Create Different Definitions?

```java
ClassLoader loader1 = new CustomClassLoader();
ClassLoader loader2 = new CustomClassLoader();

Class<?> class1 = loader1.loadClass("Person");
Class<?> class2 = loader2.loadClass("Person");

System.out.println(class1 == class2);  // false! Different classes!
```

**This is a DELIBERATE and POWERFUL design decision!**

### 17.2 The Problem It Solves: VERSION CONFLICTS

**Without ClassLoader Isolation (Nightmare):**

```
Your Application needs:
├── Library A → requires Guava 18.0
├── Library B → requires Guava 31.0
└── Library C → requires Guava 23.0

❌ WITHOUT ClassLoader isolation:
   Only ONE Guava version can exist!
   
   Guava 18: Maps.newHashMap() 
   Guava 31: Maps.newHashMap() ← signature changed!
   
   💥 NoSuchMethodError / ClassCastException at runtime
```

**With ClassLoader Isolation (Solution):**

```
┌─────────────────────────────────────────────────────────────────┐
│                      YOUR APPLICATION                           │
├─────────────────────────────────────────────────────────────────┤
│  ClassLoader A                  ClassLoader B                   │
│  ┌─────────────────┐           ┌─────────────────┐             │
│  │ Library A       │           │ Library B       │             │
│  │ Guava 18.0      │           │ Guava 31.0      │             │
│  │ Maps.class v18  │           │ Maps.class v31  │             │
│  └─────────────────┘           └─────────────────┘             │
│                                                                 │
│  ✅ BOTH versions coexist! No conflict!                        │
└─────────────────────────────────────────────────────────────────┘
```

### 17.3 Real-World Use Cases

#### Use Case 1: Application Servers (Tomcat, WildFly)

```
┌─────────────────────────────────────────────────────────────────┐
│                    TOMCAT SERVER                                │
├─────────────────────────────────────────────────────────────────┤
│  System ClassLoader (Tomcat's own classes)                      │
│          │                                                      │
│    ┌─────┴─────┬─────────────┬─────────────┐                   │
│    ▼           ▼             ▼             ▼                    │
│  ┌─────┐    ┌─────┐      ┌─────┐      ┌─────┐                  │
│  │App 1│    │App 2│      │App 3│      │App 4│                  │
│  │Spring│   │Spring│     │No   │      │Spring│                 │
│  │5.0   │    │6.0  │      │Spring│     │5.3   │                 │
│  └─────┘    └─────┘      └─────┘      └─────┘                  │
│                                                                 │
│  Each webapp has its OWN ClassLoader!                          │
│  App1's Spring 5.0 ≠ App2's Spring 6.0                         │
└─────────────────────────────────────────────────────────────────┘
```

#### Use Case 2: Plugin Systems (IDE, Jenkins, JIRA)

```
┌─────────────────────────────────────────────────────────────────┐
│                     INTELLIJ IDEA                               │
├─────────────────────────────────────────────────────────────────┤
│  Core IDE ClassLoader                                           │
│          │                                                      │
│    ┌─────┴─────┬─────────────┬─────────────┐                   │
│    ▼           ▼             ▼             ▼                    │
│  ┌──────┐  ┌──────┐     ┌──────┐     ┌──────┐                  │
│  │Lombok│  │Check │     │Spot  │     │Your  │                  │
│  │Plugin│  │Style │     │Bugs  │     │Plugin│                  │
│  └──────┘  └──────┘     └──────┘     └──────┘                  │
│                                                                 │
│  Plugins can be loaded/unloaded dynamically!                   │
└─────────────────────────────────────────────────────────────────┘
```

#### Use Case 3: Hot Deployment

```java
public class HotReloader {
    public void reloadClass() {
        // 1. Discard old ClassLoader (makes old classes eligible for GC)
        oldClassLoader = null;
        
        // 2. Create new ClassLoader
        newClassLoader = new URLClassLoader(jarPath);
        
        // 3. Load fresh version of class
        Class<?> freshClass = newClassLoader.loadClass("com.app.Service");
        
        // Now running updated code WITHOUT JVM restart!
    }
}
```

### 17.4 The Design Principle: Class Identity

In Java, a class is identified by:

```
Class Identity = Fully Qualified Name + ClassLoader
```

```java
Class<?> personA = classLoaderA.loadClass("com.example.Person");
Class<?> personB = classLoaderB.loadClass("com.example.Person");

System.out.println(personA == personB);        // false!
System.out.println(personA.equals(personB));   // false!

Object objA = personA.newInstance();
Object objB = personB.newInstance();

personA.cast(objB);  // 💥 ClassCastException!
// Even though both are "Person", they're incompatible types!
```

### 17.5 Problems Solved by ClassLoader Isolation

| Problem | How ClassLoader Isolation Solves It |
|---------|-------------------------------------|
| **Dependency Hell** | Different versions of same library coexist |
| **Hot Deployment** | Unload old classes, load new without restart |
| **Plugin Systems** | Plugins are isolated, can be added/removed dynamically |
| **Security** | Untrusted code can't access trusted classes |
| **Multi-Tenancy** | Tenant isolation in shared JVM |
| **App Servers** | Multiple apps with different dependencies |

### 17.6 The Trade-off

```
✅ BENEFITS                        ❌ COSTS
─────────────────────────────────────────────────────
• Version isolation               • More Metaspace usage
• Hot deployment                  • ClassCastException surprises
• Security boundaries             • Complexity in debugging
• Plugin architectures            • Potential memory leaks
• Multi-tenancy                   • "Class not found" issues
```

### 17.7 Real-World Metaspace Leak: Webapp Hot Redeploy

This is why webapp redeployment causes Metaspace leaks:

```
Deploy v1 → Load 5000 classes (ClassLoader A)
Undeploy v1... but ClassLoader A not GC'd due to leak
Deploy v2 → Load 5000 classes (ClassLoader B)
Undeploy v2... but ClassLoader B not GC'd
Deploy v3 → Load 5000 classes (ClassLoader C)

Metaspace now has 15000 class definitions! 💥
```

---

## 📝 Quick Revision Checklist (Final)

### Memory Basics
- [ ] Can explain heap structure (Eden, Survivor, Old Gen)
- [ ] Know what lives on Stack vs Heap
- [ ] Understand Stack frames (method calls) vs Heap objects

### Garbage Collection
- [ ] Know the difference between Minor, Major, and Full GC
- [ ] Can read and interpret GC logs
- [ ] Understand why GC triggers at specific memory thresholds
- [ ] Know real-world scenarios that cause GC pressure
- [ ] Can recommend solutions for flash sale scenarios
- [ ] Familiar with JVM tuning flags
- [ ] Can choose appropriate GC algorithm for use case

### OutOfMemoryError
- [ ] **Know all 9 types of OutOfMemoryError**
- [ ] **Know StackOverflowError is NOT an OOM (sibling classes)**
- [ ] Understand Java Heap Space vs GC Overhead Limit OOM
- [ ] **Understand Metaspace OOM causes and fixes**

### Tools & Analysis
- [ ] Know tools: JConsole, VisualVM, GCViewer, GCEasy.io, Eclipse MAT
- [ ] Can use Eclipse MAT to find memory leaks
- [ ] Know how to generate heap dumps

### Advanced Concepts
- [ ] **Understand OS reclaims memory when JVM crashes**
- [ ] **Know why ClassLoader isolation exists (version conflicts)**
- [ ] **Understand Class = Blueprint (Metaspace) vs Object = Instance (Heap)**

---

## 🎯 Interview Quick Answers

### "What are all the types of OutOfMemoryError?"
> "JVM can throw 9 types: Java heap space, GC overhead limit exceeded, Requested array size exceeds VM limit, Metaspace (Java 8+), PermGen (Java 7-), Unable to create native thread, Kill process or sacrifice child, reason stack_trace_with_native_method, and Direct buffer memory."

### "Is StackOverflowError a type of OutOfMemoryError?"
> "No, they're sibling classes under VirtualMachineError. StackOverflowError occurs when method call depth exceeds stack size. OOM occurs when objects can't be allocated. Stack = method frames, Heap = objects."

### "Who clears memory when JVM crashes?"
> "The Operating System. JVM is just a process to the OS. When the process terminates, the OS reclaims all memory assigned to it via its virtual memory management system. The JVM's GC only manages memory while the process is running."

### "When does Metaspace OOM occur?"
> "When too many class definitions are loaded. Common causes: large frameworks generating runtime proxies (Spring AOP, Hibernate), classloader leaks during hot redeploys, and dynamic class generation (CGLIB, Groovy). Fix: increase MaxMetaspaceSize, enable class unloading, or find the leak."

### "Why does the same class have different definitions with different ClassLoaders?"
> "By design! Class identity = name + ClassLoader. This solves dependency hell — multiple versions of the same library can coexist. It enables app servers to run multiple webapps with different framework versions, plugin systems to load/unload independently, and hot deployment without JVM restart."
