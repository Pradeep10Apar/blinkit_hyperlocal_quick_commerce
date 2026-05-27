# 🎓 Kafka Playground — Learn Kafka Hands-On

A small Spring Boot project to learn Apache Kafka concepts interactively.

## Architecture

```
┌──────────────┐       ┌─────────────────────────────┐       ┌──────────────────┐
│  REST API    │       │        KAFKA BROKER          │       │    CONSUMERS     │
│  (You call)  │──────►│  ┌─────────────────────┐    │──────►│                  │
│              │       │  │ Topic: greetings     │    │       │ Greetings Logger │
│ POST /greet  │       │  │  P0 | P1 | P2       │    │       │                  │
│ POST /order  │       │  ├─────────────────────┤    │       │ Order Processor  │
│ POST /notify │       │  │ Topic: orders        │    │       │                  │
│              │       │  │  P0 | P1 | P2       │    │       │ Email Service    │
│              │       │  ├─────────────────────┤    │       │ SMS Service      │
│              │       │  │ Topic: notifications │    │       │ (both get msgs!) │
│              │       │  │  P0 | P1            │    │       │                  │
└──────────────┘       └─────────────────────────────┘       └──────────────────┘
```

## Prerequisites

- **Docker Desktop** running
- **Java 21** installed
- **Maven** installed

---

## 🚀 Step 1: Start Kafka with Docker

```bash
cd kafka-playground
docker compose up -d
```

This starts:
- **Kafka** on `localhost:9092` (KRaft mode, no Zookeeper!)
- **Kafka UI** on `http://localhost:8090` (visual dashboard)

Open **http://localhost:8090** in your browser to see the Kafka UI.

## 🚀 Step 2: Run the Spring Boot App

```bash
cd kafka-playground
mvn spring-boot:run
```

The app will:
1. Connect to Kafka at `localhost:9092`
2. Auto-create 3 topics: `greetings`, `orders`, `notifications`
3. Start listening for messages on all topics

---

## 🧪 Step 3: Play with the APIs

### Experiment 1: Simple Message (no key)
```bash
curl -X POST "http://localhost:8080/api/kafka/greet?message=Hello Kafka!"
curl -X POST "http://localhost:8080/api/kafka/greet?message=Second message"
curl -X POST "http://localhost:8080/api/kafka/greet?message=Third message"
```
👀 Watch the console — messages go to **different partitions** (round-robin).

### Experiment 2: Message with Key (same key → same partition)
```bash
curl -X POST "http://localhost:8080/api/kafka/greet-with-key?key=user-A&message=Hello"
curl -X POST "http://localhost:8080/api/kafka/greet-with-key?key=user-A&message=World"
curl -X POST "http://localhost:8080/api/kafka/greet-with-key?key=user-B&message=Hi"
```
👀 Notice: `user-A` messages ALWAYS go to the **same partition**.

### Experiment 3: JSON Order Events
```bash
curl -X POST "http://localhost:8080/api/kafka/order?customer=cust-1&product=iPhone&qty=2&price=999"
curl -X POST "http://localhost:8080/api/kafka/order?customer=cust-1&product=Case&qty=1&price=29"
curl -X POST "http://localhost:8080/api/kafka/order?customer=cust-2&product=Laptop&qty=1&price=1999"
```
👀 Orders for `cust-1` always go to **same partition** (key = customerId).

### Experiment 4: Send to Specific Partition
```bash
curl -X POST "http://localhost:8080/api/kafka/send-to-partition?partition=0&message=I go to P0"
curl -X POST "http://localhost:8080/api/kafka/send-to-partition?partition=1&message=I go to P1"
curl -X POST "http://localhost:8080/api/kafka/send-to-partition?partition=2&message=I go to P2"
```

### Experiment 5: Consumer Groups (notifications)
```bash
curl -X POST "http://localhost:8080/api/kafka/notify?message=Your order has shipped!"
```
👀 Watch console — **BOTH** email and SMS consumers receive the message!
(Because they have different `groupId`s)

---

## 📊 Step 4: Explore in Kafka UI

Open **http://localhost:8090** and explore:
- **Topics** → see `greetings`, `orders`, `notifications`
- Click a topic → **Messages** tab → see all messages
- **Partitions** tab → see how messages are distributed
- **Consumer Groups** → see which consumers are reading

---

## 🧠 Key Concepts to Understand

| Concept | What it means |
|---------|---------------|
| **Topic** | A named channel/category for messages |
| **Partition** | A sub-division of a topic for parallelism |
| **Producer** | Sends messages to a topic |
| **Consumer** | Reads messages from a topic |
| **Consumer Group** | Multiple consumers sharing work on a topic |
| **Key** | Determines which partition a message goes to |
| **Offset** | Position of a message within a partition |
| **KRaft** | Kafka's built-in consensus (replaces ZooKeeper) |

---

## 🛑 Cleanup

```bash
docker compose down -v     # Stop Kafka and remove data
```
