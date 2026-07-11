package com.blinkit.phase1.order;

import java.util.List;

import com.blinkit.phase1.cart.dto.CartItemResponse;

public record OrderResponse(
        String orderId,
        OrderStatus status,
        String message,
        List<CartItemResponse> skippedItems
) {

}
