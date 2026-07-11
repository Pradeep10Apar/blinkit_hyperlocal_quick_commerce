package com.blinkit.phase1.order.event;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka event payload published when an order is successfully placed.
 * Consumers (notifications, inventory, analytics, etc.) can subscribe
 * to the "order-events" topic to react independently.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPlacedEvent {

    private String orderId;
    private String userId;
    private String status;
    private BigDecimal totalAmount;
    private int itemCount;
    private Instant placedAt;
}
