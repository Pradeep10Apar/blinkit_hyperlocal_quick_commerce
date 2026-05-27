# 📘 Kafka Playground — Technical Deep-Dive Guide

> This document explains every class in the project, what each line does,
> and the Kafka concepts behind it. Read this alongside the code.

---

## Table of Contents

1. [Project Overview & Data Flow](#1-project-overview--data-flow)
2. [Docker Compose — Infrastructure](#2-docker-compose--infrastructure)
3. [application.yml — Spring Kafka Defaults](#3-applicationyml--spring-kafka-defaults)
4. [KafkaTopicConfig — Topic & Partition Creation](#4-kafkatopicconfig--topic--partition-creation)
5. [KafkaConfig — Serialization / Deserialization Setup](#5-kafkaconfig--serialization--deserialization-setup)
6. [MessageProducer — The Publisher](#6-messageproducer--the-publisher)
7. [MessageConsumer — The Subscriber](#7-messageconsumer--the-subscriber)
8. [KafkaController — REST API Layer](#8-kafkacontroller--rest-api-layer)
9. [OrderEvent — The Message Model](#9-orderevent--the-message-model)
10. [KafkaProperties — Config-Driven Architecture](#10-kafkaproperties--config-driven-architecture)
11. [Key Kafka Concepts Explained](#11-key-kafka-concepts-explained)
12. [Message Lifecycle — End to End](#12-message-lifecycle--end-to-end)
13. [Common Interview Questions](#13-common-interview-questions)

---

## 1. Project Overview & Data Flow

```
                          KAFKA PLAYGROUND — END-TO-END FLOW
 ┌──────────┐                                                        ┌──────────────┐
 │  You /   │   HTTP POST                                            │  Console     │
 │  Postman │ ──────────► KafkaController ──► MessageProducer        │  Logs        │
 │  curl    │                                     │                  │  (output)    │
 └──────────┘                                     │                  └──────┬───────┘
                                                  │ send()                  ▲
                                                  ▼                         │ log.info()
                                        ┌─────────────────┐                │
                                        │   KAFKA BROKER   │       ┌───────┴────────┐
                                        │   (Docker)       │──────►│ MessageConsumer │
                                        │                  │       │ (@KafkaListener)│
                                        │  ┌───────────┐   │       └────────────────┘
                                        │  │ greetings │   │
                                        │  │ P0|P1|P2  │   │
                                        │  ├───────────┤   │
                                        │  │ orders    │   │
                                        │  │ P0|P1|P2  │   │
                                        │  ├───────────┤   │
                                        │  │notificatns│   │
                                        │  │ P0|P1     │   │
                                        │  └───────────┘   │
                                        └─────────────────┘
```

**The flow is:**
1. You call a REST endpoint (e.g., `POST /api/kafka/greet`)
2. `KafkaController` receives the HTTP request
3. It calls `MessageProducer` which uses `KafkaTemplate` to send a message to a **topic**
4. Kafka stores the message in a **partition** within that topic
5. `MessageConsumer` (annotated with `@KafkaListener`) automatically picks up the message
6. Consumer logs it to the console

---

## 2. Docker Compose — Infrastructure

📄 **File:** `docker-compose.yml`

This file spins up two containers:

### Kafka Broker (KRaft Mode)

```yaml
kafka:
  image: bitnami/kafka:4.0
```

| Environment Variable | Purpose |
|---|---|
| `KAFKA_CFG_NODE_ID=1` | Unique ID of this Kafka node (broker + controller combined) |
| `KAFKA_CFG_PROCESS_ROLES=broker,controller` | This single node acts as **both** broker (handles messages) and controller (manages metadata). In production you'd separate these. |
| `KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=1@kafka:9094` | KRaft consensus — tells the controller where to find voters. Format: `nodeId@host:port`. Since we have 1 node, it votes for itself. |
| `KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER` | Name of the listener used for controller-to-controller communication |
| `KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9094` | **Two listeners**: Port `9092` for client apps (your Java app), Port `9094` for internal controller traffic |
| `KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092` | What Kafka tells clients to connect to. Since your app runs on the host machine (not inside Docker), this must be `localhost` |
| `KAFKA_KRAFT_CLUSTER_ID=MkU3OE...` | A fixed cluster UUID. In KRaft mode, every broker needs the same cluster ID. We hardcode it so restarts don't break. |
| `KAFKA_CFG_NUM_PARTITIONS=3` | Default partitions for auto-created topics (but we disabled auto-create) |
| `KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE=false` | **Important!** We don't want topics created accidentally. Our Spring app creates them explicitly via `KafkaTopicConfig`. |

### What is KRaft?

Before Kafka 4.0, Kafka needed **ZooKeeper** (a separate service) to manage metadata like "which broker owns which partition." KRaft (Kafka Raft) removes ZooKeeper entirely — Kafka manages its own metadata using the Raft consensus protocol. **One less thing to run!**

### Kafka UI

```yaml
kafka-ui:
  image: provectuslabs/kafka-ui:latest
  ports:
    - "8090:8080"
```

A visual dashboard at `http://localhost:8090` where you can:
- See all topics and their partitions
- Browse messages inside each topic
- View consumer groups and their lag (how far behind they are)

---

## 3. application.yml — Spring Kafka Defaults

📄 **File:** `src/main/resources/application.yml`

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
```

**`bootstrap-servers`** — The address of the Kafka broker. Your Spring app uses this to:
1. Discover the full cluster (even if you have 10 brokers, you just need 1 address to start)
2. Send messages (producer)
3. Receive messages (consumer)

```yaml
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
```

**Serializers** — Kafka stores everything as **bytes**. Your app sends Java objects. A serializer converts:
- `String "Hello"` → `bytes [72, 101, 108, 108, 111]`

This is the **default** serializer. The `KafkaConfig.java` class overrides this for JSON messages.

```yaml
    consumer:
      auto-offset-reset: earliest
```

**`auto-offset-reset: earliest`** — When a consumer starts for the first time (no committed offset), where should it start reading?
- `earliest` → Read ALL messages from the beginning (nothing is missed)
- `latest` → Only read NEW messages that arrive after the consumer started

---

## 4. KafkaTopicConfig — Topic & Partition Creation

📄 **File:** `config/KafkaTopicConfig.java`

### What This Class Does

When the Spring Boot app starts, it **automatically creates** Kafka topics if they don't already exist. Spring's `KafkaAdmin` (auto-configured) detects all `NewTopic` beans and creates them.

### Topic Breakdown

| Bean Method | Topic Name | Partitions | Replicas | Purpose |
|---|---|---|---|---|
| `greetingsTopic()` | `greetings` | 3 | 1 | Simple string messages. 3 partitions to demo round-robin and key-based routing. |
| `ordersTopic()` | `orders` | 3 | 1 | JSON order events. Key = customerId → same customer always lands in same partition. |
| `notificationsTopic()` | `notifications` | 2 | 1 | Demonstrates consumer groups — two groups both independently consume the same messages. |

### Key Code Explained

```java
@Bean
public NewTopic greetingsTopic() {
    return TopicBuilder.name("greetings")   // Topic name
            .partitions(3)                   // Split into 3 partitions
            .replicas(1)                     // 1 copy (we only have 1 broker)
            .build();
}
```

- **`TopicBuilder`** — Spring Kafka's fluent builder for `NewTopic` objects
- **`partitions(3)`** — The topic has 3 partitions (P0, P1, P2). Each partition is an ordered, immutable log.
- **`replicas(1)`** — How many copies of each partition exist across brokers. Since we have only 1 broker, this must be 1. In production, you'd use 3 for fault tolerance.

### Why Partitions Matter

```
Topic: "greetings" with 3 partitions

  Partition 0: [msg1] [msg4] [msg7] ...
  Partition 1: [msg2] [msg5] [msg8] ...
  Partition 2: [msg3] [msg6] [msg9] ...

Without key → messages are distributed round-robin (msg1→P0, msg2→P1, msg3→P2...)
With key    → hash(key) % numPartitions determines the partition
              e.g., hash("user-A") % 3 = 1  →  always goes to P1
```

---

## 5. KafkaConfig — Serialization / Deserialization Setup

📄 **File:** `config/KafkaConfig.java`

### What This Class Does

This is the **most important configuration class**. It tells Spring Kafka HOW to convert Java objects to/from bytes when sending/receiving messages.

### Why Do We Need This?

The `application.yml` sets default serializers for **String** messages. But when we want to send **JSON objects** (like `OrderEvent`), we need separate factories with `JsonSerializer`/`JsonDeserializer`.

### The Four Bean Groups

#### Group 1: String Producer

```java
@Bean
public ProducerFactory<String, String> producerFactory() {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    return new DefaultKafkaProducerFactory<>(config);
}

@Bean
public KafkaTemplate<String, String> kafkaTemplate() {
    return new KafkaTemplate<>(producerFactory());
}
```

| Component | Role |
|---|---|
| `ProducerFactory` | Creates Kafka producer instances with the given config |
| `ProducerConfig.BOOTSTRAP_SERVERS_CONFIG` | Where is the Kafka broker? |
| `KEY_SERIALIZER_CLASS_CONFIG` | How to convert the **key** to bytes → `StringSerializer` |
| `VALUE_SERIALIZER_CLASS_CONFIG` | How to convert the **value** to bytes → `StringSerializer` |
| `KafkaTemplate<String, String>` | Spring's high-level API to send messages. Generic types = `<KeyType, ValueType>` |

**Think of `KafkaTemplate` like `RestTemplate` but for Kafka instead of HTTP.**

#### Group 2: JSON Producer (OrderEvent)

```java
@Bean
public ProducerFactory<String, OrderEvent> orderProducerFactory() {
    ...
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(config);
}

@Bean
public KafkaTemplate<String, OrderEvent> orderKafkaTemplate() {
    return new KafkaTemplate<>(orderProducerFactory());
}
```

Same as above, but `VALUE_SERIALIZER = JsonSerializer`. This converts `OrderEvent` → JSON string → bytes.

```
OrderEvent { orderId="abc", product="iPhone" }
    ↓ JsonSerializer
{"orderId":"abc","product":"iPhone","quantity":2,"price":999.0,...}
    ↓ to bytes
[123, 34, 111, 114, 100, 101, 114, 73, 100, ...]
```

#### Group 3: JSON Consumer (OrderEvent)

```java
@Bean
public ConsumerFactory<String, OrderEvent> orderConsumerFactory() {
    ...
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.learn.kafka.model");
    config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderEvent.class.getName());
    return new DefaultKafkaConsumerFactory<>(config);
}
```

| Config | Purpose |
|---|---|
| `VALUE_DESERIALIZER_CLASS_CONFIG = JsonDeserializer` | Convert bytes → JSON → `OrderEvent` object |
| `TRUSTED_PACKAGES = "com.learn.kafka.model"` | **Security measure!** Only allow deserialization of classes from this package. Without this, a malicious message could instantiate arbitrary classes. |
| `VALUE_DEFAULT_TYPE = OrderEvent.class` | What Java class should the JSON be mapped to? |

#### Group 4: JSON Listener Container Factory

```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> orderKafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, OrderEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(orderConsumerFactory());
    return factory;
}
```

- **`ConcurrentKafkaListenerContainerFactory`** — Creates the container that runs `@KafkaListener` methods.
- The `orderConsumerFactory` provides the JSON deserializer.
- When a `@KafkaListener` specifies `containerFactory = "orderKafkaListenerContainerFactory"`, it uses THIS factory (and therefore JSON deserialization).
- String consumers use the default factory (auto-configured by Spring Boot).

### Visual Summary

```
┌──────────────────────── PRODUCER SIDE ────────────────────────┐
│                                                                │
│  KafkaTemplate<String, String>      → sends String messages    │
│      uses producerFactory()         → StringSerializer         │
│                                                                │
│  KafkaTemplate<String, OrderEvent>  → sends JSON messages      │
│      uses orderProducerFactory()    → JsonSerializer           │
│                                                                │
├──────────────────────── CONSUMER SIDE ────────────────────────┤
│                                                                │
│  Default factory (auto-configured)  → reads String messages    │
│      StringDeserializer (from yml)                             │
│                                                                │
│  orderKafkaListenerContainerFactory → reads JSON messages      │
│      uses orderConsumerFactory()    → JsonDeserializer         │
│      maps to OrderEvent.class                                  │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

## 6. MessageProducer — The Publisher

📄 **File:** `producer/MessageProducer.java`

### What This Class Does

Sends (publishes) messages to Kafka topics. It has **5 methods** demonstrating different send patterns.

### Dependencies Injected

```java
private final KafkaTemplate<String, String> kafkaTemplate;          // String messages
private final KafkaTemplate<String, OrderEvent> orderKafkaTemplate; // JSON messages
```

Both are created by `KafkaConfig.java` beans. Spring auto-wires them by type matching.

### Method 1: `sendGreeting(message)` — Fire-and-Forget with Callback

```java
CompletableFuture<SendResult<String, String>> future =
        kafkaTemplate.send("greetings", message);

future.whenComplete((result, ex) -> { ... });
```

- **`kafkaTemplate.send("greetings", message)`** — Sends to topic `greetings`. No key provided, so Kafka uses **round-robin** to pick a partition.
- **Returns `CompletableFuture`** — Sending is **asynchronous**! The method returns immediately.
- **`whenComplete`** — Callback that fires when Kafka acknowledges the message.
- **`result.getRecordMetadata()`** — Contains `topic`, `partition`, and `offset` where the message landed.

### Method 2: `sendGreetingWithKey(key, message)` — Key-Based Routing

```java
kafkaTemplate.send("greetings", key, message)
```

- Three-argument `send(topic, key, value)`.
- **The key determines the partition:** `hash(key) % numPartitions`.
- Same key → always same partition → **ordering guaranteed for that key**.
- Use case: all events for `user-123` must be processed in order.

### Method 3: `sendOrder(order)` — JSON Object

```java
orderKafkaTemplate.send("orders", order.getCustomerId(), order)
```

- Uses `orderKafkaTemplate` which has `JsonSerializer`.
- Key = `customerId` → all orders for the same customer go to the same partition.
- Value = `OrderEvent` object → serialized to JSON bytes automatically.

### Method 4: `sendNotification(message)` — Consumer Group Demo

```java
kafkaTemplate.send("notifications", message);
```

- Sends to `notifications` topic.
- Two independent consumer groups (`email-notification-group` and `sms-notification-group`) both receive every message.

### Method 5: `sendToPartition(message, partition)` — Explicit Partition

```java
kafkaTemplate.send("greetings", partition, null, message)
```

- Four-argument `send(topic, partition, key, value)`.
- **You explicitly choose the partition** (0, 1, or 2).
- Key is `null` since partition is already specified.
- Use case: rare, but useful for testing or custom routing logic.

---

## 7. MessageConsumer — The Subscriber

📄 **File:** `consumer/MessageConsumer.java`

### What This Class Does

Listens to Kafka topics and processes incoming messages. Spring Kafka manages the polling loop — you just write the handler method.

### How `@KafkaListener` Works Under the Hood

```
1. Spring sees @KafkaListener on a method
2. It creates a KafkaMessageListenerContainer
3. The container creates a Kafka Consumer and calls poll() in a loop
4. When messages arrive, it deserializes them and calls YOUR method
5. After your method returns, it commits the offset (auto-commit by default)
```

### Consumer 1: `consumeGreeting` — String Consumer

```java
@KafkaListener(
    topics = "greetings",
    groupId = "greetings-group"
)
public void consumeGreeting(ConsumerRecord<String, String> record) { ... }
```

| Attribute | Meaning |
|---|---|
| `topics = "greetings"` | Subscribe to the `greetings` topic |
| `groupId = "greetings-group"` | This consumer belongs to the `greetings-group` consumer group |
| `ConsumerRecord<K, V>` | Full record with metadata: topic, partition, offset, key, value, timestamp |

**Why `ConsumerRecord` and not just `String`?**
Using `ConsumerRecord` gives you access to metadata (which partition, what offset, the key, etc.). You could also write `public void consumeGreeting(String message)` if you only care about the value.

### Consumer 2: `consumeOrder` — JSON Consumer

```java
@KafkaListener(
    topics = "orders",
    groupId = "order-processing-group",
    containerFactory = "orderKafkaListenerContainerFactory"  // ← KEY!
)
public void consumeOrder(ConsumerRecord<String, OrderEvent> record) { ... }
```

- **`containerFactory = "orderKafkaListenerContainerFactory"`** — This is critical! It tells Spring to use our custom factory (from `KafkaConfig`) that knows how to deserialize JSON into `OrderEvent`.
- Without this, Spring would use the default `StringDeserializer` and crash with a deserialization error.

### Consumers 3 & 4: `consumeNotificationForEmail` + `consumeNotificationForSms`

```java
@KafkaListener(topics = "notifications", groupId = "email-notification-group")
public void consumeNotificationForEmail(...) { ... }

@KafkaListener(topics = "notifications", groupId = "sms-notification-group")
public void consumeNotificationForSms(...) { ... }
```

**This demonstrates the MOST IMPORTANT Kafka concept: Consumer Groups.**

```
                    Topic: "notifications" (2 partitions)
                    ┌────────────┐  ┌────────────┐
                    │ Partition 0 │  │ Partition 1 │
                    └──────┬─────┘  └──────┬─────┘
                           │               │
              ┌────────────┼───────────────┼────────────┐
              │            ▼               ▼            │
              │  ┌─────────────────────────────────┐   │
              │  │  Group: "email-notification"     │   │
              │  │  Consumer: consumeForEmail()     │   │
              │  │  → Gets ALL messages             │   │
              │  └─────────────────────────────────┘   │
              │                                         │
              │  ┌─────────────────────────────────┐   │
              │  │  Group: "sms-notification"       │   │
              │  │  Consumer: consumeForSms()       │   │
              │  │  → ALSO gets ALL messages        │   │
              │  └─────────────────────────────────┘   │
              └─────────────────────────────────────────┘

    RULE: Different groups = each gets a FULL copy of every message.
          Same group       = work is SPLIT across consumers in the group.
```

---

## 8. KafkaController — REST API Layer

📄 **File:** `controller/KafkaController.java`

### What This Class Does

Exposes REST endpoints so you can trigger Kafka operations from your browser/Postman/curl. It acts as a bridge between HTTP and Kafka.

### Endpoint Summary

| HTTP Method | Path | Calls | Kafka Concept Demonstrated |
|---|---|---|---|
| `POST` | `/api/kafka/greet?message=...` | `producer.sendGreeting()` | No-key message → round-robin partitioning |
| `POST` | `/api/kafka/greet-with-key?key=...&message=...` | `producer.sendGreetingWithKey()` | Key-based partition routing |
| `POST` | `/api/kafka/order?customer=...&product=...` | `producer.sendOrder()` | JSON serialization, key = customerId |
| `POST` | `/api/kafka/send-to-partition?partition=0&message=...` | `producer.sendToPartition()` | Explicit partition selection |
| `POST` | `/api/kafka/notify?message=...` | `producer.sendNotification()` | Consumer groups (both email + SMS get it) |

### Design Pattern

```
Controller (HTTP layer)  →  Producer (Kafka layer)  →  Kafka Broker
     ↑ returns JSON              ↑ async send
     │ immediately               │ (doesn't wait)
```

The controller returns a response **immediately** after calling the producer. It doesn't wait for Kafka to acknowledge the message. The producer handles success/failure logging in its `whenComplete` callback.

---

## 9. OrderEvent — The Message Model

📄 **File:** `model/OrderEvent.java`

```java
@Data @NoArgsConstructor @AllArgsConstructor
public class OrderEvent {
    private String orderId;
    private String customerId;
    private String product;
    private int quantity;
    private double price;
    private String status;
    private String timestamp;
}
```

- **`@Data`** — Lombok generates getters, setters, `toString()`, `equals()`, `hashCode()`
- **`@NoArgsConstructor`** — Required by Jackson (JSON library) for deserialization
- **`@AllArgsConstructor`** — Convenience constructor
- **`OrderEvent.create()`** — Factory method that auto-generates `orderId`, sets status to `PLACED`, and timestamps it

### Serialization Path

```
Producer side:                           Consumer side:
OrderEvent object                        bytes from Kafka
    ↓ JsonSerializer                         ↓ JsonDeserializer
    ↓ (Jackson ObjectMapper)                 ↓ (Jackson ObjectMapper)
JSON string                              OrderEvent object
    ↓ UTF-8 encoding                         ↑ UTF-8 decoding
bytes sent to Kafka                      JSON string
```

---

## 10. KafkaProperties — Config-Driven Architecture

📄 **File:** `config/KafkaProperties.java`

### The Problem (Before)

In the original version, topic names were **hardcoded everywhere**:

```java
// In KafkaTopicConfig.java — one @Bean per topic
@Bean
public NewTopic greetingsTopic() {
    return TopicBuilder.name("greetings").partitions(3).replicas(1).build();
}
@Bean
public NewTopic ordersTopic() { ... }
@Bean
public NewTopic notificationsTopic() { ... }

// In MessageProducer.java
kafkaTemplate.send("greetings", message);       // hardcoded
orderKafkaTemplate.send("orders", key, order);  // hardcoded

// In MessageConsumer.java
@KafkaListener(topics = "greetings")             // hardcoded
@KafkaListener(topics = "orders")                // hardcoded
```

**Problems with this:**
- Adding a new topic = edit 3+ Java files + recompile
- Topic names scattered across classes (easy to typo)
- Can't change topic names per environment (dev/staging/prod)
- Not production-ready

### The Solution (After) — Config-Driven Topics

All topic config lives in **one place**: `application.yml`

```yaml
app:
  kafka:
    topics:
      greetings:                    # ← config key (used in Java)
        name: greetings             # ← actual Kafka topic name
        partitions: 3
        replicas: 1
      orders:
        name: orders
        partitions: 3
        replicas: 1
      notifications:
        name: notifications
        partitions: 2
        replicas: 1
      # payments:                   # ← uncomment to add a new topic!
      #   name: payments
      #   partitions: 4
      #   replicas: 1
```

### How It Works — The 3-Layer Binding

```
┌─────────────────────────────────────────────────────────────────┐
│                     application.yml                             │
│  app.kafka.topics.greetings.name = "greetings"                 │
│  app.kafka.topics.greetings.partitions = 3                     │
└──────────────────────────┬──────────────────────────────────────┘
                           │ @ConfigurationProperties(prefix="app.kafka")
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                    KafkaProperties.java                         │
│  Map<String, TopicDef> topics                                  │
│    "greetings" → TopicDef { name="greetings", partitions=3 }   │
│    "orders"    → TopicDef { name="orders", partitions=3 }      │
└──────────┬────────────────────────────┬─────────────────────────┘
           │                            │
           ▼                            ▼
┌─────────────────────┐    ┌──────────────────────────────┐
│ KafkaTopicConfig    │    │ MessageProducer               │
│ loops over topics   │    │ kafkaProperties.topicName(key)│
│ → creates NewTopic  │    │ → gets actual topic name      │
│   beans dynamically │    │                               │
└─────────────────────┘    └──────────────────────────────┘
```

### KafkaProperties.java — The Binding Class

```java
@Data
@Configuration
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaProperties {

    private Map<String, TopicDef> topics = new HashMap<>();

    @Data
    public static class TopicDef {
        private String name;
        private int partitions = 1;
        private int replicas = 1;
    }

    public String topicName(String key) {
        return topics.get(key).getName();
    }
}
```

| Annotation | What It Does |
|---|---|
| `@ConfigurationProperties(prefix = "app.kafka")` | Binds all YAML properties under `app.kafka.*` into this class |
| `Map<String, TopicDef> topics` | YAML map `app.kafka.topics` → Java Map. Key = "greetings", Value = TopicDef object |
| `topicName("greetings")` | Convenience method → returns `"greetings"` (the actual Kafka topic name) |

### KafkaTopicConfig.java — Dynamic Topic Creation

```java
@Bean
public KafkaAdmin.NewTopics kafkaTopics() {
    List<NewTopic> topicList = new ArrayList<>();
    kafkaProperties.getTopics().forEach((key, def) -> {
        topicList.add(TopicBuilder.name(def.getName())
                .partitions(def.getPartitions())
                .replicas(def.getReplicas())
                .build());
    });
    return new KafkaAdmin.NewTopics(topicList.toArray(new NewTopic[0]));
}
```

**Before:** 3 separate `@Bean` methods (one per topic). Adding a topic = new Java method.
**After:** 1 method loops over the YAML map. Adding a topic = 3 lines in YAML.

### MessageProducer.java — Topic Names from Config

```java
private final KafkaProperties kafkaProperties;

public void sendGreeting(String message) {
    String topic = kafkaProperties.topicName("greetings");  // ← from YAML
    kafkaTemplate.send(topic, message);
}
```

No hardcoded `"greetings"` string. If you rename the topic in YAML, the producer auto-updates.

### MessageConsumer.java — SpEL Placeholders in @KafkaListener

```java
@KafkaListener(
    topics = "${app.kafka.topics.greetings.name}",    // ← SpEL placeholder!
    groupId = "greetings-group"
)
public void consumeGreeting(ConsumerRecord<String, String> record) { ... }
```

`@KafkaListener` requires **compile-time constants** for its attributes. You can't inject a bean method. But Spring supports **`${property.path}`** placeholders in annotations — Spring resolves them from `application.yml` at startup.

### How to Add a New Topic (Zero Java Code!)

**Step 1:** Add to `application.yml`:
```yaml
app:
  kafka:
    topics:
      payments:             # ← new!
        name: payments
        partitions: 4
        replicas: 1
```

**Step 2:** Done! The topic is auto-created on next app restart.

**Step 3 (optional):** If you want a consumer for it, add one listener method:
```java
@KafkaListener(
    topics = "${app.kafka.topics.payments.name}",
    groupId = "payments-group"
)
public void consumePayment(ConsumerRecord<String, String> record) {
    log.info("💰 Payment: {}", record.value());
}
```

**Step 4 (optional):** To produce to it, use the generic method:
```java
producer.sendToTopic("payments", "pay-key-1", "Payment of $99");
```

### Per-Environment Override

In production, you can have different topic names per environment:

```yaml
# application-dev.yml
app:
  kafka:
    topics:
      orders:
        name: dev-orders
        partitions: 1

# application-prod.yml
app:
  kafka:
    topics:
      orders:
        name: prod-orders
        partitions: 12
        replicas: 3
```

Same Java code, different behavior per Spring profile!

---

## 11. Key Kafka Concepts Explained

### 🔵 Topic
A **named stream** of messages. Like a table in a database, but append-only. Messages are never updated or deleted (until retention expires).

### 🟢 Partition
A topic is split into partitions. Each partition is an **ordered, immutable sequence** of messages. Partitions enable:
- **Parallelism** — multiple consumers read different partitions simultaneously
- **Ordering** — messages within ONE partition are strictly ordered
- **Scalability** — more partitions = more throughput

### 🟡 Offset
Each message in a partition gets a unique, sequential **offset** (0, 1, 2, 3...). Consumers track their position by remembering the last offset they read.

```
Partition 0:  [offset 0] [offset 1] [offset 2] [offset 3] [offset 4]
                                                     ▲
                                              Consumer is here
                                              (committed offset = 3)
```

### 🔴 Consumer Group
A group of consumers sharing the work of reading a topic:
- Each partition is assigned to **exactly one** consumer in the group
- If a consumer dies, its partitions are reassigned to others (**rebalancing**)
- If you have **more consumers than partitions**, some consumers sit idle

```
3 partitions, 2 consumers in same group:
  Consumer A → P0, P1
  Consumer B → P2

3 partitions, 3 consumers in same group:
  Consumer A → P0
  Consumer B → P1
  Consumer C → P2

3 partitions, 4 consumers in same group:
  Consumer A → P0
  Consumer B → P1
  Consumer C → P2
  Consumer D → IDLE (no partition to read!)
```

### 🟣 Key
Optional. When set, `hash(key) % numPartitions` determines the target partition.
- **With key**: ordering guaranteed for that key (e.g., all orders for `customer-123` in order)
- **Without key**: round-robin distribution (best throughput, no ordering guarantee across partitions)

### 🟠 Broker
A Kafka server that stores and serves messages. Our Docker setup has 1 broker. Production systems typically have 3+ brokers.

### ⚪ Replica
A copy of a partition on a different broker. If broker 1 dies, broker 2 has the replica and takes over. We use `replicas(1)` because we only have 1 broker locally.

---

## 12. Message Lifecycle — End to End

Here's exactly what happens when you call `POST /api/kafka/order?customer=c1&product=iPhone&qty=2&price=999`:

```
Step 1: HTTP Request
   └─ Spring MVC dispatches to KafkaController.sendOrder()

Step 2: Build the Event
   └─ OrderEvent.create("c1", "iPhone", 2, 999.0)
   └─ Sets orderId = random UUID, status = "PLACED", timestamp = now

Step 3: Call Producer
   └─ producer.sendOrder(order)
   └─ Uses orderKafkaTemplate (JsonSerializer)

Step 4: Serialize
   └─ Key "c1" → StringSerializer → bytes
   └─ OrderEvent → JsonSerializer → {"orderId":"abc",...} → bytes

Step 5: Determine Partition
   └─ hash("c1") % 3 = (let's say) 1
   └─ Message goes to partition 1 of "orders" topic

Step 6: Send to Broker
   └─ KafkaTemplate sends ProducerRecord to Kafka broker at localhost:9092
   └─ Broker appends message to partition 1 at the next offset

Step 7: Acknowledge
   └─ Broker sends acknowledgment back
   └─ whenComplete() callback fires: "✅ Order sent! Partition=1, Offset=5"
   │
   │  💡 Why Offset=5? Because 5 messages were already in partition 1
   │     before this one. Offsets are just a per-partition counter:
   │       Offset 0 → first msg ever in this partition
   │       Offset 1 → second msg
   │       ...
   │       Offset 5 → YOUR msg (6th message in this partition)
   │     If this were the first message ever, offset would be 0.

Step 8: Consumer Polls
   └─ MessageConsumer's @KafkaListener continuously polls the broker
   └─ Finds new message at partition 1, offset 5

Step 9: Deserialize
   └─ bytes → JsonDeserializer → OrderEvent object
   └─ (uses orderKafkaListenerContainerFactory → orderConsumerFactory)

Step 10: Process
   └─ consumeOrder() method is called with the ConsumerRecord
   └─ Logs: "📥 [ORDER CONSUMER] New order received! Order ID: abc"

Step 11: Commit Offset
   └─ Consumer commits offset 5 (auto-commit enabled by default)
   └─ Next poll will start from offset 6
```

---

## 13. Common Interview Questions

**Q: What happens if a consumer crashes mid-processing?**
A: The offset hasn't been committed yet, so when the consumer restarts (or another consumer in the group takes over), it re-reads the message. This is called **at-least-once delivery**.

**Q: Can you get exactly-once delivery?**
A: Yes, using Kafka transactions + idempotent producers. Not covered here but it's a config toggle.

**Q: What if you have 3 partitions and 5 consumers in the same group?**
A: 3 consumers get 1 partition each. 2 consumers sit **idle**. Max parallelism = number of partitions.

**Q: Why use keys?**
A: To guarantee **ordering** for related messages. E.g., all events for `order-123` (created → paid → shipped) must be processed in that order. Same key = same partition = ordering preserved.

**Q: Why not just 1 partition?**
A: Single partition = single consumer = no parallelism. You'd bottleneck at the speed of one consumer. More partitions = more consumers can work in parallel.

**Q: What is `auto-offset-reset: earliest` vs `latest`?**
A: When a consumer group reads a topic for the **first time** (no prior offsets):
- `earliest` → read ALL existing messages from the start
- `latest` → skip everything, only read new messages going forward

**Q: Difference between `KafkaTemplate` and `@KafkaListener`?**
A: `KafkaTemplate` = **produce** (send) messages. `@KafkaListener` = **consume** (receive) messages. They are the two halves of the pub-sub pattern.

**Q: What does `containerFactory` do in `@KafkaListener`?**
A: It tells Spring which `ConsumerFactory` to use for deserialization. The default factory uses `StringDeserializer`. Custom factories (like `orderKafkaListenerContainerFactory`) use `JsonDeserializer` for specific object types.

---

## File Quick Reference

| File | Layer | Purpose |
|---|---|---|
| `docker-compose.yml` | Infrastructure | Runs Kafka broker + Kafka UI in Docker |
| `application.yml` | Configuration | Bootstrap server, serializers, **all topic definitions** |
| `KafkaProperties.java` | Configuration | Binds YAML `app.kafka.topics.*` → Java Map (the config-driven bridge) |
| `KafkaTopicConfig.java` | Configuration | Dynamically creates all topics by looping over KafkaProperties |
| `KafkaConfig.java` | Configuration | Configures producer factories (String + JSON) and consumer factories (JSON) |
| `MessageProducer.java` | Service | Sends messages to Kafka topics using `KafkaTemplate` |
| `MessageConsumer.java` | Service | Receives messages from Kafka topics using `@KafkaListener` |
| `KafkaController.java` | API | REST endpoints to trigger produce operations |
| `OrderEvent.java` | Model | POJO representing an order, serialized to/from JSON |
| `KafkaPlaygroundApp.java` | Bootstrap | Spring Boot main class |
