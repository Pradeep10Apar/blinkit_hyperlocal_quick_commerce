package com.blinkit.phase1.order.dto;

import com.blinkit.phase1.order.OrderItemResponse;
import com.blinkit.phase1.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Detailed order response for GET /api/orders/{id} and order listing.
 */
public record OrderDetailResponse(
        String orderId,
        String userId,
        OrderStatus status,
        BigDecimal totalAmount,
        String deliveryAddress,
        String paymentMethod,
        String paymentStatus,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt,
        Instant deliveredAt
) {
}
