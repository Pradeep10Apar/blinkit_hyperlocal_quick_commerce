# 🚀 NearBuy - Hyperlocal Quick Commerce Platform

A production-grade **hyperlocal quick commerce backend** (Blinkit/Zepto clone) built with **Spring Boot 3**, **microservices architecture**, and **event-driven design**. This platform enables 10-minute grocery delivery with real-time inventory management, full-text search, and scalable order processing.

![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.6-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Elasticsearch](https://img.shields.io/badge/Elasticsearch-8.15-005571?logo=elasticsearch&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)
![Kafka](https://img.shields.io/badge/Kafka-Redpanda-000000?logo=apachekafka&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-Ready-326CE5?logo=kubernetes&logoColor=white)

---

## 📋 Table of Contents

- [Features](#-features)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Microservices](#-microservices)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [API Documentation](#-api-documentation)
- [Key Design Patterns](#-key-design-patterns)
- [Kubernetes Deployment](#-kubernetes-deployment)
- [Frontend Integration](#-frontend-integration)

---

## ✨ Features

### Core Business Features
- 🛒 **Shopping Cart** - Redis-backed cart with TTL, real-time updates
- 🔍 **Product Search** - Full-text search with Elasticsearch (fuzzy matching, typo tolerance)
- 📦 **Inventory Management** - Real-time stock tracking with reservation system
- 🛍️ **Order Processing** - Transactional order placement with stock validation
- 👤 **User Authentication** - JWT-based auth with refresh tokens
- 📤 **Bulk Upload** - CSV-based product catalog upload (batch processing)

### Technical Features
- ⚡ **Event-Driven Architecture** - Kafka-based async communication
- 🔄 **Transactional Outbox Pattern** - Guaranteed event delivery
- 🎯 **CQRS Pattern** - Separated read (Elasticsearch) and write (PostgreSQL) paths
- 🔐 **Service Discovery** - Netflix Eureka for microservice registration
- 🌐 **API Gateway** - Spring Cloud Gateway for routing and load balancing
- 📊 **Observability** - Health checks, metrics, and logging

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              FRONTEND (React)                                    │
│                         http://localhost:5173                                    │
└─────────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           API GATEWAY (Spring Cloud)                             │
│                              Port: 8080                                          │
│    • Request Routing    • Load Balancing    • Rate Limiting    • CORS           │
└─────────────────────────────────────────────────────────────────────────────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    ▼                  ▼                  ▼
┌──────────────────────────┐ ┌──────────────────┐ ┌──────────────────────────┐
│     BLINKIT-APP          │ │ INVENTORY-SERVICE│ │    DISCOVERY-SERVER      │
│     Port: 8080           │ │   Port: 8082     │ │    (Eureka) :8761        │
│                          │ │                  │ │                          │
│ • Product CRUD           │ │ • Stock CRUD     │ │ • Service Registration   │
│ • Cart Management        │ │ • Reservation    │ │ • Health Monitoring      │
│ • Order Processing       │ │ • Auto-create    │ │ • Load Balancing         │
│ • User Auth (JWT)        │ │   on product     │ └──────────────────────────┘
│ • Search (ES)            │ │   event          │
└───────────┬──────────────┘ └────────┬─────────┘
            │                         │
            │    ┌────────────────────┤
            ▼    ▼                    ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                              MESSAGE BROKER                                       │
│                        Kafka (Redpanda) - Port: 9092                             │
│                                                                                   │
│   Topics: product-events, order-events                                           │
│   Pattern: Transactional Outbox → Poller → Kafka → Consumer                     │
└──────────────────────────────────────────────────────────────────────────────────┘
            │                         │
            ▼                         ▼
┌───────────────────────┐  ┌───────────────────────┐  ┌───────────────────────┐
│     PostgreSQL        │  │     PostgreSQL        │  │       Redis           │
│   (blinkit DB)        │  │   (inventory_db)      │  │     Port: 6379        │
│    Port: 5432         │  │     Port: 5433        │  │                       │
│                       │  │                       │  │ • Cart Storage        │
│ • Products            │  │ • Stock Levels        │  │ • TTL: 7 days         │
│ • Orders              │  │ • Reservations        │  │ • AOF Persistence     │
│ • Users               │  │                       │  │                       │
│ • Outbox Events       │  │                       │  │                       │
└───────────────────────┘  └───────────────────────┘  └───────────────────────┘
            │
            ▼
┌───────────────────────┐
│    Elasticsearch      │
│     Port: 9200        │
│                       │
│ • Product Index       │
│ • Full-text Search    │
│ • Fuzzy Matching      │
└───────────────────────┘
```

---

## 🛠 Tech Stack

### Backend Framework

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 17 | Language (LTS) |
| **Spring Boot** | 3.3.6 | Application Framework |
| **Spring Cloud** | 2023.0.3 | Microservices (Eureka, Gateway) |
| **Spring Data JPA** | - | Database ORM |
| **Spring Kafka** | - | Event Streaming |
| **Spring Security** | - | Authentication & Authorization |

### Data Stores

| Technology | Version | Purpose |
|------------|---------|---------|
| **PostgreSQL** | 16 | Primary Database (ACID) |
| **Elasticsearch** | 8.15.2 | Search Engine (Full-text) |
| **Redis** | 7-alpine | Cart Cache (In-memory) |
| **Kafka (Redpanda)** | latest | Message Broker |

### DevOps & Tooling

| Technology | Purpose |
|------------|---------|
| **Docker Compose** | Local Development |
| **Kubernetes** | Production Deployment |
| **Flyway** | Database Migrations |
| **Lombok** | Boilerplate Reduction |
| **JWT (jjwt)** | Token-based Auth |

---

## 📦 Microservices

| Service | Port | Description |
|---------|------|-------------|
| **discovery-server** | 8761 | Netflix Eureka for service discovery |
| **api-gateway** | 8080 | Spring Cloud Gateway for routing |
| **blinkit-app** | 8080 | Core business logic (Products, Cart, Orders, Auth) |
| **inventory-service** | 8082 | Stock management and reservation |
| **inventory-api** | - | OpenAPI-generated client library |

---

## 📁 Project Structure

```
blinkit-phase1-starter/
├── docker-compose.yml          # Local infrastructure
├── pom.xml                     # Parent POM (multi-module)
├── DOCUMENTATION.md            # Detailed technical docs
│
├── discovery-server/           # Eureka Service Registry
│   └── src/main/java/
│       └── DiscoveryServerApplication.java
│
├── api-gateway/                # API Gateway
│   └── src/main/resources/
│       └── application.yml     # Route definitions
│
├── blinkit-app/                # Main Application Service
│   └── src/main/java/com/blinkit/phase1/
│       ├── auth/               # JWT Authentication
│       │   ├── AuthController.java
│       │   ├── AuthService.java
│       │   ├── SecurityConfig.java
│       │   └── jwt/            # JWT utilities
│       ├── cart/               # Redis Cart
│       │   ├── CartController.java
│       │   ├── CartService.java
│       │   └── CartState.java
│       ├── product/            # Product Catalog
│       │   ├── ProductController.java
│       │   ├── ProductService.java
│       │   ├── ProductSearchService.java
│       │   ├── outbox/         # Transactional Outbox
│       │   └── bulk_upload/    # CSV Processing
│       ├── order/              # Order Management
│       │   ├── OrderController.java
│       │   ├── OrderService.java
│       │   └── outbox/
│       ├── inventory/          # Inventory Client
│       └── elastic/            # Elasticsearch Integration
│
├── inventory-service/          # Inventory Microservice
│   └── src/main/java/com/blinkit/inventory/
│       ├── controller/
│       ├── service/
│       ├── entity/
│       └── consumer/           # Kafka event consumer
│
├── inventory-api/              # OpenAPI Client Library
│   └── src/main/resources/openapi/
│       └── inventory-api.yaml
│
└── k8s/                        # Kubernetes Manifests
    ├── 00-namespace.yaml
    ├── 01-postgres.yaml
    ├── 02-redis.yaml
    ├── 03-kafka.yaml
    ├── 04-elasticsearch.yaml
    ├── 10-eureka.yaml
    ├── 11-blinkit-app.yaml
    ├── 12-inventory-service.yaml
    └── 13-api-gateway.yaml
```

---

## 🚀 Getting Started

### Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Docker Desktop** (with 6GB+ RAM allocated)
- **Git**

### 1. Clone the Repository

```bash
git clone https://github.com/Pradeep10Apar/blinkit_clone.git
cd blinkit_clone
git checkout feature/papar/hyperlocal_quick_commerce
```

### 2. Start Infrastructure

```bash
docker compose up -d
```

This starts:
- PostgreSQL (2 instances: blinkit + inventory)
- Elasticsearch + Kibana
- Redis
- Redpanda (Kafka) + Console

### 3. Wait for Services

```bash
# Check all services are healthy
docker compose ps

# Verify Elasticsearch
curl http://localhost:9200
```

### 4. Run the Applications

```bash
# Terminal 1: Discovery Server
mvn spring-boot:run -pl discovery-server

# Terminal 2: Main App
mvn spring-boot:run -pl blinkit-app

# Terminal 3: Inventory Service
mvn spring-boot:run -pl inventory-service

# Terminal 4: API Gateway (optional)
mvn spring-boot:run -pl api-gateway
```

### 5. Verify

| Service | URL |
|---------|-----|
| **API** | http://localhost:8080/api/products |
| **Eureka Dashboard** | http://localhost:8761 |
| **Kibana** | http://localhost:5601 |
| **Kafka Console** | http://localhost:8081 |

---

## 📚 API Documentation

### Product APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/products` | Create product |
| `GET` | `/api/products/{id}` | Get product by ID |
| `GET` | `/api/products/search?q={query}` | Full-text search |
| `POST` | `/api/products/bulk/upload-csv` | Bulk CSV upload |

### Cart APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/cart` | Get cart (Header: `X-Cart-Id`) |
| `POST` | `/api/cart/items` | Add item to cart |
| `PUT` | `/api/cart/items/{productId}` | Update quantity |
| `DELETE` | `/api/cart/items/{productId}` | Remove item |
| `DELETE` | `/api/cart` | Clear cart |

### Order APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/orders` | Place order from cart |
| `GET` | `/api/orders/{id}` | Get order details |
| `GET` | `/api/orders` | List user orders |

### Auth APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/register` | User registration |
| `POST` | `/api/auth/login` | Login (returns JWT) |
| `POST` | `/api/auth/refresh` | Refresh access token |

### Inventory APIs (Internal)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/inventory/{productId}` | Get stock level |
| `POST` | `/api/inventory/reserve` | Reserve stock |

---

## 🎯 Key Design Patterns

### 1. Transactional Outbox Pattern
```
┌─────────────────────────────────────────────────────────────┐
│                    @Transactional                           │
│  ┌─────────────┐     ┌─────────────┐                       │
│  │  products   │     │product_outbox│  (status=NEW)        │
│  │   INSERT    │ +   │   INSERT    │                       │
│  └─────────────┘     └─────────────┘                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ @Scheduled (1 sec)
                              ▼
                    ProductOutboxPublisher
                              │
                              ▼ Kafka
                    ProductIndexConsumer
                              │
                              ▼
                       Elasticsearch
```

### 2. CQRS (Command Query Responsibility Segregation)
- **Writes** → PostgreSQL (source of truth)
- **Reads/Search** → Elasticsearch (optimized for queries)

### 3. Event-Driven Communication
- Product created → Kafka → Inventory auto-creates stock entry
- Order placed → Kafka → Inventory reserves stock

### 4. Redis Cart Pattern
- Shopping cart stored in Redis with 7-day TTL
- Product validation against PostgreSQL on every operation
- Cart cleared upon order placement

---

## ☸️ Kubernetes Deployment

### Deploy to Local Kubernetes

```bash
# Using provided script (Windows)
.\k8s-deploy.bat

# Or manually
kubectl apply -f k8s/
```

### K8s Resources

| Resource | Replicas | Purpose |
|----------|----------|---------|
| Namespace | 1 | `blinkit` isolation |
| PostgreSQL | 2 | Stateful databases |
| Redis | 1 | Cart cache |
| Elasticsearch | 1 | Search engine |
| Redpanda | 1 | Message broker |
| Eureka | 1 | Service discovery |
| Blinkit-App | 2 | Main service (scaled) |
| Inventory-Service | 2 | Inventory (scaled) |
| API-Gateway | 1 | Entry point |

---

## 🎨 Frontend Integration

**Frontend Repository:** [blinkit-hyperlocal_frontend](https://github.com/Pradeep10Apar/blinkit-hyperlocal_frontend)

- React 18 + TypeScript + Vite
- TanStack Query for data fetching
- Tailwind CSS for styling

```bash
# Clone frontend
git clone https://github.com/Pradeep10Apar/blinkit-hyperlocal_frontend.git
cd blinkit-hyperlocal_frontend
npm install
npm run dev
```

---

## 📊 Monitoring URLs

| Service | URL | Purpose |
|---------|-----|---------|
| **Kibana** | http://localhost:5601 | Elasticsearch UI |
| **Kafka Console** | http://localhost:8081 | Topic browser |
| **Eureka** | http://localhost:8761 | Service registry |
| **Health Check** | http://localhost:8080/actuator/health | App health |

---

## 📄 License

This project is for educational and portfolio purposes.

---

## 👨‍💻 Author

**Pradeep Aparanji**

- GitHub: [@Pradeep10Apar](https://github.com/Pradeep10Apar)

---

## 🙏 Acknowledgments

- Inspired by Blinkit, Zepto, and Dunzo
- Built with Spring Boot and modern microservices patterns
- Designed for scalability and production readiness
