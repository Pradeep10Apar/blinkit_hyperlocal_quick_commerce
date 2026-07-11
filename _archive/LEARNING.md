# 📚 Technical Learning Notes — Blinkit Phase 1

A collection of key Spring Boot & JPA concepts learned while building this project.

---

## Table of Contents
- [1. @Service Annotation](#1-service-annotation)
- [2. @Entity vs @Service — Don't Mix Them Up](#2-entity-vs-service--dont-mix-them-up)
- [3. @Transactional — Why It Matters](#3-transactional--why-it-matters)
- [4. @Builder (Lombok) — Clean Object Construction](#4-builder-lombok--clean-object-construction)
- [5. Order–OrderItem Design Pattern (Database Normalization)](#5-orderorderitem-design-pattern-database-normalization)
- [6. @OneToMany / @ManyToOne — Bidirectional JPA Relationships](#6-onetomany--manytoone--bidirectional-jpa-relationships)
- [7. Cascade & OrphanRemoval — Parent Controls Child Lifecycle](#7-cascade--orphanremoval--parent-controls-child-lifecycle)
- [8. Bidirectional References — Is It Cyclic?](#8-bidirectional-references--is-it-cyclic)
- [9. Kafka Consumer Groups & Partition Assignment](#9-kafka-consumer-groups--partition-assignment)
- [10. Data Integrity Across System Boundaries — The UUID Bug](#10-data-integrity-across-system-boundaries--the-uuid-bug)
- [11. Transactional Outbox Pattern](#11-transactional-outbox-pattern)
- [12. CQRS-lite — Separate Read and Write Models](#12-cqrs-lite--separate-read-and-write-models)
- [13. Kafka Fan-out — One Topic, Multiple Consumer Groups](#13-kafka-fan-out--one-topic-multiple-consumer-groups)
- [14. Order Status State Machine](#14-order-status-state-machine)
- [15. Partial Fulfillment Pattern — quantity_ordered vs quantity_fulfilled](#15-partial-fulfillment-pattern--quantity_ordered-vs-quantity_fulfilled)
- [16. @PathVariable vs @RequestParam vs @RequestBody — Three Ways Data Enters a Controller](#16-pathvariable-vs-requestparam-vs-requestbody--three-ways-data-enters-a-controller)
- [17. Kubernetes Declarative State Management — Why Pods Auto-Restart](#17-kubernetes-declarative-state-management--why-pods-auto-restart)
- [18. Docker Context Issues — Multiple Docker Daemons](#18-docker-context-issues--multiple-docker-daemons)
- [19. Eureka Service Discovery in Kubernetes — The IP vs Hostname Problem](#19-eureka-service-discovery-in-kubernetes--the-ip-vs-hostname-problem)
- [20. Spring Boot Relaxed Binding — Environment Variables to Properties](#20-spring-boot-relaxed-binding--environment-variables-to-properties)
- [21. kubectl Context — Connecting to Different Clusters](#21-kubectl-context--connecting-to-different-clusters)
- [22. Kubernetes YAML Anatomy — The 4 Essential Parts](#22-kubernetes-yaml-anatomy--the-4-essential-parts)

---

## 1. @Service Annotation

**Package:** `org.springframework.stereotype.Service`

### What it does
- Marks a class as a **Spring-managed bean** — Spring auto-detects it during component scanning and registers a singleton instance in the Application Context (IoC container).
- Provides **semantic clarity** — signals that this class holds **business logic**.

### Spring stereotype annotations at a glance

| Annotation                    | Intended Layer         |
|-------------------------------|------------------------|
| `@Controller` / `@RestController` | Web / API layer    |
| **`@Service`**                | **Business logic**     |
| `@Repository`                 | Data access layer      |
| `@Component`                  | Generic / utility      |

### What happens without it?
- Spring **won't register** the class as a bean.
- Any class trying to inject it (`@Autowired` or constructor injection) will fail at startup:
  ```
  NoSuchBeanDefinitionException: No qualifying bean of type 'OrderService' available
  ```
- **The application will fail to start** if any bean depends on it.

**In short:** `@Service` = "Spring, please manage this class and let others inject it."

---

## 2. @Entity vs @Service — Don't Mix Them Up

| Aspect         | `@Service` (Spring)                    | `@Entity` (JPA / Hibernate)            |
|----------------|----------------------------------------|----------------------------------------|
| **Package**    | `org.springframework.stereotype.Service` | `jakarta.persistence.Entity`         |
| **Purpose**    | Registers as a Spring bean (business logic) | Maps class to a database table      |
| **Managed by** | Spring IoC container                   | JPA / Hibernate ORM                    |

### What goes wrong if you put `@Entity` on a service class?

1. **Not a Spring bean** — injection fails with `NoSuchBeanDefinitionException`.
2. **Hibernate tries to map it as a table** — fails because:
   - No `@Id` field → `AnnotationException: No identifier specified`
   - Fields like `Repository`, `ObjectMapper` aren't DB columns → mapping errors.
3. **Application crashes at startup** with multiple errors.

**Takeaway:** `@Entity` = database table, `@Service` = injectable business logic. Wrong annotation = everything breaks.

---

## 3. @Transactional — Why It Matters

**Package:** `jakarta.transaction.Transactional`

### What it does
Wraps the entire method in a **single database transaction**. Either **all** DB operations succeed together, or **all** roll back — guaranteeing **atomicity**.

### Real example from `ProductService.create()`

```java
@Transactional
public ProductEntity create(CreateProductRequest req) {
    ProductEntity saved = repo.save(entity);           // DB Write #1
    outboxRepository.save(outboxEvent);                // DB Write #2
    return saved;
}
```

### With `@Transactional` ✅

```
BEGIN TRANSACTION
  repo.save(entity)           → INSERT into products        ✅
  outboxRepository.save(...)  → INSERT into product_outbox   ✅
COMMIT
```

If the second write fails → **both are rolled back**. Clean state.

### Without `@Transactional` ❌

Each save runs in its own auto-commit transaction:

```
Auto-commit TX 1: repo.save(entity)           → COMMITTED ✅
Auto-commit TX 2: outboxRepository.save(...)  → EXCEPTION  ❌
```

**Result:** Product exists in DB, but outbox event is missing → Elasticsearch never gets notified → product is invisible in search. This is a **partial write** — the worst kind of silent bug.

### Rule of thumb
Any service method with **2+ database writes** (or read-then-write) should be `@Transactional`.

---

## 4. @Builder (Lombok) — Clean Object Construction

**Package:** `lombok.Builder`

### The problem without it
A class with many fields (like `ProductEntity` with 9 fields) requires ugly constructor calls:

```java
// Which argument is which? Easy to swap brand ↔ category (both String)!
new ProductEntity(id, "Milk", "Amul", "Dairy", price, true, null, now, now);
```

### With `@Builder` ✅

```java
ProductEntity.builder()
    .id(id)
    .name("Milk")
    .brand("Amul")
    .category("Dairy")
    .price(new BigDecimal("55.00"))
    .active(true)
    .createdAt(now)
    .updatedAt(now)
    .build();
```

### Why it's better

| Aspect                  | Constructor           | Builder                          |
|-------------------------|-----------------------|----------------------------------|
| Readability             | ❌ Positional args    | ✅ Named fields                  |
| Swapping args by mistake| ❌ Silent bugs        | ✅ Impossible                    |
| Optional fields         | ❌ Multiple overloads | ✅ Just skip the field           |
| Adding new fields later | ❌ Breaks all callers | ✅ Existing code still compiles  |

Lombok auto-generates the entire `Builder` inner class at compile time — you never write the boilerplate yourself.

---

## 5. Order–OrderItem Design Pattern (Database Normalization)

### Why you can't just use `List<ProductEntity>` in an Order

| Info needed           | Available on ProductEntity? |
|-----------------------|-----------------------------|
| Which products?       | ✅ Product IDs              |
| How many of each?     | ❌ No `quantity` field       |
| Price at order time?  | ❌ Only *current* price      |

If a product's price changes tomorrow, past orders should still show what the customer **actually paid**.

### The correct design — Two entities

```
ORDER #abc-123                         ← OrderEntity.id
├── Line Item #item-001                ← OrderItemEntity.id
│     Milk × 2 @ ₹55 each
├── Line Item #item-002                ← OrderItemEntity.id
│     Bread × 3 @ ₹40 each
└── Total: ₹230
```

In the database:

```
orders table                    order_items table
┌──────────┬─────────┐         ┌──────────┬──────────┬─────┬───────┐
│ id       │ user_id │         │ id       │ order_id │ qty │ price │
├──────────┼─────────┤         ├──────────┼──────────┼─────┼───────┤
│ abc-123  │ user-1  │◀────────│ item-001 │ abc-123  │  2  │ 55.00 │
│          │         │◀────────│ item-002 │ abc-123  │  3  │ 40.00 │
└──────────┴─────────┘         └──────────┴──────────┴─────┴───────┘
```

### Why OrderItemEntity stores snapshots

| Field          | Why it exists                                              |
|----------------|------------------------------------------------------------|
| `productName`  | Product may be renamed later — order keeps original name   |
| `unitPrice`    | Price may change — order keeps the price customer paid     |
| `quantity`     | How many units — doesn't exist on ProductEntity            |

**Real-world analogy:** Every app — Amazon, Swiggy, Blinkit — uses this Order → OrderItems pattern. The two IDs are not redundant; `OrderEntity.id` = receipt number, `OrderItemEntity.id` = each line on the receipt.

---

## 6. @OneToMany / @ManyToOne — Bidirectional JPA Relationships

### The annotations

**Parent side** (`OrderEntity`):
```java
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
private List<OrderItemEntity> items = new ArrayList<>();
```

**Child side** (`OrderItemEntity`):
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "order_id", nullable = false)
private OrderEntity order;
```

### `mappedBy = "order"`

Tells JPA: *"The FK column lives on the other side — look at the `order` field in OrderItemEntity."*

| Scenario              | Tables created                                              |
|-----------------------|-------------------------------------------------------------|
| Without `mappedBy`    | `orders` + `order_items` + `orders_order_items` (join table) ❌ |
| With `mappedBy`       | `orders` + `order_items` (FK in order_items) ✅              |

The `@JoinColumn(name = "order_id")` on the child side creates the actual foreign key column.

---

## 7. Cascade & OrphanRemoval — Parent Controls Child Lifecycle

### `cascade = CascadeType.ALL`

Whatever you do to the Order, JPA automatically does to its Items:

```java
// Save order → automatically saves all items too
order.getItems().add(milk);
order.getItems().add(bread);
orderRepository.save(order);   // saves order + milk + bread in one call!
```

Without cascade, you'd manually save every item separately.

| Cascade Type | Effect                            |
|--------------|-----------------------------------|
| `PERSIST`    | Save order → saves items too      |
| `MERGE`      | Update order → updates items too  |
| `REMOVE`     | Delete order → deletes items too  |
| `ALL`        | **All of the above**              |

### `orphanRemoval = true`

Removing an item from the list **deletes it from the database**:

```java
order.getItems().remove(milk);     // removed from list
orderRepository.save(order);       // milk row DELETED from order_items table
```

Without `orphanRemoval`, the milk row stays in the DB as an orphan — pointing to the order but no longer referenced.

**Analogy:**
- `cascade = ALL` → Shredding the receipt (order) shreds all lines (items) too
- `orphanRemoval`  → Crossing out a line item erases it permanently
- `mappedBy`       → Each line item knows which receipt it belongs to

---

## 8. Bidirectional References — Is It Cyclic?

`OrderEntity` → `List<OrderItemEntity>` and `OrderItemEntity` → `OrderEntity` — this **is** a circular reference, but it's **intentional and safe** in JPA.

### Why it works

| Concern                     | Problem?     | Reason                                                        |
|-----------------------------|--------------|---------------------------------------------------------------|
| Java objects referencing each other | ✅ Fine   | Just pointer assignments, no construction loop              |
| Database foreign keys       | ✅ Fine       | Only **one** FK exists (`order_items.order_id`), controlled by `mappedBy` |
| JSON serialization          | ⚠️ **Can break** | Jackson would infinitely recurse — use DTOs or `@JsonIgnore` |

### How to avoid the JSON infinite loop

- **Best practice:** Return **DTO objects** from controllers, not entities directly.
- **Quick fix:** Add `@JsonIgnore` on the `@ManyToOne` side:
  ```java
  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  private OrderEntity order;
  ```

---

## 9. Kafka Consumer Groups & Partition Assignment

### Those repeating INFO logs at startup

When your app starts, you see logs like:
```
Setting offset for partition product-events-0 to the committed offset FetchPosition{offset=6010, ...}
partitions assigned: [product-events-0]
```

### What's actually happening

1. **Consumer group protocol** — Your `@KafkaListener` joins the consumer group `blinkit-phase1`.
2. **Partition assignment** — Kafka's **Group Coordinator** assigns partitions to consumers in the group.
3. **Offset restore** — The consumer resumes from its last committed offset (e.g., 6010), so it doesn't re-read old messages.

This is **normal startup behavior**, not an error.

### When it becomes a problem

If these logs repeat **continuously during runtime** (not just at startup), it means a **consumer rebalance loop**:
- Consumer takes too long to process a batch (exceeds `max.poll.interval.ms`)
- Unhandled exception in `onMessage()` crashes the listener container, which auto-restarts
- Network instability causing repeated disconnects

### How to suppress the noise

Add to `application.yml`:
```yaml
logging:
  level:
    org.apache.kafka: WARN
    org.springframework.kafka: WARN
```

This hides INFO-level Kafka chatter while still showing actual errors.

### Key concept — Offsets

| Term | Meaning |
|---|---|
| **Offset** | Sequential ID of each message in a partition (0, 1, 2, ...) |
| **Committed offset** | Last offset the consumer confirmed as processed |
| **auto-offset-reset: earliest** | If no committed offset exists, start from message 0 |
| **auto-offset-reset: latest** | If no committed offset exists, start from newest message only |

Spring Boot sets `enable.auto.commit = false` by default — it manages offset commits after your listener successfully processes each message.

---

## 10. Data Integrity Across System Boundaries — The UUID Bug

### The bug

Search results from Elasticsearch returned products to the UI, but "Add to Cart" failed with 404:
```json
{"status": 404, "message": "Product not found: dd1892b6-9df9-4933-a4c4-53e5e42a6cc2"}
```

### Root cause

In `ProductSearchService.parseHits()`, the Elasticsearch `_id` was correctly extracted but then **discarded**:

```java
// ❌ BUG: returns a RANDOM UUID every time — not the real product ID!
String id = h.path("_id").asText(null);   // extracted correctly
ProductResponse pr = new ProductResponse(
        UUID.randomUUID(),                 // ← threw away 'id', generated fake one
        ...
);
```

Every search result got a **brand-new fake UUID** on every request. When the UI sent this fake UUID to `POST /api/cart/items`, the `CartService` looked it up in PostgreSQL → not found → 404.

### The fix

```java
// ✅ FIX: use the actual product ID from Elasticsearch
UUID.fromString(id)
```

### The lesson — System boundary data integrity

When data flows across system boundaries (Postgres → Kafka → Elasticsearch → API → UI → API → Postgres), **every ID must be faithfully carried through**. One broken link in the chain causes silent failures downstream.

```
Postgres ID ──→ Kafka payload ──→ ES _id ──→ API response ──→ UI ──→ Cart API ──→ Postgres lookup
   ✅              ✅               ✅         ❌ randomUUID()   ❌        ❌ 404!
```

**Rule:** Never generate new IDs for data that already has one. Always trace IDs end-to-end.

---

## 11. Transactional Outbox Pattern

### The problem it solves

You need to **write to a database AND publish to Kafka** atomically. But they're two different systems — you can't wrap them in a single transaction.

```
// ❌ Dual-write problem
repo.save(product);                    // DB write succeeds
kafkaTemplate.send("topic", payload);  // Kafka send fails — network error!
// Result: product in DB, but Elasticsearch never knows about it
```

### The solution — Write to ONE system, relay to the other

```java
@Transactional
public ProductEntity create(...) {
    repo.save(product);                  // DB write #1
    outboxRepository.save(outboxEvent);  // DB write #2 (same transaction!)
    // Kafka is NOT called here
}
```

Both writes are in the **same Postgres transaction** — they either both succeed or both roll back. No inconsistency possible.

### The relay — Scheduled poller

```java
@Scheduled(fixedDelay = 1000)  // every 1 second
public void publish() {
    var batch = repo.findNextNew(500);       // SELECT WHERE status='NEW'
    for (var e : batch) {
        kafkaTemplate.send("product-events", e.getPayload());
        e.setStatus("PUBLISHED");            // mark as done
        repo.save(e);
    }
}
```

### Why it's reliable

| Scenario | What happens |
|---|---|
| Kafka send succeeds | Outbox row marked `PUBLISHED` ✅ |
| Kafka send fails | Row stays `NEW`, retried next poll cycle ✅ |
| App crashes after DB write | Outbox row persists, picked up on restart ✅ |
| App crashes after Kafka send but before marking PUBLISHED | Message sent twice (consumer must be **idempotent**) ⚠️ |

### Guarantee: At-least-once delivery

The outbox guarantees the event **will** reach Kafka eventually, but it may arrive **more than once** (if the app crashes between send and status update). Your consumer must handle duplicates — typically by using the document ID as the ES `_id` (upsert = idempotent).

### Outbox table lifecycle

```
NEW ──→ PUBLISHED    (happy path)
 NEW ──→ retry ──→ retry ──→ ... ──→ FAILED   (after 10 attempts)
```

---

## 12. CQRS-lite — Separate Read and Write Models

**CQRS** = Command Query Responsibility Segregation

### What it means in this project

| Operation | Goes to | Why |
|---|---|---|
| **Writes** (create/update product) | PostgreSQL | ACID transactions, source of truth |
| **Reads** (search products) | Elasticsearch | Full-text search, fuzzy matching, fast |

The two databases serve **different purposes** and have **different schemas**.

### Why not just use PostgreSQL for search?

Postgres `LIKE '%milk%'` works for small data, but:
- No **fuzziness** (typo "mlk" won't match "milk")
- No **relevance scoring** (can't rank "Amul Milk" higher than "Milk Chocolate")
- No **field boosting** (name match > category match)
- **Slow** on large datasets without full-text indexes

Elasticsearch is purpose-built for this.

### Why not just use Elasticsearch for everything?

- ES is **eventually consistent** — not suitable for transactional writes
- No **ACID transactions** — can't atomically write order + order_items
- No **foreign keys or joins** — relational data doesn't fit
- **Not the source of truth** — if ES index is deleted, rebuild from Postgres

### The sync mechanism

```
Postgres (write) ──→ Outbox ──→ Kafka ──→ Consumer ──→ Elasticsearch (read)
```

There's a **small delay** (~1-2 seconds) between writing to Postgres and the data appearing in ES search results. This is called **eventual consistency** and is acceptable for search.

---

## 13. Kafka Fan-out — One Topic, Multiple Consumer Groups

### The concept

Multiple services can **independently consume** the same Kafka topic by using **different consumer group IDs**.

```
              Kafka Topic: "order-events"
                        │
         ┌──────────────┼──────────────┐
         ▼              ▼              ▼
   group: store    group: notif    group: query
   ┌──────────┐    ┌──────────┐    ┌──────────┐
   │  Store   │    │Notification│   │  Order   │
   │ Service  │    │  Service  │    │  Indexer │
   └──────────┘    └──────────┘    └──────────┘
```

### Key rules

| Scenario | Behavior |
|---|---|
| Same `group.id` | Messages are **split** among consumers (load balancing) |
| Different `group.id` | Each group gets **every** message (fan-out / broadcast) |

### Why this matters

When `OrderService` publishes an `ORDER_PLACED` event, **all three** consumers receive it independently:
- **Store Service** → notifies the dark store to start picking
- **Notification Service** → sends SMS/push/email to customer
- **Order Indexer** → indexes order into ES for fast queries

No extra code needed — Kafka handles the fan-out. Each consumer tracks its own offset.

### Existing example in this project

`ProductIndexConsumer` uses `groupId = "blinkit-phase1"`. If you added a second consumer on `product-events` with a different group ID, both would receive every product event.

---

## 14. Order Status State Machine

### The lifecycle

An order goes through a **defined sequence of states**:

```
PLACED ──→ ACCEPTED ──→ PICKING ──→ PACKED ──→ OUT_FOR_DELIVERY ──→ DELIVERED
  │            │
  │            ▼
  │        MODIFIED (partial fulfillment)
  │            │
  ▼            ▼
CANCELLED   CANCELLED
  │
  ▼
REFUNDED
```

### Why use an enum, not a String

```java
// ❌ String — typos are silent bugs
order.setStatus("PALCED");  // typo! no compile error, no runtime error

// ✅ Enum — compiler catches mistakes
order.setStatus(OrderStatus.PALCED);  // ❌ won't compile!
```

### JPA mapping with `@Enumerated`

```java
@Enumerated(EnumType.STRING)   // stores "PLACED" in DB, not 0
@Column(nullable = false, length = 30)
private OrderStatus status;
```

| `EnumType` | DB stores | Problem |
|---|---|---|
| `ORDINAL` (default) | `0`, `1`, `2`... | Reordering enum values silently corrupts data |
| **`STRING`** | `"PLACED"`, `"PACKED"` | Safe — human-readable, order-independent |

**Always use `EnumType.STRING`.**

### Who changes the status?

| Transition | Triggered by |
|---|---|
| → PLACED | Customer checkout |
| → ACCEPTED | Store app accepts order |
| → PICKING | Store picker starts |
| → MODIFIED | Store marks items unavailable |
| → PACKED | Picker finishes |
| → OUT_FOR_DELIVERY | Rider picks up |
| → DELIVERED | Rider confirms delivery |
| → CANCELLED | Customer or system cancels |

Each transition can publish a Kafka event → Notification Service sends real-time updates to the customer.

---

## 15. Partial Fulfillment Pattern — quantity_ordered vs quantity_fulfilled

### The problem

Customer orders 5 items → store only has 3. What happens?

### The solution — Track both quantities

```sql
order_items table:
┌──────────┬──────────────────┬────────────────────┬────────┐
│ product  │ quantity_ordered  │ quantity_fulfilled  │ status │
├──────────┼──────────────────┼────────────────────┼────────┤
│ Milk     │ 2                │ 2                  │ PICKED │
│ Bread    │ 3                │ 3                  │ PICKED │
│ Eggs     │ 1                │ 0                  │ UNAVAILABLE │
│ Butter   │ 2                │ 0                  │ UNAVAILABLE │
│ Cheese   │ 1                │ 1                  │ PICKED │
└──────────┴──────────────────┴────────────────────┴────────┘
```

### Why two quantity fields?

| Field | Meaning | Set when |
|---|---|---|
| `quantity_ordered` | What customer **wanted** | Order placed (never changes) |
| `quantity_fulfilled` | What store **actually gave** | Store picker updates |

### The financial impact

```
orders table:
┌───────────────────┬────────────────┬───────────────────┐
│ original_total     │ final_total    │ adjustment_amount │
├───────────────────┼────────────────┼───────────────────┤
│ ₹500              │ ₹320           │ ₹180 (refund)     │
└───────────────────┴────────────────┴───────────────────┘
```

- `original_total` — calculated at checkout (sum of all `quantity_ordered × unit_price`)
- `final_total` — recalculated after store adjustments (sum of `quantity_fulfilled × unit_price`)
- `adjustment_amount` — difference, triggers refund if prepaid

### The event flow

```
Store marks 2 items UNAVAILABLE
        │
        ▼
OrderService.adjustOrder()
  ├── Update order_items (qty_fulfilled = 0, status = UNAVAILABLE)
  ├── Recalculate final_total
  ├── Outbox event: ORDER_MODIFIED
        │
        ▼  Kafka: "order-events"
        │
    ┌───┴──────────────┐
    ▼                  ▼
 Notification       Payment
    │                  │
 📱 "2 items removed" 💰 Refund ₹180
```

### Why not just reject the whole order?

In quick commerce, **speed matters more than perfection**. Delivering 3 out of 5 items in 10 minutes is better than cancelling and making the customer re-order. This is exactly what Blinkit/Zepto do — you've probably seen the "item unavailable, refund initiated" notification.

---

## 16. @PathVariable vs @RequestParam vs @RequestBody — Three Ways Data Enters a Controller

All three annotations pass data to a controller method, but they extract it from **different parts of an HTTP request**.

### The HTTP request — where does data live?

```
POST /api/products/3fa85f64-5717-4562-b3fc-2c963f66afa6?active=true HTTP/1.1
│                  └──────────── Path ────────────────┘ └─ Query ─┘
│
Content-Type: application/json

{                          ← Body
  "name": "Milk",
  "price": 50.0
}
```

Each annotation targets **one of these three locations**.

---

### 1. `@PathVariable` — from the URL path

**Package:** `org.springframework.web.bind.annotation.PathVariable`

Extracts a value embedded directly **in the URL path itself**, identified by `{placeholder}` in the route.

```java
@GetMapping("/{id}")
public ProductResponse get(@PathVariable UUID id) { ... }
```

```
GET /api/products/3fa85f64-5717-4562-b3fc-2c963f66afa6
                  └──────────────── id ────────────────┘
```

**When to use:** To identify a **specific resource** — "get me THIS product", "delete THIS order".

**Real examples from this project:**
- `GET /api/products/{id}` — fetch one product
- `GET /api/orders/{id}` — fetch one order
- `DELETE /api/cart/items/{productId}` — remove a specific item from cart

---

### 2. `@RequestParam` — from the query string

**Package:** `org.springframework.web.bind.annotation.RequestParam`

Extracts key-value pairs from the **query string** — the part after `?` in the URL.

```java
@GetMapping("/search")
public List<ProductResponse> search(
        @RequestParam String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
) { ... }
```

```
GET /api/products/search?q=milk&page=0&size=20
                         └─q──┘ └page┘ └size┘
```

**When to use:** For **filtering, searching, sorting, pagination** — optional/supplementary inputs that don't identify a single resource.

**Key features:**
- Supports **default values**: `@RequestParam(defaultValue = "0")` — if `page` is missing from URL, it defaults to `0`
- Can be **optional**: `@RequestParam(required = false) String category`
- Multiple params are joined with `&`: `?q=milk&page=2&size=10`

---

### 3. `@RequestBody` — from the HTTP request body (JSON payload)

**Package:** `org.springframework.web.bind.annotation.RequestBody`

Deserializes the **entire HTTP request body** (typically JSON) into a Java object using Jackson.

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ProductResponse create(@Valid @RequestBody CreateProductRequest req) { ... }
```

```
POST /api/products
Content-Type: application/json

{                              ← entire JSON becomes CreateProductRequest
  "name": "Amul Milk",
  "brand": "Amul",
  "category": "Dairy",
  "price": 55.00,
  "active": true
}
```

**When to use:** For sending **complex, structured data** — create or update operations with multiple fields. Typically used with `POST`, `PUT`, `PATCH`.

**Paired with `@Valid`:** Adding `@Valid` triggers Jakarta Bean Validation on the DTO — if `name` is `@NotBlank` and the client sends `"name": ""`, Spring returns a 400 error automatically.

---

### Why not just use one for everything?

| Reason | Explanation |
|---|---|
| **GET requests have no body** | You *can't* use `@RequestBody` for search or fetch operations |
| **Query strings are flat & length-limited** | You *shouldn't* pass a complex object like `CreateProductRequest` (5+ fields) as query params |
| **Path variables identify resources** | `/products/123` is RESTful and cacheable; `/products?id=123` is less idiomatic |
| **REST conventions** | The industry agrees: IDs in path, filters in query, payloads in body |

### Side-by-side comparison from `ProductController`

| Annotation | Data source | Example in this project | Typical HTTP method |
|---|---|---|---|
| `@PathVariable` | URL path segment `/{id}` | `GET /api/products/{id}` | GET, DELETE |
| `@RequestParam` | Query string `?key=value` | `GET /api/products/search?q=milk&page=0` | GET |
| `@RequestBody` | JSON body | `POST /api/products` with JSON payload | POST, PUT, PATCH |

### Bonus: `@RequestPart` — for file uploads

There's a fourth annotation in the controller — `@RequestPart`, used for **multipart form data** (file uploads):

```java
@PostMapping(value = "/bulk/upload-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public BulkUploadResponse uploadCsv(@RequestPart("file") MultipartFile file) { ... }
```

This is similar to `@RequestParam` but specifically designed for `multipart/form-data` requests where the body contains files + other fields.

### Mental model

```
HTTP Request
│
├── URL Path:     /api/products/{id}        ──→  @PathVariable
│
├── Query String: ?q=milk&page=0            ──→  @RequestParam
│
├── JSON Body:    { "name": "Milk", ... }   ──→  @RequestBody
│
└── File Upload:  [binary file data]        ──→  @RequestPart
```

---

## Quick Reference — All Annotations Discussed

| Annotation        | Package                              | Purpose                                        |
|-------------------|--------------------------------------|-------------------------------------------------|
| `@Service`        | `o.s.stereotype.Service`             | Spring bean — business logic layer              |
| `@Entity`         | `jakarta.persistence.Entity`         | JPA — maps class to DB table                    |
| `@Transactional`  | `jakarta.transaction.Transactional`  | Wraps method in a DB transaction                |
| `@Builder`        | `lombok.Builder`                     | Generates Builder pattern for clean construction|
| `@OneToMany`      | `jakarta.persistence.OneToMany`      | Parent side of 1:N relationship                 |
| `@ManyToOne`      | `jakarta.persistence.ManyToOne`      | Child side of N:1 relationship                  |
| `@JoinColumn`     | `jakarta.persistence.JoinColumn`     | Defines the FK column name                      |
| `@Enumerated`     | `jakarta.persistence.Enumerated`     | Maps Java enum to DB column                     |
| `@Column`         | `jakarta.persistence.Column`         | Customizes DB column (name, length, nullable)   |
| `@Table`          | `jakarta.persistence.Table`          | Specifies the DB table name                     |
| `@Id`             | `jakarta.persistence.Id`             | Marks the primary key field                     |
| `@Getter/@Setter` | `lombok.Getter / lombok.Setter`      | Auto-generates getters/setters                  |
| `@NoArgsConstructor` | `lombok.NoArgsConstructor`        | Generates no-arg constructor (JPA needs this)   |
| `@AllArgsConstructor`| `lombok.AllArgsConstructor`        | Generates constructor with all fields           |
| `@RequiredArgsConstructor` | `lombok.RequiredArgsConstructor` | Constructor for `final` fields (used for DI) |
| `@Builder.Default`| `lombok.Builder.Default`             | Sets default value when using Builder           |
| `@Slf4j`          | `lombok.extern.slf4j.Slf4j`         | Auto-generates a `log` field for logging        |
| `@PathVariable`   | `o.s.web.bind.annotation.PathVariable` | Extracts value from URL path segment          |
| `@RequestParam`   | `o.s.web.bind.annotation.RequestParam` | Extracts value from query string `?key=val`   |
| `@RequestBody`    | `o.s.web.bind.annotation.RequestBody`  | Deserializes JSON body into Java object       |
| `@RequestPart`    | `o.s.web.bind.annotation.RequestPart`  | Extracts part from multipart/form-data        |
| `@Valid`          | `jakarta.validation.Valid`             | Triggers bean validation on the annotated param |

---

*Last updated: June 26, 2026 (added Kubernetes deployment sections 17-22)*

---

## 17. Kubernetes Declarative State Management — Why Pods Auto-Restart

### The Question
> "I restarted Rancher Desktop and all my pods came back automatically. I never ran `kubectl apply` again!"

### The Answer — Kubernetes Self-Healing

Kubernetes uses a **declarative model**:

| Approach | How It Works | Example |
|----------|--------------|---------|
| **Imperative** | "Do this action NOW" | `docker run nginx` |
| **Declarative** | "Make sure this state EXISTS" | `kubectl apply -f deployment.yaml` |

When you `kubectl apply`, Kubernetes stores your **desired state** in etcd. The control plane constantly monitors:

```
┌─────────────────────────────────────────────────────────────┐
│                    KUBERNETES RECONCILIATION                 │
│                                                              │
│   Desired State (etcd): "I want 3 nginx pods"               │
│   Actual State: "Currently 2 pods running"                   │
│   Action: "Create 1 more pod"                                │
│                                                              │
│   After restart:                                             │
│   Desired State (etcd): "I want 12 pods"                    │
│   Actual State: "0 pods running"                             │
│   Action: "Create all 12 pods!"                              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**Analogy:** Kubernetes is like a thermostat. You set 68°F, and it constantly adjusts to maintain it — you don't tell it "turn on AC now", you tell it "keep it 68°F".

---

## 18. Docker Context Issues — Multiple Docker Daemons

### The Problem
After Rancher Desktop restart, pods showed `ErrImageNeverPull`:
```
blinkit-app-6f8c8f4b6c-xxxxx   0/1   ErrImageNeverPull   0   5m
```

### Root Cause: Multiple Docker Contexts
Docker CLI can connect to different Docker daemons:

```bash
> docker context ls

NAME              DOCKER ENDPOINT
default           npipe:////./pipe/docker_engine
desktop-linux     Docker Desktop's VM
rancher-desktop * K3s's containerd (for Kubernetes)
```

| Context | Where Images Go | Used By |
|---------|-----------------|---------|
| `default` | Docker Desktop's storage | Docker Desktop |
| `rancher-desktop` | K3s's containerd | Kubernetes |

**If you build in wrong context, images go to wrong storage!**

### The Fix
```bash
# Ensure correct context
docker context use rancher-desktop

# Rebuild images
docker build --no-cache -t blinkit-app:v1 ./blinkit-app/

# Restart deployment
kubectl rollout restart deployment blinkit-app -n blinkit
```

### `imagePullPolicy: Never` Explained

| Policy | Behavior | Use Case |
|--------|----------|----------|
| `Always` | Pull from registry every time | Production (ECR, DockerHub) |
| `IfNotPresent` | Pull only if not in local cache | Reduce bandwidth |
| **`Never`** | Never pull, use local only | **Local dev with K3s** |

`Never` + missing image = `ErrImageNeverPull` (intentional — tells you to build first!)

---

## 19. Eureka Service Discovery in Kubernetes — The IP vs Hostname Problem

### The Problem
API Gateway logged:
```
java.net.UnknownHostException: blinkit-app-7c8d6f5b4d-xxxxx
```

### Root Cause
By default, Eureka instances register with their **hostname**. In K8s, pod hostnames are like `blinkit-app-7c8d6f5b4d-xxxxx`.

Other services can't resolve these hostnames because K8s DNS only knows **service names**, not individual pod names.

```
DEFAULT (❌ BROKEN):
Pod registers: "I'm at hostname blinkit-app-7c8d6f5b4d"
API Gateway tries: connect to blinkit-app-7c8d6f5b4d
Result: UnknownHostException

WITH FIX (✅ WORKS):
Pod registers: "I'm at IP 10.42.0.15"
API Gateway tries: connect to 10.42.0.15
Result: Success!
```

### The Fix
Add to ALL Spring services' ConfigMap:
```yaml
EUREKA_INSTANCE_PREFER_IP_ADDRESS: "true"
```

This makes pods register with their **IP address** instead of hostname.

---

## 20. Spring Boot Relaxed Binding — Environment Variables to Properties

### The Pattern
Spring Boot converts environment variables to property names:

```
Environment Variable    →    Property Name
─────────────────────────────────────────────
APP_ELASTICSEARCH_URL   →    app.elasticsearch.url
APP_ELASTIC_BASE_URL    →    app.elastic.base-url
SPRING_DATASOURCE_URL   →    spring.datasource.url
```

**Conversion rules:**
1. Replace `_` with `.`
2. Convert to lowercase
3. Handle special cases (camelCase)

### Real Example
```yaml
# ConfigMap (environment variable)
APP_ELASTIC_TIMEOUT_SECONDS: "15"

# application.yml
app:
  elastic:
    timeout-seconds: ${APP_ELASTIC_TIMEOUT_SECONDS:10}

# Java code
@ConfigurationProperties(prefix = "app.elastic")
public record ElasticProperties(int timeoutSeconds) {}
```

---

## 21. kubectl Context — Connecting to Different Clusters

### The Problem
You run `kubectl get pods` and see AWS resources when you're working locally!

### Root Cause
kubectl uses **contexts** to know which cluster to connect to:

```bash
> kubectl config get-contexts

CURRENT   NAME                                      CLUSTER
*         arn:aws:eks:us-east-1:123:cluster/prod    aws-cluster
          rancher-desktop                           rancher-desktop
```

The `*` shows current context — you might be connected to AWS instead of local!

### The Fix
```bash
# See current context
kubectl config current-context

# List all contexts
kubectl config get-contexts

# Switch to local
kubectl config use-context rancher-desktop
```

---

## 22. Kubernetes YAML Anatomy — The 4 Essential Parts

### Part 1: ConfigMap (Environment Variables)
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: blinkit-app-config
  namespace: blinkit
data:
  SPRING_PROFILES_ACTIVE: "k8s"
  APP_ELASTIC_BASE_URL: "http://elasticsearch:9200"
```

### Part 2: Deployment (Pod Template + Replicas)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: blinkit-app
spec:
  replicas: 2                     # How many identical pods
  selector:
    matchLabels:
      app: blinkit-app            # Finds pods with this label
  template:
    spec:
      containers:
      - name: blinkit-app
        image: blinkit-app:v1
        imagePullPolicy: Never    # Local images only
        resources:
          requests:               # Minimum guaranteed
            memory: "256Mi"
            cpu: "100m"
          limits:                 # Maximum allowed
            memory: "512Mi"
            cpu: "500m"
```

### Part 3: Service (Stable Network Endpoint)
```yaml
apiVersion: v1
kind: Service
metadata:
  name: blinkit-app               # DNS name for other services
spec:
  selector:
    app: blinkit-app              # Route to pods with this label
  ports:
  - port: 8080
  type: ClusterIP                 # Inside cluster only
```

### Part 4: HPA (Auto-scaling)
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
spec:
  scaleTargetRef:
    name: blinkit-app
  minReplicas: 1
  maxReplicas: 5
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        averageUtilization: 70    # Scale up if CPU > 70%
```

### Key Concepts

| Concept | What It Does | Analogy |
|---------|--------------|---------|
| `replicas: 2` | Run 2 identical pods | 2 cashiers at checkout |
| `selector.matchLabels` | How Deployment finds its pods | Employee badge |
| `envFrom.configMapRef` | Inject env vars from ConfigMap | Loading .env file |
| `resources.requests` | Minimum guaranteed resources | Reserved parking spot |
| `resources.limits` | Maximum allowed resources | Speed limit |
| Service name | DNS name for pod access | Phone extension |

### Service Types

| Type | Accessibility | Use Case |
|------|---------------|----------|
| `ClusterIP` | Inside cluster only | Internal services |
| `NodePort` | Outside via node IP:port | Dev/testing access |
| `LoadBalancer` | External load balancer | Production (cloud) |

---

## Quick Reference — Kubernetes Commands

```bash
# Cluster
kubectl cluster-info
kubectl config current-context
kubectl config use-context rancher-desktop

# Pods
kubectl get pods -n blinkit
kubectl logs <pod-name> -n blinkit
kubectl describe pod <pod-name> -n blinkit
kubectl exec -it <pod-name> -n blinkit -- /bin/sh

# Deployments
kubectl rollout restart deployment blinkit-app -n blinkit
kubectl rollout status deployment blinkit-app -n blinkit

# Apply/Delete
kubectl apply -f k8s/ -n blinkit
kubectl delete -f k8s/ -n blinkit

# Debug
kubectl get events -n blinkit --sort-by='.lastTimestamp'
```

---

## Troubleshooting Quick Reference

| Symptom | Cause | Fix |
|---------|-------|-----|
| `ErrImageNeverPull` | Image not in K3s | Build with correct Docker context |
| `CrashLoopBackOff` | App crashing | Check logs: `kubectl logs <pod>` |
| `UnknownHostException` | Eureka hostname issue | Add `EUREKA_INSTANCE_PREFER_IP_ADDRESS: "true"` |
| Wrong cluster resources | kubectl context wrong | `kubectl config use-context rancher-desktop` |
| Timeout errors | ES/DB not ready | Increase timeout config, check readinessProbe |
