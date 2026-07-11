# NearrBuy Hyperlocal  — Quick Commerce Backend

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
- [Microservices Architecture (Phase 2)](#microservices-architecture-phase-2)
- [Deep Dive: Transactional Outbox Pattern](#deep-dive-transactional-outbox-pattern)
- [Sync vs Async: Order → Inventory Communication](#sync-vs-async-order--inventory-communication)
- [Deep Dive: Eventual Consistency](#deep-dive-eventual-consistency)
- [Inventory Service Design](#inventory-service-design)
- [Design Decisions & Rationale](#design-decisions--rationale)
- [Kubernetes Deployment (Phase 3)](#kubernetes-deployment-phase-3)
  - [Kubernetes Architecture Overview](#kubernetes-architecture-overview)
  - [Declarative State Management](#understanding-kubernetes-declarative-state-management)
  - [Docker Context Issues](#docker-context-issues--a-common-gotcha)
  - [imagePullPolicy Explained](#understanding-imagepullpolicy-never)
  - [Eureka in Kubernetes](#eureka-service-discovery-in-kubernetes)
  - [Spring Boot Relaxed Binding](#spring-boot-relaxed-binding--how-environment-variables-map-to-properties)
  - [Elasticsearch Timeout Fix](#elasticsearch-timeout-fix--making-configuration-dynamic)
  - [K8s YAML Anatomy](#kubernetes-yaml-file-anatomy--line-by-line-explanation)
  - [kubectl Context](#kubectl-context--local-vs-cloud-clusters)
  - [AWS Production Strategy](#aws-production-deployment-strategy-conceptual)
  - [Troubleshooting Cheat Sheet](#troubleshooting-cheat-sheet)

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

## Useful Commands & URLs

### 🌐 UI Dashboards

| Service | URL | Credentials | Purpose |
|---------|-----|-------------|---------|
| **Kibana** | http://localhost:5601 | (none) | Elasticsearch dashboard, Dev Tools for queries |
| **Redpanda Console** | http://localhost:8081 | (none) | Kafka topics, messages, consumer groups |
| **pgAdmin** | http://localhost:5050 | `admin@blinkit.com` / `admin` | PostgreSQL database browser |
| **Redis Commander** | http://localhost:8083 | (none) | Redis keys browser, data viewer |

#### pgAdmin Server Setup (first-time)

When you first open pgAdmin, add these server connections:

**blinkit database:**
| Field | Value |
|-------|-------|
| Name | blinkit |
| Host | `host.docker.internal` |
| Port | `5432` |
| Database | `blinkit` |
| Username | `blinkit` |
| Password | `blinkit` |

**inventory_db database:**
| Field | Value |
|-------|-------|
| Name | inventory |
| Host | `host.docker.internal` |
| Port | `5433` |
| Database | `inventory_db` |
| Username | `inventory` |
| Password | `inventory` |

---

### 🔗 Service URLs

| Service | URL | Purpose |
|---------|-----|---------|
| **blinkit-app** | http://localhost:8080 | Main application REST API |
| **inventory-service** | http://localhost:8082 | Inventory microservice API |
| **Elasticsearch** | http://localhost:9200 | Search engine REST API |
| **Redpanda (Kafka)** | localhost:9092 | Message broker (no HTTP) |
| **Redis** | localhost:6379 | Cache/cart storage (no HTTP) |
| **PostgreSQL (blinkit)** | localhost:5432 | Primary database |
| **PostgreSQL (inventory)** | localhost:5433 | Inventory database |

---

### 📊 Health Check URLs

```bash
# Application health
curl.exe -s "http://localhost:8080/actuator/health"
curl.exe -s "http://localhost:8082/actuator/health"

# Elasticsearch cluster health
curl.exe -s "http://localhost:9200/_cluster/health"

# Check if services are responding
curl.exe -s "http://localhost:9200" | Select-String "cluster_name"
```

---

### 🔍 Elasticsearch Commands

| What | Command |
|---|---|
| Cluster health | `curl.exe -s "http://localhost:9200/_cluster/health"` |
| Count docs | `curl.exe -s "http://localhost:9200/products_v1/_count"` |
| Get doc by ID | `curl.exe -s "http://localhost:9200/products_v1/_doc/{id}"` |
| Search products | `curl.exe -s "http://localhost:9200/products_v1/_search?q=milk"` |
| List all indices | `curl.exe -s "http://localhost:9200/_cat/indices?v"` |
| Delete all docs (keep index) | `curl.exe -s -X POST "http://localhost:9200/products_v1/_delete_by_query" -H "Content-Type: application/json" -d "{\"query\":{\"match_all\":{}}}"` |
| Delete entire index | `curl.exe -s -X DELETE "http://localhost:9200/products_v1"` |

---

### 🐳 Docker Commands

| What | Command |
|---|---|
| Start all services | `docker compose up -d` |
| Start specific service | `docker compose up -d postgres elasticsearch redis` |
| Start UI dashboards | `docker compose up -d pgadmin redis-commander redpanda-console` |
| Check status | `docker compose ps` |
| View logs | `docker compose logs -f <service>` |
| View all logs | `docker compose logs -f` |
| Stop services | `docker compose down` |
| **Full clean reset** | `docker compose down -v` (⚠️ deletes all data!) |
| Rebuild images | `docker compose build --no-cache` |

---

### 📦 Kafka (Redpanda) Commands

| What | Command |
|---|---|
| List topics | `docker exec -it blinkit-phase1-starter-redpanda-1 rpk topic list` |
| Create topic | `docker exec -it blinkit-phase1-starter-redpanda-1 rpk topic create <topic-name>` |
| Consume messages | `docker exec -it blinkit-phase1-starter-redpanda-1 rpk topic consume product-events` |
| Describe topic | `docker exec -it blinkit-phase1-starter-redpanda-1 rpk topic describe product-events` |
| List consumer groups | `docker exec -it blinkit-phase1-starter-redpanda-1 rpk group list` |
| Describe consumer group | `docker exec -it blinkit-phase1-starter-redpanda-1 rpk group describe blinkit-phase1` |

---

### 🗄️ Redis Commands

| What | Command |
|---|---|
| Check Redis service | `docker compose ps redis` |
| List cart keys | `docker exec -it blinkit-phase1-starter-redis-1 redis-cli KEYS "cart:*"` |
| Read one cart | `docker exec -it blinkit-phase1-starter-redis-1 redis-cli GET "cart:{cart-id}"` |
| Delete a cart | `docker exec -it blinkit-phase1-starter-redis-1 redis-cli DEL "cart:{cart-id}"` |
| View Redis memory usage | `docker exec -it blinkit-phase1-starter-redis-1 redis-cli INFO memory` |
| Real-time Redis stats | `docker exec -it blinkit-phase1-starter-redis-1 redis-cli --stat` |
| Container RAM usage | `docker stats blinkit-phase1-starter-redis-1` |
| Check AOF setting | `docker exec -it blinkit-phase1-starter-redis-1 redis-cli CONFIG GET appendonly` |
| Check snapshot rule | `docker exec -it blinkit-phase1-starter-redis-1 redis-cli CONFIG GET save` |
| Flush all data | `docker exec -it blinkit-phase1-starter-redis-1 redis-cli FLUSHALL` |

---

### 🐘 PostgreSQL Commands

| What | Command |
|---|---|
| Connect to blinkit DB | `docker exec -it blinkit-phase1-starter-postgres-1 psql -U blinkit -d blinkit` |
| Connect to inventory DB | `docker exec -it blinkit-phase1-starter-inventory-postgres-1 psql -U inventory -d inventory_db` |
| List tables | `\dt` (inside psql) |
| Describe table | `\d products` (inside psql) |
| Exit psql | `\q` |

**Useful SQL queries:**
```sql
-- Count products
SELECT COUNT(*) FROM products;

-- Check outbox events
SELECT * FROM product_outbox ORDER BY created_at DESC LIMIT 10;

-- Check pending outbox events
SELECT * FROM product_outbox WHERE status = 'NEW';

-- Check orders
SELECT * FROM orders ORDER BY created_at DESC LIMIT 10;

-- Check inventory stock
SELECT * FROM stock ORDER BY updated_at DESC LIMIT 10;
```

---

### 🚀 Application Startup

```bash
# Start infrastructure first
docker compose up -d

# Wait for services to be ready (especially Elasticsearch)
Start-Sleep -Seconds 20

# Start blinkit-app (from project root)
mvn spring-boot:run -pl blinkit-app

# Start inventory-service (in separate terminal)
mvn spring-boot:run -pl inventory-service

# OR use VS Code's Run/Debug for individual services
```

---

### 🧪 API Testing (Sample cURL)

```bash
# Get all products
curl.exe -s "http://localhost:8080/api/products" | ConvertFrom-Json

# Search products
curl.exe -s "http://localhost:8080/api/products/search?query=milk"

# Get cart
curl.exe -s "http://localhost:8080/api/cart/{cartId}"

# Add to cart
curl.exe -X POST "http://localhost:8080/api/cart/{cartId}/items" -H "Content-Type: application/json" -d "{\"productId\":\"uuid-here\",\"quantity\":2}"

# Check inventory
curl.exe -s "http://localhost:8082/api/inventory/{productId}"
```

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
---

## Microservices Architecture (Phase 2)

The project has been refactored from a monolith to a **multi-module microservices architecture**.

### Module Structure

```
blinkit-phase1-starter/
├── pom.xml                    # Parent POM (blinkit-parent)
├── blinkit-app/               # Main application (Port 8080)
│   ├── pom.xml
│   └── src/
└── inventory-service/         # Inventory microservice (Port 8082)
    ├── pom.xml
    └── src/
```

### Service Boundaries

| Service | Port | Database | Responsibilities |
|---------|------|----------|------------------|
| **blinkit-app** | 8080 | `blinkit` (5432) | Products, Cart, Orders, Search |
| **inventory-service** | 8082 | `inventory_db` (5433) | Stock management, Reservations |

### Inter-Service Communication

Services communicate via **Kafka events** (not direct HTTP calls) for loose coupling:

```
blinkit-app                         inventory-service
     │                                     │
     │  ORDER_PLACED                       │
     │────────────────────────────────────►│
     │  (Kafka: order-events)              │
     │                                     │
     │  ORDER_CONFIRMED / ORDER_REJECTED   │
     │◄────────────────────────────────────│
     │  (Kafka: inventory-events)          │
```

---

## Deep Dive: Transactional Outbox Pattern

### The Problem: Dual-Write Inconsistency

Why can't we directly write to both PostgreSQL and Elasticsearch/Kafka in the same transaction?

```java
// ❌ DANGEROUS: Dual-write anti-pattern
@Transactional
public ProductEntity create(CreateProductRequest req) {
    ProductEntity saved = repo.save(entity);      // DB write
    elasticService.index(saved);                   // ES write - OUTSIDE transaction!
    return saved;
}
```

**Failure scenarios:**

| Scenario | PostgreSQL | Elasticsearch | Result |
|----------|-----------|---------------|--------|
| Both succeed | ✅ Saved | ✅ Indexed | Happy path |
| ES fails after DB commit | ✅ Saved | ❌ Not indexed | **Product exists but not searchable** |
| DB rolls back after ES indexed | ❌ Rolled back | ✅ Indexed | **Ghost product in search** |

**Root cause:** `@Transactional` only covers PostgreSQL operations. Elasticsearch/Kafka are external systems with no shared transaction boundary.

### The Solution: Outbox Pattern

```
┌─────────────────────────────────────────────────────────────┐
│              SINGLE POSTGRESQL TRANSACTION                  │
│                                                             │
│   INSERT INTO products (...)                                │
│   INSERT INTO product_outbox (status='NEW', ...)            │
│                                                             │
│        ✅ BOTH SUCCEED OR BOTH FAIL (ACID GUARANTEED)       │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ @Scheduled (1 second polling)
                              ▼
                 ┌─────────────────────────┐
                 │ ProductOutboxPublisher  │
                 │  (Polls for NEW rows)   │
                 └───────────┬─────────────┘
                             │
                             ▼
                 ┌─────────────────────────┐
                 │   Kafka: product-events │
                 └───────────┬─────────────┘
                             │
                             ▼
                 ┌─────────────────────────┐
                 │  ProductIndexConsumer   │
                 │  → Elasticsearch        │
                 └─────────────────────────┘
```

**Guarantees:**
- **Atomicity:** Product and outbox event are saved together or not at all
- **Durability:** If app crashes, event stays `NEW` and is picked up on restart
- **At-least-once delivery:** Event is retried until successfully published
- **Resilience:** Kafka/ES outages don't break product creation

### Why Order Also Uses Outbox Pattern

Orders use the outbox pattern for **non-critical downstream notifications**, but stock reservation is handled **synchronously** (see [Sync vs Async Decision](#sync-vs-async-order--inventory-communication)).

```
ORDER_CONFIRMED event consumers (ASYNC - non-critical):
├── notification-service → Send SMS/Email (future)
├── analytics-service    → Update dashboards (future)
└── delivery-service     → Assign rider (future)
```

**Note:** Stock reservation is NOT done via events. It's a synchronous HTTP call because user needs immediate feedback.

---

## Sync vs Async: Order → Inventory Communication

### The Problem with Fully Async Stock Check

```
❌ WRONG: Fully Async Design
───────────────────────────

User clicks "Place Order"
         │
         ▼
┌─────────────────┐
│ OrderService    │
│ • Save order    │
│ • Clear cart    │
│ • Return 201 ✅ │  ──────►  "Order Placed Successfully!" 
└────────┬────────┘            (User is HAPPY 😊)
         │
         │  ~1-2 seconds later (async event)
         ▼
┌─────────────────┐
│ InventoryService│
│ • Check stock   │
│ • INSUFFICIENT! │  ──────►  "Sorry, order cancelled" 😠
└─────────────────┘            (User is ANGRY - false promise!)
```

**This is terrible UX!** User was told order was placed, cart was cleared, then later told it's cancelled.

### The Correct Hybrid Design

```
✅ CORRECT: Sync for Critical Path + Async for Notifications
─────────────────────────────────────────────────────────────

User clicks "Place Order"
         │
         ▼
┌─────────────────┐      SYNC HTTP (WebClient)      ┌─────────────────┐
│ OrderService    │ ────────────────────────────────► │ InventoryService│
│                 │  POST /api/stock/reserve         │                 │
│                 │  {orderId, items:[...]}          │ • Check stock   │
│                 │                                  │ • Reserve if OK │
│                 │ ◄──────────────────────────────── │                 │
│                 │  {success: true/false,           │                 │
└────────┬────────┘   reservedItems: [...]}          └─────────────────┘
         │
         ├── IF reserved successfully:
         │   ├── Save order (status=CONFIRMED)
         │   ├── Save outbox (ORDER_CONFIRMED) → async notifications
         │   ├── Clear cart
         │   └── Return 201 "Order confirmed!" ✅
         │
         └── IF reservation failed:
             ├── DON'T save order
             ├── DON'T clear cart
             └── Return 400 "Insufficient stock" ❌ (immediate feedback)
```

### Why Sync for Stock Reservation?

| Aspect | Fully Async (Wrong) | Sync Reservation (Correct) |
|--------|---------------------|----------------------------|
| User feedback | "Placed!" then later "Cancelled" | Immediate success/failure |
| Trust | Broken (false promise) | Maintained (honest) |
| Cart | Already cleared on failure | Still intact, user can retry |
| Inventory down | Accept order blindly (risky!) | Fail fast, don't accept |

### When to Use Sync vs Async

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    SYNC vs ASYNC - CORRECT USE CASES                            │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  SYNC (Critical Path - User needs immediate feedback):                          │
│  ─────────────────────────────────────────────────────                          │
│  • Stock reservation    ← Must know if items available                          │
│  • Payment processing   ← Can't confirm without payment                         │
│  • User authentication  ← Can't proceed without login                           │
│                                                                                 │
│  ASYNC (Non-Critical - Can happen in background):                               │
│  ────────────────────────────────────────────────                               │
│  • Send SMS/Email       ← 5 second delay is acceptable                          │
│  • Update analytics     ← Dashboard can be eventual                             │
│  • Notify delivery      ← Rider assignment can wait                             │
│  • Sync to ES           ← Search lag is acceptable                              │
│  • Generate invoice PDF ← Can be emailed later                                  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Trade-offs Acknowledged

| Trade-off | Our Choice | Reason |
|-----------|------------|--------|
| Latency | +50-100ms for HTTP call | Acceptable for accurate feedback |
| Availability | If inventory down → orders fail | Better than false promises |
| Coupling | OrderService knows InventoryService | Acceptable for critical path |
| Complexity | Need timeout, retry, circuit breaker | Worth it for correctness |

---

## Deep Dive: Eventual Consistency

### Definition

> *"If no new updates are made, eventually all reads will return the same value."*

In this system, there's a **1-2 second window** where PostgreSQL and Elasticsearch/Inventory may have different data.

### Consistency Models Spectrum

```
STRONG CONSISTENCY                              EVENTUAL CONSISTENCY
       │                                                │
       ▼                                                ▼
┌─────────────────┐                           ┌─────────────────┐
│ • Single DB     │                           │ • Outbox pattern│
│ • 2-Phase Commit│                           │ • Event-driven  │
│ • All nodes see │                           │ • Async replica │
│   same data     │                           │   -tion         │
│   instantly     │                           │                 │
├─────────────────┤                           ├─────────────────┤
│ Pros:           │                           │ Pros:           │
│ • Always correct│                           │ • High available│
│ • Simple logic  │                           │ • Scalable      │
│                 │                           │ • Fault tolerant│
├─────────────────┤                           ├─────────────────┤
│ Cons:           │                           │ Cons:           │
│ • Slow          │                           │ • Temporary     │
│ • Single point  │                           │   inconsistency │
│   of failure    │                           │ • Complex logic │
└─────────────────┘                           └─────────────────┘
```

### CAP Theorem Trade-off

This system uses a **hybrid approach**:
- **Stock reservation:** Synchronous (consistency over availability)
- **Notifications/Analytics:** Eventual consistency (availability over consistency)

For the critical order path, we choose **CP** (Consistency + Partition Tolerance):
- **Consistent:** Stock check happens before order confirmation
- **Partition Tolerant:** Services on different networks
- **Not Always Available:** If inventory-service down, orders fail (acceptable trade-off)

### When is Eventual Consistency Acceptable?

| ✅ Acceptable (Async) | ❌ Not Acceptable (Must be Sync) |
|-----------------------|----------------------------------|
| Product search (ES lags 1s) | Stock reservation (user needs feedback) |
| SMS/Email notifications | Payment processing |
| Analytics dashboards | Booking a seat (can't double-book) |
| Delivery assignment | Bank transfers |

---

## Inventory Service Design

### Stock Table Schema

```sql
CREATE TABLE stock (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id      UUID        NOT NULL UNIQUE,   -- 1:1 with product
    quantity        INT         NOT NULL DEFAULT 0, -- available stock
    reserved        INT         NOT NULL DEFAULT 0, -- reserved during order
    warehouse       VARCHAR(120) NOT NULL DEFAULT 'DEFAULT',
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now()
);
```

### Stock Lifecycle

```
1. PRODUCT CREATED (catalog)
   └─► Stock entry created (qty=0, reserved=0)
       [ProductStockConsumer listens to product-events - ASYNC]

2. WAREHOUSE REPLENISHMENT (physical goods arrive)
   └─► Staff adds inventory via API (qty=100)
       [POST /api/stock/{productId}/replenish]

3. ORDER PLACEMENT (user clicks "Place Order")
   └─► SYNC call: Reserve stock (quantity -= orderQty, reserved += orderQty)
       [POST /api/stock/reserve - must succeed before order is created]

4. ORDER CONFIRMED (reservation successful)
   └─► Commit reservation (reserved -= orderQty)
       [Stock already deducted in step 3]

5. ORDER CANCELLED / PAYMENT FAILED
   └─► Release reservation (quantity += reservedQty, reserved -= reservedQty)
       [POST /api/stock/release]
```

### Order Processing Scenarios

**Scenario 1: Full Inventory Available**
```
Order: Product A (5 units)
Stock: Product A (100 available)
Sync Call: POST /api/stock/reserve → {success: true}
Result: Order CONFIRMED, user sees success immediately
```

**Scenario 2: Partial Inventory**
```
Order: Product A (5 units), Product B (3 units)
Stock: Product A (5), Product B (1)
Sync Call: POST /api/stock/reserve → {success: false, available: {A:5, B:1}}
Result: Order NOT created, user sees "Only 1 unit of Product B available"
        Cart NOT cleared, user can adjust and retry
```

**Scenario 3: No Inventory**
```
Order: Product A (5 units)
Stock: Product A (0 available)
Sync Call: POST /api/stock/reserve → {success: false, reason: "OUT_OF_STOCK"}
Result: Order NOT created, user sees "Product A is out of stock"
        Cart NOT cleared, user can remove item and retry
```

### Corrected Event Flow (Sync + Async Hybrid)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    COMPLETE ORDER FLOW (CORRECTED)                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  POST /api/orders?cartId=xyz                                           │
│           │                                                             │
│           ▼                                                             │
│  OrderService                                                           │
│    │                                                                    │
│    ├── 1. Get cart from Redis                                           │
│    │                                                                    │
│    ├── 2. SYNC HTTP call to inventory-service (WebClient)               │
│    │      POST /api/stock/reserve                                       │
│    │      {orderId: "...", items: [{productId, qty}, ...]}             │
│    │           │                                                        │
│    │           ▼                                                        │
│    │      ┌─────────────────────────────────────┐                      │
│    │      │ InventoryService                    │                      │
│    │      │ • Check stock for each item         │                      │
│    │      │ • If ALL available:                 │                      │
│    │      │   - Deduct: quantity -= orderQty    │                      │
│    │      │   - Return {success: true}          │                      │
│    │      │ • If ANY unavailable:               │                      │
│    │      │   - Don't deduct anything           │                      │
│    │      │   - Return {success: false, ...}    │                      │
│    │      └─────────────────────────────────────┘                      │
│    │           │                                                        │
│    │           ▼                                                        │
│    ├── 3. IF reservation FAILED:                                        │
│    │      └── Return 400 "Insufficient stock" (cart NOT cleared)        │
│    │                                                                    │
│    ├── 4. IF reservation SUCCEEDED:                                     │
│    │      ├── Save order (status=CONFIRMED)                             │
│    │      ├── Save order_outbox (ORDER_CONFIRMED)                       │
│    │      ├── Clear cart                                                │
│    │      └── Return 201 "Order confirmed!"                             │
│    │                                                                    │
│           │ ~1 second (outbox polling) - ASYNC from here                │
│           ▼                                                             │
│  Kafka: order-events {ORDER_CONFIRMED, orderId, ...}                   │
│           │                                                             │
│           ├──────────────────┬──────────────────┐                      │
│           ▼                  ▼                  ▼                      │
│  notification-svc     analytics-svc      delivery-svc                  │
│  (send SMS/email)     (update dashboard) (assign rider)                │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Kafka Topics (Updated)

| Topic | Publisher | Consumers | Event Types |
|-------|-----------|-----------|-------------|
| `product-events` | blinkit-app | ProductIndexConsumer (ES), ProductStockConsumer (inventory) | PRODUCT_UPSERTED |
| `order-events` | blinkit-app | NotificationService, AnalyticsService (future) | ORDER_CONFIRMED, ORDER_CANCELLED |

**Note:** `inventory-service` is no longer a Kafka consumer for orders. Stock reservation is done via synchronous HTTP.

---

## Kubernetes Deployment (Phase 3)

This section documents the Kubernetes deployment learnings from setting up the microservices on local K3s (Rancher Desktop).

---

### Kubernetes Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         KUBERNETES CLUSTER (K3s / Rancher Desktop)              │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│   ┌─────────────────────────────────────────────────────────────────────────┐  │
│   │                        NAMESPACE: blinkit                                │  │
│   ├─────────────────────────────────────────────────────────────────────────┤  │
│   │                                                                         │  │
│   │   ┌───────────────────────────────────────────────────────────────┐    │  │
│   │   │              INFRASTRUCTURE LAYER (StatefulSets)               │    │  │
│   │   │                                                                │    │  │
│   │   │   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │    │  │
│   │   │   │  PostgreSQL │  │  PostgreSQL │  │    Redis    │          │    │  │
│   │   │   │   (blinkit) │  │ (inventory) │  │             │          │    │  │
│   │   │   │   :5432     │  │   :5433     │  │   :6379     │          │    │  │
│   │   │   └─────────────┘  └─────────────┘  └─────────────┘          │    │  │
│   │   │                                                                │    │  │
│   │   │   ┌─────────────┐  ┌─────────────┐                           │    │  │
│   │   │   │Elasticsearch│  │  Redpanda   │                           │    │  │
│   │   │   │   :9200     │  │(Kafka):9092 │                           │    │  │
│   │   │   └─────────────┘  └─────────────┘                           │    │  │
│   │   └───────────────────────────────────────────────────────────────┘    │  │
│   │                                                                         │  │
│   │   ┌───────────────────────────────────────────────────────────────┐    │  │
│   │   │              APPLICATION LAYER (Deployments + HPA)             │    │  │
│   │   │                                                                │    │  │
│   │   │   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │    │  │
│   │   │   │   Eureka    │  │ API Gateway │  │ blinkit-app │          │    │  │
│   │   │   │   :8761     │  │   :8085     │  │   :8080     │          │    │  │
│   │   │   └─────────────┘  └─────────────┘  └─────────────┘          │    │  │
│   │   │                                                                │    │  │
│   │   │   ┌─────────────┐                                             │    │  │
│   │   │   │ inventory-  │                                             │    │  │
│   │   │   │  service    │                                             │    │  │
│   │   │   │   :8082     │                                             │    │  │
│   │   │   └─────────────┘                                             │    │  │
│   │   └───────────────────────────────────────────────────────────────┘    │  │
│   │                                                                         │  │
│   │   ┌───────────────────────────────────────────────────────────────┐    │  │
│   │   │              EXTERNAL ACCESS (NodePort)                        │    │  │
│   │   │                                                                │    │  │
│   │   │   localhost:30085 ──► API Gateway ──► blinkit-app/inventory   │    │  │
│   │   │                                                                │    │  │
│   │   └───────────────────────────────────────────────────────────────┘    │  │
│   │                                                                         │  │
│   └─────────────────────────────────────────────────────────────────────────┘  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

### Understanding Kubernetes Declarative State Management

#### Why Do Pods Auto-Restart After Rancher Desktop Restart?

**The Question:**
> "I restarted Rancher Desktop and all my pods came back automatically. I never ran `kubectl apply` again!"

**The Answer — Kubernetes Self-Healing:**

Kubernetes uses a **declarative model**, not an imperative one:

| Approach | How It Works | Example |
|----------|--------------|---------|
| **Imperative** | "Do this action NOW" | `docker run nginx` |
| **Declarative** | "Make sure this state EXISTS" | `kubectl apply -f deployment.yaml` |

When you `kubectl apply`, Kubernetes stores your **desired state** in etcd (its database). The **control plane** constantly monitors the cluster:

```
┌─────────────────────────────────────────────────────────────┐
│                    KUBERNETES CONTROL LOOP                   │
│                                                              │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    │
│   │ etcd stores │    │  Scheduler  │    │  Kubelet    │    │
│   │ "I want 3   │───►│  "Node-1    │───►│  "Start     │    │
│   │  nginx pods"│    │  has room"  │    │  container" │    │
│   └─────────────┘    └─────────────┘    └─────────────┘    │
│          │                                      │           │
│          │         Compare                      │           │
│          ▼         desired vs actual            ▼           │
│   ┌─────────────────────────────────────────────────┐      │
│   │          RECONCILIATION LOOP                     │      │
│   │  "Desired: 3 pods" vs "Actual: 2 pods"          │      │
│   │   → Action: Create 1 more pod                    │      │
│   └─────────────────────────────────────────────────┘      │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**After Rancher Desktop restart:**
1. K3s control plane starts
2. Reads desired state from etcd ("12 pods should exist")
3. Checks actual state (0 pods running)
4. Creates all 12 pods to match desired state

**Analogy:** Kubernetes is like a thermostat. You set desired temperature (68°F), and it constantly adjusts AC/heating to maintain it — you don't tell it "turn on AC now", you tell it "keep it 68°F".

---

### Docker Context Issues — A Common Gotcha

#### The Problem: "My images are gone!"

After Rancher Desktop restart, pods showed `ErrImageNeverPull`:
```
blinkit-app-6f8c8f4b6c-xxxxx   0/1   ErrImageNeverPull   0   5m
```

#### Root Cause: Multiple Docker Contexts

Docker CLI can connect to **different Docker daemons**. Rancher Desktop creates its own:

```bash
> docker context ls

NAME              DESCRIPTION                    DOCKER ENDPOINT
default           Current DOCKER_HOST based...   npipe:////./pipe/docker_engine
desktop-linux     Docker Desktop                 npipe:////./pipe/dockerDesktopLinuxEngine
rancher-desktop * Rancher Desktop moby context   npipe:////./pipe/dockerDesktopLinuxEngine
```

| Context | Where Images Go | Used By |
|---------|-----------------|---------|
| `default` | Docker Desktop's storage | Docker Desktop |
| `desktop-linux` | Docker Desktop's VM | Docker Desktop |
| `rancher-desktop` | K3s's containerd | K3s/Kubernetes |

**If you build in wrong context:**
```bash
# ❌ WRONG: Builds to Docker Desktop, not K3s
docker build -t blinkit-app:v1 .

# ✅ CORRECT: Ensure rancher-desktop context first
docker context use rancher-desktop
docker build -t blinkit-app:v1 .
```

#### The Fix

```bash
# 1. Check current context
docker context ls

# 2. Switch to rancher-desktop
docker context use rancher-desktop

# 3. Rebuild all images
docker build --no-cache -t eureka:v1 ./eureka/
docker build --no-cache -t api-gateway:v1 ./api-gateway/
docker build --no-cache -t blinkit-app:v1 ./blinkit-app/
docker build --no-cache -t inventory-service:v1 ./inventory-service/

# 4. Restart deployments to pick up new images
kubectl rollout restart deployment eureka api-gateway blinkit-app inventory-service -n blinkit
```

---

### Understanding `imagePullPolicy: Never`

In our K8s YAML files, we use:

```yaml
containers:
- name: blinkit-app
  image: blinkit-app:v1
  imagePullPolicy: Never  # ← What does this mean?
```

| imagePullPolicy | Behavior | Use Case |
|-----------------|----------|----------|
| `Always` | Pull from registry every time | Production (ECR, DockerHub) |
| `IfNotPresent` | Pull only if not in local cache | Reduce bandwidth |
| **`Never`** | **Never pull, use local only** | **Local dev with K3s** |

**Why `Never` for local development?**
- Images are built directly into K3s's containerd
- No registry involved (no DockerHub/ECR)
- If image doesn't exist locally → `ErrImageNeverPull` (intentional — tells you to build first!)

---

### Eureka Service Discovery in Kubernetes

#### The Problem: Services Couldn't Find Each Other

After deploying to K8s, API Gateway logged:
```
Request failed... host: blinkit-app-7c8d6f5b4d-xxxxx
java.net.UnknownHostException: blinkit-app-7c8d6f5b4d-xxxxx
```

#### Root Cause: Pods Registering with Hostnames

By default, Eureka instances register with their **hostname**. In Kubernetes, pod hostnames are generated names like `blinkit-app-7c8d6f5b4d-xxxxx`.

**The problem:** Other services can't resolve these hostnames because Kubernetes DNS only knows **service names**, not individual pod names.

```
┌─────────────────────────────────────────────────────────────┐
│                    EUREKA REGISTRATION                       │
│                                                              │
│   DEFAULT BEHAVIOR (❌ BROKEN in K8s):                      │
│   ┌─────────────────┐    ┌───────────────────────────┐     │
│   │   blinkit-app   │───►│ Eureka: "I'm at hostname  │     │
│   │   (Pod)         │    │ blinkit-app-7c8d6f5b4d"   │     │
│   └─────────────────┘    └───────────────────────────┘     │
│                                │                            │
│                                ▼                            │
│   ┌─────────────────┐    ┌───────────────────────────┐     │
│   │   API Gateway   │◄───│ "Connect to hostname:     │     │
│   │   (tries to     │    │  blinkit-app-7c8d6f5b4d"  │     │
│   │   connect)      │    └───────────────────────────┘     │
│   └─────────────────┘              │                        │
│          │                         │                        │
│          ▼                         │                        │
│   ❌ UnknownHostException         ❌ DNS can't resolve     │
│      (K8s DNS doesn't                pod names!             │
│       know pod names)                                       │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### The Fix: Register with IP Address

Add this environment variable to ALL Spring services:

```yaml
# In ConfigMap
EUREKA_INSTANCE_PREFER_IP_ADDRESS: "true"
```

**What it does:**

```
WITH prefer-ip-address=true (✅ WORKS):

┌─────────────────┐    ┌───────────────────────────┐
│   blinkit-app   │───►│ Eureka: "I'm at IP        │
│   (Pod IP:      │    │ 10.42.0.15:8080"          │
│   10.42.0.15)   │    └───────────────────────────┘
└─────────────────┘              │
                                 ▼
┌─────────────────┐    ┌───────────────────────────┐
│   API Gateway   │◄───│ "Connect to 10.42.0.15"   │
│                 │    └───────────────────────────┘
└─────────────────┘
         │
         ▼
✅ Direct IP connection works!
```

---

### Spring Boot Relaxed Binding — How Environment Variables Map to Properties

#### The Pattern

Spring Boot uses **relaxed binding** to convert environment variables to property names:

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
3. Handle special cases (`BASEURL` → `base-url`)

#### Real Example from This Project

**ConfigMap (environment variable):**
```yaml
data:
  APP_ELASTICSEARCH_URL: "http://elasticsearch:9200"
  APP_ELASTIC_BASE_URL: "http://elasticsearch:9200"
```

**application.yml (how Spring reads it):**
```yaml
app:
  elasticsearch:
    url: ${APP_ELASTICSEARCH_URL:http://localhost:9200}
  elastic:
    base-url: ${APP_ELASTIC_BASE_URL:http://localhost:9200}
```

**Java code (how you use it):**
```java
@ConfigurationProperties(prefix = "app.elastic")
public record ElasticProperties(
    String baseUrl,        // ← Maps from APP_ELASTIC_BASE_URL
    String index,
    int timeoutSeconds     // ← Maps from APP_ELASTIC_TIMEOUT_SECONDS
) {}
```

---

### Elasticsearch Timeout Fix — Making Configuration Dynamic

#### The Problem

In Docker Compose, Elasticsearch starts in ~3 seconds. In Kubernetes, it can take 10-15 seconds (resource limits, startup probes, etc.). The hardcoded 3-second timeout caused:

```
Error: java.util.concurrent.TimeoutException: Did not observe any item 
       or terminal signal within 3000ms
```

#### The Fix: Configurable Timeout

**1. Update ElasticProperties.java:**
```java
@ConfigurationProperties(prefix = "app.elastic")
public record ElasticProperties(
    String baseUrl, 
    String index,
    int timeoutSeconds  // NEW: configurable timeout
) {}
```

**2. Update application.yml:**
```yaml
app:
  elastic:
    base-url: ${APP_ELASTIC_BASE_URL:http://localhost:9200}
    index: products_v1
    timeout-seconds: ${APP_ELASTIC_TIMEOUT_SECONDS:10}  # Default 10s
```

**3. Update ElasticIndexService.java:**
```java
public void ensureIndexExists() {
    int timeout = props.timeoutSeconds() > 0 ? props.timeoutSeconds() : 10;
    
    webClient.get()
        .uri("/{index}", props.index())
        .retrieve()
        .toBodilessEntity()
        .timeout(Duration.ofSeconds(timeout))  // Use configurable timeout
        .block();
}
```

**4. ConfigMap in K8s (optional override):**
```yaml
data:
  APP_ELASTIC_TIMEOUT_SECONDS: "15"  # Override if needed
```

---

### Kubernetes YAML File Anatomy — Line-by-Line Explanation

A complete K8s service definition has **4 parts**: ConfigMap, Deployment, Service, HPA.

#### Part 1: ConfigMap (Environment Variables)

```yaml
apiVersion: v1                    # K8s API version for ConfigMap
kind: ConfigMap                   # Resource type
metadata:
  name: blinkit-app-config        # Name to reference this ConfigMap
  namespace: blinkit              # Namespace isolation
data:                             # Key-value pairs (all strings!)
  SPRING_PROFILES_ACTIVE: "k8s"   # Activates k8s profile
  SERVER_PORT: "8080"             # Port app listens on
  APP_ELASTIC_BASE_URL: "http://elasticsearch:9200"  # ES connection
  EUREKA_INSTANCE_PREFER_IP_ADDRESS: "true"          # Fix for K8s DNS
```

**Why ConfigMap?**
- Separates configuration from code
- Can change config without rebuilding image
- Shared across pods

---

#### Part 2: Deployment (Pod Template + Replicas)

```yaml
apiVersion: apps/v1               # API version for Deployments
kind: Deployment                  # Creates and manages pods
metadata:
  name: blinkit-app               # Deployment name
  namespace: blinkit
spec:
  replicas: 2                     # How many identical pods to run
  selector:
    matchLabels:
      app: blinkit-app            # Finds pods with this label
  template:                       # Pod template - what each pod looks like
    metadata:
      labels:
        app: blinkit-app          # Label pods for Service to find them
    spec:
      containers:
      - name: blinkit-app
        image: blinkit-app:v1     # Docker image to use
        imagePullPolicy: Never    # Don't pull from registry (local only)
        ports:
        - containerPort: 8080     # Container exposes this port
        envFrom:
        - configMapRef:
            name: blinkit-app-config   # Inject ALL keys from ConfigMap
        - secretRef:
            name: blinkit-app-secrets  # Inject ALL keys from Secret
        resources:
          requests:               # Minimum resources guaranteed
            memory: "256Mi"
            cpu: "100m"           # 0.1 CPU cores
          limits:                 # Maximum resources allowed
            memory: "512Mi"
            cpu: "500m"           # 0.5 CPU cores
        readinessProbe:           # Is pod ready to receive traffic?
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        livenessProbe:            # Should K8s restart this pod?
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 45
          periodSeconds: 15
```

**Key Concepts:**

| Concept | What It Does | Analogy |
|---------|--------------|---------|
| `replicas: 2` | Run 2 identical pods | 2 cashiers at checkout |
| `selector.matchLabels` | How Deployment finds its pods | Employee badge |
| `envFrom.configMapRef` | Inject env vars from ConfigMap | Loading .env file |
| `resources.requests` | Minimum guaranteed resources | Reserved parking spot |
| `resources.limits` | Maximum allowed resources | Speed limit |
| `readinessProbe` | Is pod ready for traffic? | "Open" sign on store |
| `livenessProbe` | Is pod alive? (restart if not) | Heart monitor |

---

#### Part 3: Service (Stable Network Endpoint)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: blinkit-app               # Service name = DNS name!
  namespace: blinkit
spec:
  selector:
    app: blinkit-app              # Route traffic to pods with this label
  ports:
  - port: 8080                    # Port the Service listens on
    targetPort: 8080              # Port on the container
  type: ClusterIP                 # Only accessible inside cluster
```

**Service Types:**

| Type | Accessibility | Use Case |
|------|---------------|----------|
| `ClusterIP` | Inside cluster only | Internal services |
| `NodePort` | Outside via node IP:port | Dev/testing access |
| `LoadBalancer` | External load balancer | Production (cloud) |

**Why Service?**
- Pods are ephemeral (IP changes on restart)
- Service provides stable DNS name: `blinkit-app.blinkit.svc.cluster.local`
- Load balances across all matching pods

```
┌─────────────────────────────────────────────────────────────┐
│                    SERVICE LOAD BALANCING                    │
│                                                              │
│   Request to "blinkit-app:8080"                             │
│           │                                                  │
│           ▼                                                  │
│   ┌───────────────┐                                         │
│   │   Service     │                                         │
│   │  blinkit-app  │                                         │
│   └───────┬───────┘                                         │
│           │                                                  │
│     ┌─────┼─────┐                                           │
│     ▼     ▼     ▼                                           │
│   ┌───┐ ┌───┐ ┌───┐                                         │
│   │Pod│ │Pod│ │Pod│  (labels: app=blinkit-app)              │
│   │ 1 │ │ 2 │ │ 3 │                                         │
│   └───┘ └───┘ └───┘                                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

#### Part 4: HorizontalPodAutoscaler (Auto-scaling)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: blinkit-app-hpa
  namespace: blinkit
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: blinkit-app             # Which Deployment to scale
  minReplicas: 1                  # Minimum pods (even at 0 load)
  maxReplicas: 5                  # Maximum pods (cost control)
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70    # Scale up if CPU > 70%
```

**How It Works:**
```
CPU at 30%  →  1 pod (minReplicas)
CPU at 75%  →  Scale up! Now 2 pods
CPU at 80%  →  Scale up! Now 3 pods
CPU drops to 40%  →  Scale down to 2 pods
```

---

### kubectl Context — Local vs Cloud Clusters

#### The Problem: "I'm seeing AWS resources!"

```bash
> kubectl get pods
NAME                           READY   STATUS    RESTARTS   AGE
aws-load-balancer-controller   1/1     Running   0          5d
coredns-7b9f8c6d8b-xxxxx       1/1     Running   0          5d
```

**Wait, I'm working locally! Why am I seeing AWS pods?**

#### Root Cause: Wrong kubectl Context

kubectl can connect to multiple clusters. It uses **contexts** to know which one:

```bash
> kubectl config get-contexts

CURRENT   NAME                                      CLUSTER
          arn:aws:eks:us-east-1:123:cluster/prod    aws-prod-cluster
*         rancher-desktop                           rancher-desktop
```

The `*` shows current context. If you previously connected to AWS EKS, that context might still be active!

#### The Fix

```bash
# See all contexts
kubectl config get-contexts

# Switch to local
kubectl config use-context rancher-desktop

# Verify
kubectl config current-context
# Output: rancher-desktop
```

---

### AWS Production Deployment Strategy (Conceptual)

#### How Would We Deploy to AWS?

**Architecture Change:**

| Aspect | Local (K3s) | Production (AWS EKS) |
|--------|-------------|----------------------|
| Image Storage | Local containerd | Amazon ECR |
| Cluster | Single-node K3s | Multi-node EKS |
| `imagePullPolicy` | `Never` | `Always` |
| Database | In-cluster pods | Amazon RDS |
| Load Balancer | NodePort | AWS ALB |
| Secrets | K8s Secrets | AWS Secrets Manager |

**CI/CD Pipeline Flow:**

```
┌─────────────────────────────────────────────────────────────┐
│                    PRODUCTION CI/CD                          │
│                                                              │
│   Git Push                                                   │
│       │                                                      │
│       ▼                                                      │
│   GitHub Actions                                             │
│       │                                                      │
│       ├── 1. Build Docker images                             │
│       │                                                      │
│       ├── 2. Push to Amazon ECR                              │
│       │      aws ecr get-login-password | docker login       │
│       │      docker push 123456789.dkr.ecr.../blinkit-app   │
│       │                                                      │
│       ├── 3. Update K8s manifests (new image tag)           │
│       │                                                      │
│       └── 4. kubectl apply -f k8s/prod/                      │
│              (or ArgoCD sync)                                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**Production YAML Changes:**

```yaml
# k8s/prod/11-blinkit-app.yaml
spec:
  containers:
  - name: blinkit-app
    image: 123456789.dkr.ecr.us-east-1.amazonaws.com/blinkit-app:v1.2.3
    imagePullPolicy: Always   # ← Changed from Never
```

---

### Troubleshooting Cheat Sheet

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| `ErrImageNeverPull` | Image not in K3s containerd | Build with correct Docker context |
| `ImagePullBackOff` | Can't pull from registry | Check registry credentials / image name |
| `CrashLoopBackOff` | App crashing on startup | Check logs: `kubectl logs <pod>` |
| `UnknownHostException` | Eureka hostname issue | Add `EUREKA_INSTANCE_PREFER_IP_ADDRESS: "true"` |
| `Connection refused` | Service not ready | Check readinessProbe, wait for startup |
| Pods in `Pending` | No node has enough resources | Check `kubectl describe pod` for events |
| Timeout errors | Network/DNS issues | Check service names, increase timeouts |
| Wrong cluster resources | kubectl context wrong | `kubectl config use-context rancher-desktop` |

---

### Useful Kubernetes Commands

```bash
# === CLUSTER INFO ===
kubectl cluster-info                     # Is cluster running?
kubectl config current-context           # Which cluster am I connected to?
kubectl config get-contexts              # List all contexts
kubectl config use-context rancher-desktop  # Switch context

# === NAMESPACE ===
kubectl get namespaces                   # List all namespaces
kubectl create namespace blinkit         # Create namespace

# === DEPLOYMENTS ===
kubectl get deployments -n blinkit       # List deployments
kubectl rollout status deployment/blinkit-app -n blinkit  # Watch rollout
kubectl rollout restart deployment/blinkit-app -n blinkit # Restart pods

# === PODS ===
kubectl get pods -n blinkit              # List pods
kubectl get pods -n blinkit -o wide      # Show IPs and nodes
kubectl describe pod <pod-name> -n blinkit  # Detailed info + events
kubectl logs <pod-name> -n blinkit       # View logs
kubectl logs <pod-name> -n blinkit -f    # Follow logs (tail -f)
kubectl exec -it <pod-name> -n blinkit -- /bin/sh  # Shell into pod

# === SERVICES ===
kubectl get svc -n blinkit               # List services
kubectl describe svc api-gateway -n blinkit  # Service details

# === APPLY / DELETE ===
kubectl apply -f k8s/ -n blinkit         # Apply all YAML files
kubectl delete -f k8s/ -n blinkit        # Delete all resources
kubectl apply -f 11-blinkit-app.yaml -n blinkit  # Apply single file

# === DEBUG ===
kubectl get events -n blinkit --sort-by='.lastTimestamp'  # Recent events
kubectl top pods -n blinkit              # CPU/Memory usage (needs metrics-server)
```

---

## Design Decisions & Rationale

### Why Not SKU in Stock Table?

**SKU (Stock Keeping Unit)** is a human-readable product identifier (e.g., `AMUL-BTR-100G`).

| Use Case | product_id (UUID) | SKU |
|----------|-------------------|-----|
| Database foreign keys | ✅ Perfect | ❌ Avoid |
| Barcode scanning | ❌ Can't scan | ✅ Scannable |
| Warehouse reports | ❌ Unreadable | ✅ Human-friendly |

**Decision:** For this learning project, `product_id` (UUID) is sufficient. SKU can be added later for warehouse features.

### Why Stock Entry Created with qty=0?

```
Real World Timeline:
────────────────────
Day 1: Admin adds "Amul Butter 100g" to catalog
       └─► Stock entry created: qty=0 (no physical goods yet)
       └─► Product visible on app as "Out of Stock"

Day 3: Shipment arrives at warehouse
       └─► Staff scans/counts items
       └─► API call: replenish(productId, 500)
       └─► Stock updated: qty=500, now "In Stock"
```

**Product catalog exists BEFORE physical inventory arrives.** These are different business events.

### Batch Processing for Bulk Operations

For 1000 products bulk upload:

| Approach | Kafka Events | DB Calls | Efficiency |
|----------|--------------|----------|------------|
| Individual inserts | 1000 | 1000 | ❌ Slow |
| Batch consumer | 1000 | ~10-20 | ✅ Good |
| Single bulk event | 1 | 1 | ✅ Best |

**Recommendation:** Use batch Kafka consumer for normal flow, bulk events for CSV uploads.

### Sync vs Async for Inter-Service Communication

**Initial (Incorrect) Assumption:**
```java
// ❌ We initially thought fully async was better
// "If inventory-service down, order still succeeds!"
// But this leads to false promises and bad UX
```

**Corrected Understanding:**
```java
// ✅ SYNC for critical path (stock reservation)
@Transactional
public OrderResponse placeOrder(String cartId) {
    CartResponse cart = cartService.getCart(cartId);
    
    // SYNC HTTP call - user needs immediate feedback
    ReserveResponse reservation = inventoryClient.reserveStock(cart.items());
    
    if (!reservation.success()) {
        // Don't create order, don't clear cart
        throw new InsufficientStockException(reservation.unavailableItems());
    }
    
    // Only proceed if stock reserved
    Order order = createAndSaveOrder(cart, reservation);
    saveOutboxEvent(order, "ORDER_CONFIRMED");  // Async notifications
    cartService.clearCart(cartId);
    
    return buildResponse(order);
}
```

**Key Insight:** Not everything should be async. Critical path operations that affect user feedback must be synchronous, even in microservices architecture.
