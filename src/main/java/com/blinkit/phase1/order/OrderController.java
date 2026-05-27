package com.blinkit.phase1.order;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /api/orders?cartId={cartId}
     *
     * Places a new order from the items currently in the given cart.
     * The cart is cleared after the order is persisted.
     */
    @GetMapping
    public ResponseEntity<OrderResponse> placeOrder(@RequestParam String cartId) {
        OrderResponse response = orderService.placeOrder(cartId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
