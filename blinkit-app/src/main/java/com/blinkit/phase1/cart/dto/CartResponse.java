package com.blinkit.phase1.cart.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CartResponse(
        String cartId,
        List<CartItemResponse> items,
        int totalQuantity,
        BigDecimal subtotal,
        Instant updatedAt
) {
}
