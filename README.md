docker context create rancher-desktop --docker "host=npipe:////./pipe/rancher-desktop"docker context create rancher-desktop --docker "host=npipe:////./pipe/rancher-desktop"docker context use rancher-desktop# Blinkit Clone – Phase 1 Starter (DB + Elasticsearch Indexing)

Phase 1 goal:
1. Store Owner loads Products into **Postgres** via REST API.
2. Product Service persists to DB and **indexes the same product into Elasticsearch**.
3. You can verify indexed products in **Kibana (Elastic UI)**.

## Tech
- Java 17
- Spring Boot (REST + JPA + Flyway)
- Postgres (source of truth)
- Elasticsearch + Kibana (for browsing/search/index visibility)
- Redis (cart storage with TTL)
- Docker Compose for local infra

---

## 1) Start local infra (Postgres + ES + Kibana + Redis)

From project root:

```bash
docker compose up -d
```

Check:
```bash
curl http://localhost:9200
# Kibana UI: http://localhost:5601
```

---

## 2) Run the Spring Boot app

```bash
mvn clean spring-boot:run
```

App runs on: http://localhost:8080

---

## 3) Create a product (DB + Elasticsearch)

```bash
curl -X POST "http://localhost:8080/api/products" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Amul Taaza Milk 1L",
    "brand": "Amul",
    "category": "Dairy",
    "price": 72.00,
    "active": true
  }'
```

Response will include the generated UUID.

---

## 4) Verify in Elasticsearch (quick CLI check)

```bash
curl "http://localhost:9200/products_v1/_search?pretty"
```

---

## 5) Verify in Kibana (Elastic UI)

1. Open Kibana: http://localhost:5601
2. Go to **Stack Management → Data Views**
3. Create a data view:
   - Name: `products`
   - Index pattern: `products_v1`
   - Time field: **I don’t want to use a time filter**
4. Go to **Discover** and select `products` data view.

You should see the indexed product document(s).

---

## Notes / Next steps
- For Phase 1 we do **synchronous indexing** (DB save → index into ES in the same request).
- In later phases we’ll upgrade to **Outbox pattern + async indexing** for reliability and performance.

---

## 6) Cart API quickstart (Redis-backed)

Cart APIs use header `X-Cart-Id` (UUID). Use the same cart id across requests.

### 6.1 Create a cart id and pick one active product id (PowerShell)

```powershell
$cartId = [guid]::NewGuid().ToString()
$productId = (docker exec blinkit-phase1-starter-postgres-1 psql -U blinkit -d blinkit -t -A -c "select id from products where active=true limit 1;").Trim()
Write-Host "CartId: $cartId"
Write-Host "ProductId: $productId"
```

### 6.2 Get empty cart

```powershell
Invoke-RestMethod -Method Get "http://localhost:8080/api/cart" -Headers @{"X-Cart-Id"=$cartId}
```

### 6.3 Add item to cart

```powershell
$body = @{ productId = $productId; quantity = 2 } | ConvertTo-Json
Invoke-RestMethod -Method Post "http://localhost:8080/api/cart/items" `
  -Headers @{"X-Cart-Id"=$cartId} -ContentType "application/json" -Body $body
```

### 6.4 Update quantity

```powershell
$body = @{ quantity = 5 } | ConvertTo-Json
Invoke-RestMethod -Method Put "http://localhost:8080/api/cart/items/$productId" `
  -Headers @{"X-Cart-Id"=$cartId} -ContentType "application/json" -Body $body
```

### 6.5 Remove item

```powershell
Invoke-RestMethod -Method Delete "http://localhost:8080/api/cart/items/$productId" -Headers @{"X-Cart-Id"=$cartId}
```

### 6.6 Clear cart

```powershell
Invoke-RestMethod -Method Delete "http://localhost:8080/api/cart" -Headers @{"X-Cart-Id"=$cartId}
```

### 6.7 Optional Redis verification

```powershell
docker exec blinkit-phase1-starter-redis-1 redis-cli KEYS "cart:*"
docker exec blinkit-phase1-starter-redis-1 redis-cli GET "cart:$cartId"
```
