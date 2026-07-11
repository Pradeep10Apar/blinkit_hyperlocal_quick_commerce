package com.learn.kafka.controller;

import com.learn.kafka.model.OrderEvent;
import com.learn.kafka.producer.MessageProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API to trigger Kafka operations.
 * Hit these endpoints using curl, Postman, or your browser.
 */
@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
public class KafkaController {

    private final MessageProducer producer;

    // ════════════════════════════════════════════════════════════
    //  1. SIMPLE STRING MESSAGES
    // ════════════════════════════════════════════════════════════

    /**
     * Send a simple greeting (no key → round-robin across partitions)
     *
     * curl -X POST "http://localhost:8080/api/kafka/greet?message=Hello Kafka!"
     */
    @PostMapping("/greet")
    public ResponseEntity<Map<String, String>> sendGreeting(@RequestParam String message) {
        producer.sendGreeting(message);
        return ResponseEntity.ok(Map.of(
                "status", "sent",
                "topic", "greetings",
                "message", message
        ));
    }

    /**
     * Send a greeting WITH a key (same key → same partition always)
     *
     * curl -X POST "http://localhost:8080/api/kafka/greet-with-key?key=user-1&message=Hello"
     * curl -X POST "http://localhost:8080/api/kafka/greet-with-key?key=user-1&message=World"
     * → Both go to SAME partition because key is "user-1"
     */
    @PostMapping("/greet-with-key")
    public ResponseEntity<Map<String, String>> sendGreetingWithKey(
            @RequestParam String key,
            @RequestParam String message) {
        producer.sendGreetingWithKey(key, message);
        return ResponseEntity.ok(Map.of(
                "status", "sent",
                "topic", "greetings",
                "key", key,
                "message", message
        ));
    }

    // ════════════════════════════════════════════════════════════
    //  2. JSON OBJECT MESSAGES (OrderEvent)
    // ════════════════════════════════════════════════════════════

    /**
     * Send an order event as JSON
     *
     * curl -X POST "http://localhost:8080/api/kafka/order?customer=cust-123&product=iPhone&qty=2&price=999.99"
     */
    @PostMapping("/order")
    public ResponseEntity<Map<String, Object>> sendOrder(
            @RequestParam String customer,
            @RequestParam String product,
            @RequestParam(defaultValue = "1") int qty,
            @RequestParam(defaultValue = "0.0") double price) {
        OrderEvent order = OrderEvent.create(customer, product, qty, price);
        producer.sendOrder(order);
        return ResponseEntity.ok(Map.of(
                "status", "sent",
                "topic", "orders",
                "order", order
        ));
    }

    // ════════════════════════════════════════════════════════════
    //  3. SEND TO SPECIFIC PARTITION
    // ════════════════════════════════════════════════════════════

    /**
     * Send to a specific partition (0, 1, or 2)
     *
     * curl -X POST "http://localhost:8080/api/kafka/send-to-partition?partition=0&message=Goes to P0"
     * curl -X POST "http://localhost:8080/api/kafka/send-to-partition?partition=1&message=Goes to P1"
     */
    @PostMapping("/send-to-partition")
    public ResponseEntity<Map<String, Object>> sendToPartition(
            @RequestParam int partition,
            @RequestParam String message) {
        producer.sendToPartition(message, partition);
        return ResponseEntity.ok(Map.of(
                "status", "sent",
                "topic", "greetings",
                "partition", partition,
                "message", message
        ));
    }

    // ════════════════════════════════════════════════════════════
    //  4. NOTIFICATIONS (demonstrates consumer groups)
    // ════════════════════════════════════════════════════════════

    /**
     * Send a notification → consumed by BOTH email and SMS consumers
     * (because they have DIFFERENT groupIds)
     *
     * curl -X POST "http://localhost:8080/api/kafka/notify?message=Your order shipped!"
     */
    @PostMapping("/notify")
    public ResponseEntity<Map<String, String>> sendNotification(@RequestParam String message) {
        producer.sendNotification(message);
        return ResponseEntity.ok(Map.of(
                "status", "sent",
                "topic", "notifications",
                "message", message,
                "consumers", "email-group + sms-group (both receive it!)"
        ));
    }
}
