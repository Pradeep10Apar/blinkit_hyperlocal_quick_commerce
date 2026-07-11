package com.learn.kafka.producer;

import com.learn.kafka.config.KafkaProperties;
import com.learn.kafka.model.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║                  KAFKA PRODUCER (Publisher)                 ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  A Producer SENDS messages to a Kafka topic.               ║
 * ║                                                             ║
 * ║  Topic names come from KafkaProperties (application.yml)    ║
 * ║  → No hardcoded topic strings anywhere!                     ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTemplate<String, OrderEvent> orderKafkaTemplate;
    private final KafkaProperties kafkaProperties;  // ← injected config

    /**
     * DEMO 1: Send a simple string message (no key → round-robin partitions)
     */
    public void sendGreeting(String message) {
        String topic = kafkaProperties.topicName("greetings");
        log.info("📤 Sending greeting to '{}': {}", topic, message);

        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(topic, message);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("✅ Greeting sent! Topic={}, Partition={}, Offset={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("❌ Failed to send greeting", ex);
            }
        });
    }

    /**
     * DEMO 2: Send a string message WITH a key
     * → Same key ALWAYS goes to the same partition
     */
    public void sendGreetingWithKey(String key, String message) {
        String topic = kafkaProperties.topicName("greetings");
        log.info("📤 Sending greeting with key='{}' to '{}': {}", key, topic, message);

        kafkaTemplate.send(topic, key, message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("✅ Sent! Key={}, Partition={}, Offset={}",
                                key,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("❌ Failed!", ex);
                    }
                });
    }

    /**
     * DEMO 3: Send a JSON object (OrderEvent)
     * → Key = customerId (all orders for same customer go to same partition)
     */
    public void sendOrder(OrderEvent order) {
        String topic = kafkaProperties.topicName("orders");
        log.info("📤 Sending order to '{}': {} for customer: {}",
                topic, order.getOrderId(), order.getCustomerId());

        orderKafkaTemplate.send(topic, order.getCustomerId(), order)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("✅ Order sent! Partition={}, Offset={}",
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("❌ Failed to send order", ex);
                    }
                });
    }

    /**
     * DEMO 4: Send notification (two consumer groups will BOTH receive it)
     */
    public void sendNotification(String message) {
        String topic = kafkaProperties.topicName("notifications");
        log.info("📤 Sending notification to '{}': {}", topic, message);
        kafkaTemplate.send(topic, message);
    }

    /**
     * DEMO 5: Send to a SPECIFIC partition
     */
    public void sendToPartition(String message, int partition) {
        String topic = kafkaProperties.topicName("greetings");
        log.info("📤 Sending to '{}' partition {}: {}", topic, partition, message);

        kafkaTemplate.send(topic, partition, null, message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("✅ Sent to partition {}!", result.getRecordMetadata().partition());
                    } else {
                        log.error("❌ Failed!", ex);
                    }
                });
    }

    /**
     * GENERIC: Send a string message to ANY topic by its config key.
     * Usage: sendToTopic("payments", "some-key", "message")
     */
    public void sendToTopic(String topicKey, String key, String message) {
        String topic = kafkaProperties.topicName(topicKey);
        log.info("📤 Sending to '{}' (key='{}'): {}", topic, key, message);
        kafkaTemplate.send(topic, key, message);
    }
}
