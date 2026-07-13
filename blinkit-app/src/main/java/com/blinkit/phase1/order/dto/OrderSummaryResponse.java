package com.blinkit.phase1.order.dto;

import com.blinkit.phase1.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Summary response for order listing (without full item details).
 */
public record OrderSummaryResponse(
        String orderId,
        OrderStatus status,
        BigDecimal totalAmount,
        int itemCount,
        String paymentStatus,
        Instant createdAt
) {
}
