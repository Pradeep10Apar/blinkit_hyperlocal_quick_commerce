package com.learn.kafka.consumer;

import com.learn.kafka.model.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║                KAFKA CONSUMER (Subscriber)                  ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║  Topic names use ${...} SpEL → resolved from application.yml║
 * ║                                                             ║
 * ║  Example:                                                   ║
 * ║    topics = "${app.kafka.topics.greetings.name}"            ║
 * ║    → Spring reads app.kafka.topics.greetings.name from YAML ║
 * ║    → Resolves to "greetings"                                ║
 * ║                                                             ║
 * ║  If you rename a topic in YAML, consumers auto-update!      ║
 * ║                                                             ║
 * ║  Consumer Group Rules:                                      ║
 * ║  • Same groupId   = work is SPLIT (each msg → 1 consumer)  ║
 * ║  • Different groupId = each group gets ALL messages         ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
@Slf4j
@Service
public class MessageConsumer {

    /**
     * CONSUMER 1: Listens to "greetings" topic (name from config)
     */
    @KafkaListener(
            topics = "${app.kafka.topics.greetings.name}",
            groupId = "greetings-group"
    )
    public void consumeGreeting(ConsumerRecord<String, String> record) {
        log.info("──────────────────────────────────────────────");
        log.info("📥 [GREETINGS CONSUMER] Received message!");
        log.info("   Topic     : {}", record.topic());
        log.info("   Partition : {}", record.partition());
        log.info("   Offset    : {}", record.offset());
        log.info("   Key       : {}", record.key());
        log.info("   Value     : {}", record.value());
        log.info("   Timestamp : {}", record.timestamp());
        log.info("──────────────────────────────────────────────");
    }

    /**
     * CONSUMER 2: Listens to "orders" topic (JSON messages, name from config)
     */
    @KafkaListener(
            topics = "${app.kafka.topics.orders.name}",
            groupId = "order-processing-group",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    public void consumeOrder(ConsumerRecord<String, OrderEvent> record) {
        OrderEvent order = record.value();
        log.info("══════════════════════════════════════════════");
        log.info("📥 [ORDER CONSUMER] New order received!");
        log.info("   Order ID   : {}", order.getOrderId());
        log.info("   Customer   : {}", order.getCustomerId());
        log.info("   Product    : {}", order.getProduct());
        log.info("   Quantity   : {}", order.getQuantity());
        log.info("   Price      : ${}", order.getPrice());
        log.info("   Status     : {}", order.getStatus());
        log.info("   Partition  : {}", record.partition());
        log.info("   Offset     : {}", record.offset());
        log.info("══════════════════════════════════════════════");
    }

    /**
     * CONSUMER 3: Email notification consumer (name from config)
     * Different groupId → gets its OWN copy of ALL messages
     */
    @KafkaListener(
            topics = "${app.kafka.topics.notifications.name}",
            groupId = "email-notification-group"
    )
    public void consumeNotificationForEmail(ConsumerRecord<String, String> record) {
        log.info("📧 [EMAIL SERVICE] Notification: {}", record.value());
    }

    /**
     * CONSUMER 4: SMS notification consumer (name from config)
     * SAME topic, DIFFERENT group → also gets ALL messages independently
     */
    @KafkaListener(
            topics = "${app.kafka.topics.notifications.name}",
            groupId = "sms-notification-group"
    )
    public void consumeNotificationForSms(ConsumerRecord<String, String> record) {
        log.info("📱 [SMS SERVICE] Notification: {}", record.value());
    }
}
