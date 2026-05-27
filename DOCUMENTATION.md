# Blinkit Phase 1 — Quick Commerce Backend

## Table of Contents
- [Project Overview](#project-overview)
- [Tech Stack](#tech-stack)
- [Infrastructure (Docker Compose)](#infrastructure-docker-compose)
- [Database Schema](#database-schema)
- [Redis & Cart Design](#redis--cart-design)
- [Architecture & Data Flow](#architecture--data-flow)
- [Package Structure](#package-structure)
- [REST API Endpoints](#rest-api-endpoints)
- [Key Architectural Patterns](#key-architectural-patterns)
- [How to Run](#how-to-run)
- [Useful Commands](#useful-commands)
- [FAQs](#faqs)

---

## Project Overview

A Blinkit-clone quick commerce backend built with Spring Boot. Phase 1 focuses on **Product catalog management** with:
- **PostgreSQL** as the source of truth (writes)
- **Elasticsearch** for full-text product search (reads)
- **Kafka (Redpanda)** as the message broker connecting them via the **Transactional Outbox Pattern**
- **Redis** for low-latency shopping cart storage

---

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 17 | Language |
| Spring Boot | 3.3.6 | Framework |
| PostgreSQL | 16 | Primary database |
| Elasticsearch | 8.15.2 | Search engine |
| Redpanda | latest | Kafka-compatible message broker |
| Kibana | 8.15.2 | ES dashboard |
| Redis | 7 (alpine image) | Cart cache/storage with TTL |
| Flyway | 10.14.0 | DB migrations |
| Spring Kafka | (managed) | Kafka producer/consumer |
| Spring Data Redis | (managed) | Redis integration |
| Apache Commons CSV | 1.10.0 | CSV parsing for bulk uploads |
| Lombok | (managed) | Boilerplate reduction |
| Jackson | (managed) | JSON serialization |

---

## Infrastructure (Docker Compose)

`docker-compose.yml` includes these services:

| Service | Image | Port | Purpose |
|---|---|---|---|
| **postgres** | `postgres:16` | 5432 | Primary data store (DB: `blinkit`, User: `blinkit`, Pass: `blinkit`) |
| **elasticsearch** | `elasticsearch:8.15.2` | 9200 | Search engine (single-node, security disabled) |
| **kibana** | `kibana:8.15.2` | 5601 | ES dashboard UI |
| **redpanda** | `redpandadata/redpanda:latest` | 9092 | Kafka-compatible message broker |
| **redis** | `redis:7-alpine` | 6379 | Cart storage (AOF + periodic RDB persistence) |
| **blinkit-app** | built from project Dockerfile | 8080 | Optional containerized app runtime |

> **Note:** You can run the app either on host (`mvn spring-boot:run`) or via `blinkit-app` service in Docker.

---

## Database Schema

Managed by **Flyway** migrations in `src/main/resources/db/migration/`:

### V1 — `products` table
```sql
CREATE TABLE products (
  id           UUID PRIMARY KEY,
  name         VARCHAR(255) NOT NULL,
  brand        VARCHAR(120),
  category     VARCHAR(120),
  price        NUMERIC(12,2) NOT NULL,
  active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMP NOT NULL,
  updated_at   TIMESTAMP NOT NULL
);
-- Indexes on category and brand
```

### V2 — `product_outbox` table (Transactional Outbox)
```sql
CREATE TABLE product_outbox (
  id            UUID PRIMARY KEY,
  aggregate_id  UUID NOT NULL,
  event_type    VARCHAR(60) NOT NULL,    -- e.g. PRODUCT_UPSERTED
  payload       JSONB NOT NULL,
  status        VARCHAR(20) NOT NULL,    -- NEW → PUBLISHED → FAILED
  attempts      INT NOT NULL DEFAULT 0,
  created_at    TIMESTAMP NOT NULL,
  updated_at    TIMESTAMP NOT NULL
);
-- Indexes on (status, created_at) and aggregate_id
```

### V3 — Alter outbox payload type
```sql
ALTER TABLE product_outbox ALTER COLUMN payload TYPE TEXT;
```

### V4 — Add product image URL
```sql
ALTER TABLE products ADD COLUMN IF NOT EXISTS image_url VARCHAR(1024);
```

---

## Redis & Cart Design

### Why Redis for cart?
- Cart operations are high-frequency and latency-sensitive (add/remove/update qty).
- Redis provides fast reads/writes and native TTL.
- Postgres remains source of truth for durable order data at checkout.

### Cart identity and key model
- API contract uses header: `X-Cart-Id` (UUID).
- Redis key format: `cart:{cartId}`
- Value shape (`CartState`):
  - `items`: map of `{ productId -> quantity }`
  - `updatedAt`: last modification timestamp

### TTL behavior
- Config: `blinkit.cart.ttl-hours` (default `168` = 7 days)
- Each write refreshes TTL.

### Redis persistence (AOF + periodic RDB)
- Redis service runs with:
  - `--appendonly yes`
  - `--save 300 10`
- Meaning:
  - **AOF** logs writes for recovery with minimal loss window.
  - **RDB** snapshot every 300s if at least 10 keys changed.

### Where Redis data is stored in Docker
- In container: `/data`
- In compose: mounted to named volume `redis-data`
- Practical effect: cart data survives container restart/recreate unless volumes are removed (`docker compose down -v`).

### Read/write flow with Redis + Postgres
1. **Shopping stage (open cart):** Read/write cart in Redis.
2. **Checkout stage (future phase):** Read cart from Redis, validate, persist order in Postgres, clear Redis cart.
3. **Order history stage:** Read from Postgres.

### App startup understanding
- App does **not** preload all Redis data into app memory.
- Redis process itself restores in-memory keys from persistence files when Redis starts.
- App fetches only requested keys on demand.

### Is Redis using RAM or volume?
- Redis is **in-memory first** during runtime (fast reads/writes happen in RAM).
- Persistence files (AOF/RDB) are written to `/data` and stored in `redis-data` volume.
- So both are used:
  - **RAM** = working dataset / cache speed
  - **Volume** = durability across restart/redeploy

### Whose RAM is used in Docker Desktop on Windows?
- Redis container uses memory allocated to Docker Desktop VM.
- Docker VM memory comes from your host machine RAM.
- Practical chain: **Host RAM → Docker VM RAM → Redis process memory**.

---

## Architecture & Data Flow

```
  POST /api/products
         │
         ▼
  ┌──────────────┐
  │ ProductService │  ← @Transactional
  └──────┬───────┘
         │
    ┌────┴────┐
    ▼         ▼
 products   product_outbox (status=NEW)
  table       table
                │
                │  @Scheduled (every 1 second)
                ▼
     ProductOutboxPublisher
                │
                ▼  Kafka topic: "product-events"
     ProductIndexConsumer
                │
                ▼
     ElasticDocumentIndexer
                │
                ▼
     Elasticsearch (index: products_v1)
                │
   GET /api/products/search?q=...
```

### Step-by-step (when you POST a product):

1. **HTTP Request** → `ProductController.create()` receives and validates the JSON body
2. **@Transactional** → `ProductService.create()` atomically:
   - INSERTs into `products` table
   - INSERTs into `product_outbox` table (status=`NEW`, eventType=`PRODUCT_UPSERTED`)
3. **Response returned** to the client with the created product
4. **~1 second later** → `ProductOutboxPublisher` (scheduled poller) picks up `NEW` outbox rows → sends payload to Kafka topic `product-events` → marks status as `PUBLISHED`
5. **Kafka Consumer** → `ProductIndexConsumer` receives the message → calls `ElasticDocumentIndexer` → PUTs the document into Elasticsearch at `products_v1/_doc/{id}`

---

## Package Structure

```
com.blinkit.phase1
├── BlinkitPhase1Application.java     # Entry point (@EnableScheduling)
├── api/
│   ├── ApiError.java                 # Error response DTO (record)
│   └── GlobalExceptionHandler.java   # @RestControllerAdvice (404, 400, 500)
├── cart/
│   ├── CartController.java           # Redis-backed cart endpoints (/api/cart)
│   ├── CartService.java              # Cart business logic + TTL + product validation
│   ├── CartState.java                # Redis value model (items + updatedAt)
│   ├── CartItemNotFoundException.java
│   └── dto/
│       ├── AddCartItemRequest.java
│       ├── UpdateCartItemQuantityRequest.java
│       ├── CartItemResponse.java
│       └── CartResponse.java
├── config/
│   ├── WebConfig.java                # CORS config
│   └── RedisConfig.java              # RedisTemplate<String, CartState> serializer config
├── elastic/
│   ├── ElasticProperties.java        # @ConfigurationProperties (baseUrl, index)
│   ├── ElasticConfig.java            # RestClient bean for ES
│   ├── ElasticIndexInitializer.java  # Creates ES index on app startup
│   ├── ElasticIndexService.java      # ensureIndexExists() + indexProduct() via WebClient
│   ├── ElasticDocumentIndexer.java   # PUT JSON doc into ES via WebClient
│   └── ProductIndexConsumer.java     # @KafkaListener on "product-events" → indexes to ES
└── product/
    ├── ProductEntity.java            # JPA entity → products table
    ├── ProductRepository.java        # JpaRepository<ProductEntity, UUID>
    ├── ProductController.java        # REST controller (/api/products)
    ├── ProductService.java           # Create product + outbox event (transactional)
    ├── ProductSearchService.java     # Full-text search via ES (multi_match + fuzziness)
    ├── ProductMapper.java            # Entity → Response DTO mapper
    ├── ProductNotFoundException.java # Custom 404 exception
    ├── dto/
    │   ├── CreateProductRequest.java # Validated request record
    │   └── ProductResponse.java      # Response record
    ├── outbox/
    │   ├── ProductOutboxEvent.java   # JPA entity → product_outbox table
    │   ├── ProductOutboxRepository.java # findNextNew(limit) native query
    │   └── ProductOutboxPublisher.java  # @Scheduled poller → Kafka producer
    └── bulk_upload/
        ├── ProductCsvRow.java        # Validated CSV row record
        ├── ProductBulkUploadService.java # CSV parsing + batch insert (200/batch)
        └── BulkUploadResponse.java   # Response with totalRows, inserted, errors
```

---

## REST API Endpoints

### Create Product
```
POST /api/products
Content-Type: application/json

{
    "name": "MINERAL WATER 3L",       // required, max 255
    "brand": "KINLEY",                 // optional, max 120
    "category": "Dairy",               // optional, max 120
    "price": 74.00,                    // required, >= 0.01
    "active": true                     // required
}

Response: 201 Created
{
    "id": "c6ed6d10-cc48-4981-95c2-64c2b45212cc",
    "name": "MINERAL WATER 3L",
    "brand": "KINLEY",
    "category": "Dairy",
    "price": 74.00,
    "active": true,
    "createdAt": "2026-03-25T13:37:34.802866Z",
    "updatedAt": "2026-03-25T13:37:34.802866Z"
}
```

### Get Product by ID
```
GET /api/products/{id}

Response: 200 OK (same shape as above)
Response: 404 if not found
```

### Search Products (via Elasticsearch)
```
GET /api/products/search?q=water&page=0&size=20

Response: 200 OK — array of ProductResponse
- Uses multi_match on name^3, brand^2, category
- Fuzziness enabled (handles typos)
- Only returns active=true products
```

### Bulk Upload via CSV
```
POST /api/products/bulk/upload-csv
Content-Type: multipart/form-data
Body: file (CSV with headers: name, brand, category, price, active)

Response: 200 OK
{
    "totalRows": 1000,
    "inserted": 995,
    "rejected": 5,
    "errors": [
        { "rowNumber": 42, "message": "price must be >= 0.01" }
    ]
}
```

### Cart APIs (Redis-backed)

All cart APIs require header:
```http
X-Cart-Id: <uuid>
```

#### Get Cart
```http
GET /api/cart
```

#### Add Item
```http
POST /api/cart/items
Content-Type: application/json

{
  "productId": "28da2fbd-a288-4ace-baca-a83af93cd2d0",
  "quantity": 2
}
```

#### Update Item Quantity
```http
PUT /api/cart/items/{productId}
Content-Type: application/json

{
  "quantity": 5
}
```

#### Remove Item
```http
DELETE /api/cart/items/{productId}
```

#### Clear Cart
```http
DELETE /api/cart
```

Sample response:
```json
{
  "cartId": "77ee5786-90ee-4ab5-a90c-af46d7c74eae",
  "items": [
    {
      "productId": "28da2fbd-a288-4ace-baca-a83af93cd2d0",
      "name": "Butter 100g",
      "brand": "Amul",
      "category": "Milk & Dairy",
      "imageUrl": null,
      "unitPrice": 61.18,
      "quantity": 2,
      "lineTotal": 122.36,
      "active": true
    }
  ],
  "totalQuantity": 2,
  "subtotal": 122.36,
  "updatedAt": "2026-03-25T16:46:35.223895600Z"
}
```

---

## Key Architectural Patterns

### 1. Transactional Outbox Pattern
Product + outbox event are saved in the **same DB transaction**. A scheduled poller relays events to Kafka. This guarantees **eventual consistency** between Postgres and Elasticsearch without two-phase commit.

### 2. CQRS-lite
- **Writes** → PostgreSQL (source of truth)
- **Reads/Search** → Elasticsearch

### 3. Batch Processing
Bulk CSV upload uses JPA batching (batch_size=200) with `EntityManager.flush()/clear()` for memory efficiency.

### 4. Retry with Dead-lettering
Outbox publisher retries up to 10 times. After 10 failures → status becomes `FAILED`.

### 5. Graceful ES Degradation
If Elasticsearch is down, the app still runs. Indexing failures are logged but don't roll back DB writes.

### 6. Redis Cart Pattern
- Redis is the cart store during shopping stage (`/api/cart` APIs).
- Product validation still uses Postgres records (`exists`, `active`).
- Cart keys expire by TTL (`blinkit.cart.ttl-hours`).

### 7. Redis Durability in Docker
- AOF + periodic RDB enabled in Redis container.
- `redis-data` named volume stores Redis files.
- Normal restarts preserve cart data; `docker compose down -v` removes it.

---

## How to Run

### Step 1: Start Infrastructure
```bash
docker compose up -d
```
This starts Postgres, Elasticsearch, Kibana, Redpanda, and Redis.

### Step 2: Start the Spring Boot App
```bash
mvn spring-boot:run
```
The app runs on `http://localhost:8080`.

### Step 3: Verify
- App health: `http://localhost:8080/actuator/health`
- Kibana UI: `http://localhost:5601`
- ES direct: `curl.exe -s "http://localhost:9200/products_v1/_count"`

---

## Useful Commands

### Elasticsearch
| What | Command |
|---|---|
| Count docs | `curl.exe -s "http://localhost:9200/products_v1/_count"` |
| Get doc by ID | `curl.exe -s "http://localhost:9200/products_v1/_doc/{id}"` |
| Delete all docs (keep index) | `curl.exe -s -X POST "http://localhost:9200/products_v1/_delete_by_query" -H "Content-Type: application/json" -d "{\"query\":{\"match_all\":{}}}"` |
| Delete entire index | `curl.exe -s -X DELETE "http://localhost:9200/products_v1"` |
| List all indices | `curl.exe -s "http://localhost:9200/_cat/indices?v"` |

### Docker
| What | Command |
|---|---|
| Start services | `docker compose up -d` |
| Check status | `docker compose ps` |
| View logs | `docker compose logs -f <service>` |
| Stop services | `docker compose down` |
| **Full clean reset** | `docker compose down -v` (deletes all data!) |

### Redis
| What | Command |
|---|---|
| Check Redis service | `docker compose ps redis` |
| List cart keys | `docker exec -it blinkit-phase1-starter-redis-1 redis-cli KEYS "cart:*"` |
| Read one cart | `docker exec -it blinkit-phase1-starter-redis-1 redis-cli GET "cart:{cart-id}"` |
| View Redis memory usage | `docker exec -it blinkit-phase1-starter-redis-1 redis-cli INFO memory` |
| Real-time Redis stats | `docker exec -it blinkit-phase1-starter-redis-1 redis-cli --stat` |
| Container RAM usage | `docker stats blinkit-phase1-starter-redis-1` |
| Check AOF setting | `docker exec -it blinkit-phase1-starter-redis-1 redis-cli CONFIG GET appendonly` |
| Check snapshot rule | `docker exec -it blinkit-phase1-starter-redis-1 redis-cli CONFIG GET save` |

> **Note:** In PowerShell, always use `curl.exe` (with `.exe`). Plain `curl` is aliased to `Invoke-WebRequest` which has different syntax.

---

## FAQs

### Q1: Where does `Asia/Calcutta` come from? I never set it.
**A:** You didn't set it anywhere in the project. It comes from your **Windows OS timezone** (Indian Standard Time). The JVM picks it up automatically and maps it to the legacy name `Asia/Calcutta`. When the PostgreSQL JDBC driver opens a connection, it sends this timezone during the initial handshake — but PostgreSQL 16 only recognizes the modern name `Asia/Kolkata`, causing the error.

**Fix:** Set the JVM timezone to UTC before anything starts:
```java
// In BlinkitPhase1Application.java
public static void main(String[] args) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    SpringApplication.run(BlinkitPhase1Application.class, args);
}
```

This aligns with the rest of the project config (`docker-compose.yml` sets `TZ=UTC`, `application.yml` sets `hibernate.jdbc.time_zone: UTC`).

---

### Q2: Where do all those Kafka ConsumerConfig values come from? I only set 3-4 in application.yml.
**A:** They come from **3 layered sources**:

1. **Your `application.yml`** (3-4 values you explicitly set):
   - `bootstrap.servers = localhost:9092`
   - `group.id = blinkit-phase1`
   - `auto.offset.reset = earliest`

2. **Spring Boot auto-configuration** (5-6 values):
   - `enable.auto.commit = false` (Spring manages offsets)
   - `key/value.deserializer = StringDeserializer` (defaults)
   - `client.id = consumer-blinkit-phase1-1` (auto-generated)

3. **Apache Kafka client library defaults** (90+ values):
   - All `ssl.*`, `sasl.*`, `fetch.*`, `session.*`, etc.
   - These are hard-coded in `org.apache.kafka.clients.consumer.ConsumerConfig`
   - Kafka always logs ALL config keys at startup — most are just defaults you never need to touch.

---

### Q3: When I POST a product, does Elasticsearch also get updated?
**A:** Yes! The full flow is:
1. Product + outbox event saved to Postgres (same transaction)
2. ~1 second later, outbox poller sends event to Kafka topic `product-events`
3. Kafka consumer receives it and indexes the document into Elasticsearch

You can verify with:
```bash
curl.exe -s "http://localhost:9200/products_v1/_doc/{product-id}"
```

---

### Q4: How do I delete data from Elasticsearch?
**A:**
- **Delete all docs (keep index):**
  ```bash
  curl.exe -s -X POST "http://localhost:9200/products_v1/_delete_by_query" -H "Content-Type: application/json" -d "{\"query\":{\"match_all\":{}}}"
  ```
  `match_all` = select every document. The index structure is kept.

- **Delete entire index:**
  ```bash
  curl.exe -s -X DELETE "http://localhost:9200/products_v1"
  ```
  The app will recreate it on next startup via `ElasticIndexInitializer`.

- **Delete one doc by ID:**
  ```bash
  curl.exe -s -X DELETE "http://localhost:9200/products_v1/_doc/{id}"
  ```

---

### Q5: I started the app after a long time. Why did Elasticsearch still have old data?
**A:** Because **Docker volumes persist data** even when containers are stopped/removed.

| Command | Containers | Volumes (data) |
|---|---|---|
| `docker compose stop` | Stopped | ✅ Kept |
| `docker compose down` | Removed | ✅ Kept |
| `docker compose down -v` | Removed | ❌ Deleted |

Same applies to PostgreSQL — your tables also persist. For a **completely fresh start**:
```bash
docker compose down -v
docker compose up -d
```
The `-v` flag deletes all volumes. Flyway will recreate DB tables, and `ElasticIndexInitializer` will recreate the ES index.

---

### Q6: Why does PowerShell's `curl` not work?
**A:** In PowerShell, `curl` is an alias for `Invoke-WebRequest` (a PowerShell cmdlet), NOT the real curl. That's why it prompts `Supply values for the following parameters:`. Always use `curl.exe` (with the `.exe` suffix) to invoke the actual curl binary.

---

### Q7: Will cart data flush if app restarts?
**A:** App restart alone does not flush Redis. Redis is a separate service. Data loss usually happens on explicit flush/eviction, persistence misconfiguration, or volume deletion.

---

### Q8: What does "AOF + periodic RDB" mean?
**A:**
- **AOF (Append Only File):** append write operations for replay.
- **RDB snapshots:** periodic full-memory snapshots.

Using both improves recovery and durability.

---

### Q9: In Docker, where exactly does Redis save data?
**A:** Redis writes to `/data` in container, mapped to named volume `redis-data` in this project.

---

### Q10: Do large systems store customer cart data this way?
**A:** Conceptually yes (in-memory + persistence), but production setups are distributed and hardened (cluster, replicas, encryption, ACLs, backups, retention).

---

### Q11: Can I see where in-memory cart data is stored? Is it my machine RAM?
**A:** Yes. In Docker Desktop setups, Redis memory is container memory, backed by Docker VM memory, which is carved out of your machine RAM.

#### How to view it

1) **Check Redis memory usage**
```bash
docker exec -it blinkit-phase1-starter-redis-1 redis-cli INFO memory
```
Look at:
- `used_memory_human`
- `used_memory_peak_human`
- `maxmemory_human`

2) **See keys in memory (cart keys)**
```bash
docker exec -it blinkit-phase1-starter-redis-1 redis-cli KEYS "cart:*"
```

3) **View one in-memory key value**
```bash
docker exec -it blinkit-phase1-starter-redis-1 redis-cli GET "cart:{cart-id}"
```

4) **Real-time memory monitor**
```bash
docker exec -it blinkit-phase1-starter-redis-1 redis-cli --stat
```

5) **Container RAM usage from Docker side**
```bash
docker stats blinkit-phase1-starter-redis-1
```

So yes, you can inspect it live; it’s in Redis process memory, backed by your system RAM allocation.

