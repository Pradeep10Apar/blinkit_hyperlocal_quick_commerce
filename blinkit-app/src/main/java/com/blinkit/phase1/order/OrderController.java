package com.blinkit.phase1.order;

import com.blinkit.phase1.order.dto.OrderDetailResponse;
import com.blinkit.phase1.order.dto.OrderSummaryResponse;
import com.blinkit.phase1.order.dto.UpdateOrderStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // ═══════════════════════════════════════════════════════════════════════════
    // Place Order
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/orders?cartId={cartId}
     *
     * Places a new order from the items currently in the given cart.
     * The cart is cleared after the order is persisted.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@RequestParam String cartId) {
        OrderResponse response = orderService.placeOrder(cartId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Get Order(s)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/orders/{orderId}
     *
     * Retrieves a single order by its ID with full details including line items.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrderById(@PathVariable UUID orderId) {
        OrderDetailResponse response = orderService.getOrderById(orderId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/orders?userId={userId}&page=0&size=20
     *
     * Retrieves all orders for a specific user with pagination.
     */
    @GetMapping
    public ResponseEntity<List<OrderSummaryResponse>> getOrdersByUser(
            @RequestParam String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<OrderSummaryResponse> orders = orderService.getOrdersByUserId(userId, page, size);
        return ResponseEntity.ok(orders);
    }

    /**
     * GET /api/orders/status/{status}
     *
     * Retrieves all orders with a specific status (admin dashboard).
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OrderSummaryResponse>> getOrdersByStatus(
            @PathVariable OrderStatus status
    ) {
        List<OrderSummaryResponse> orders = orderService.getOrdersByStatus(status);
        return ResponseEntity.ok(orders);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Update Order Status
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * PATCH /api/orders/{orderId}/status
     *
     * Updates the status of an order. Only valid transitions are allowed.
     * Requires ADMIN role.
     */
    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderDetailResponse> updateOrderStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        OrderDetailResponse response = orderService.updateOrderStatus(orderId, request.status());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/orders/{orderId}/cancel
     *
     * Cancels an order. Can only be done before the order is out for delivery.
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderDetailResponse> cancelOrder(@PathVariable UUID orderId) {
        OrderDetailResponse response = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(response);
    }
}
