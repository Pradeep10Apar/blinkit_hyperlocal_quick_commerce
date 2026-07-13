package com.blinkit.phase1.order.dto;

import com.blinkit.phase1.order.OrderStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating order status.
 * Used by admins to move orders through the lifecycle.
 */
public record UpdateOrderStatusRequest(
        @NotNull(message = "Status is required")
        OrderStatus status
) {
}
