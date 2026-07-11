package com.learn.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A sample event that gets sent as JSON through Kafka.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    private String orderId;
    private String customerId;
    private String product;
    private int quantity;
    private double price;
    private String status;       // PLACED, CONFIRMED, SHIPPED, DELIVERED
    private String timestamp;

    /** Factory method to quickly create a new order event */
    public static OrderEvent create(String customerId, String product, int qty, double price) {
        OrderEvent event = new OrderEvent();
        event.setOrderId(UUID.randomUUID().toString().substring(0, 8));
        event.setCustomerId(customerId);
        event.setProduct(product);
        event.setQuantity(qty);
        event.setPrice(price);
        event.setStatus("PLACED");
        event.setTimestamp(LocalDateTime.now().toString());
        return event;
    }
}
