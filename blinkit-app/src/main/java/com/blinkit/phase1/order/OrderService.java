package com.blinkit.phase1.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blinkit.inventory.api.model.ReserveItem;
import com.blinkit.inventory.api.model.ReserveStockResponse;
import com.blinkit.phase1.cart.CartService;
import com.blinkit.phase1.cart.dto.CartItemResponse;
import com.blinkit.phase1.cart.dto.CartResponse;
import com.blinkit.phase1.inventory.InventoryClient;
import com.blinkit.phase1.order.dto.OrderDetailResponse;
import com.blinkit.phase1.order.dto.OrderSummaryResponse;
import com.blinkit.phase1.order.exception.InvalidOrderStatusTransitionException;
import com.blinkit.phase1.order.exception.OrderNotFoundException;
import com.blinkit.phase1.order.outbox.OrderOutboxEvent;
import com.blinkit.phase1.order.outbox.OrderOutboxRepository;
import com.blinkit.phase1.product.ProductEntity;
import com.blinkit.phase1.product.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;
    private final OrderOutboxRepository orderOutboxRepository;
    private final ObjectMapper objectMapper;
    private final InventoryClient inventoryClient;

    /**
     * Places an order from the contents of the given cart.
     *
     * 1. Fetches the current cart snapshot.
     * 2. Validates the cart is not empty.
     * 3. Validates every product exists in the products table and is active.
     * 4. Creates an {@link OrderEntity} with line items using the latest DB price.
     * 5. Persists the order.
     * 6. Clears the cart so it cannot be placed again.
     * 7. Returns a summary {@link OrderResponse} to the caller / UI.
     */
    @Transactional
    public OrderResponse placeOrder(String cartId) {

        // 1. Fetch cart
        CartResponse cart = cartService.getCart(cartId);

        if (cart.items() == null || cart.items().isEmpty()) {
            throw new IllegalStateException("Cannot place an order with an empty cart");
        }

        // 2. Fetch all referenced products from DB in one query
        List<UUID> productIds = cart.items().stream()
                .map(CartItemResponse::productId)
                .toList();

        Map<UUID, ProductEntity> productsById = productRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        // 3. Reserve stock from Inventory Service (SYNCHRONOUS call)
        List<ReserveItem> reserveItems = cart.items().stream()
                .map(item -> InventoryClient.createReserveItem(item.productId(), item.quantity()))
                .toList();

        ReserveStockResponse inventoryResponse = inventoryClient.reserveStock(reserveItems);
        
        // If stock reservation failed, return failure response with details
        if (!inventoryResponse.getSuccess()) {
            List<String> failureReasons = inventoryResponse.getFailedItems().stream()
                    .map(failed -> String.format("Product %s: %s (requested=%d, available=%d)",
                            failed.getProductId(),
                            failed.getReason(),
                            failed.getRequestedQuantity(),
                            failed.getAvailableQuantity()))
                    .toList();
            
            log.warn("Stock reservation failed for cart {}: {}", cartId, failureReasons);
            throw new InsufficientStockException("Cannot place order - insufficient stock", failureReasons);
        }
        
        log.info("Stock reserved successfully for {} items", inventoryResponse.getReservedItems().size());

        // 4. Build order (stock is now reserved)

        Instant now = Instant.now();
        OrderEntity order = OrderEntity.builder()
                .id(UUID.randomUUID())
                .userId(cartId)                       // using cartId as user identifier
                .status(OrderStatus.PLACED)
                .totalAmount(BigDecimal.ZERO)          // will be computed below
                .paymentStatus("PENDING")
                .createdAt(now)
                .updatedAt(now)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItemEntity> orderItems = new ArrayList<>();

        for (CartItemResponse cartItem : cart.items()) {
            ProductEntity product = productsById.get(cartItem.productId());

            
            
                
            BigDecimal unitPrice = product.getPrice();  // latest price from DB
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.quantity()));
            totalAmount = totalAmount.add(lineTotal);

            OrderItemEntity orderItem = OrderItemEntity.builder()
                    .id(UUID.randomUUID())
                    .order(order)
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(cartItem.quantity())
                    .unitPrice(unitPrice)
                    .build();

            orderItems.add(orderItem);
        }

        order.setTotalAmount(totalAmount);
        order.setItems(orderItems);

        // 6. Persist order
        orderRepository.save(order);

        // 7. Write outbox event (same transaction — guaranteed atomicity)
        saveOrderOutboxEvent(order);

        // 8. Clear the cart so a duplicate order cannot be placed
        cartService.clearCart(cartId);

        // 9. Return response for UI
        log.info("Order {} placed successfully with total ₹{}", order.getId(), totalAmount);
        
        return new OrderResponse(
                order.getId().toString(),
                order.getStatus(),
                "Order placed successfully! Total: ₹" + totalAmount,
                List.of()  // No skipped items - we use all-or-nothing reservation
        );
    }

    /**
     * Saves an ORDER_PLACED outbox event in the same DB transaction as the order.
     * The scheduled {@link com.blinkit.phase1.order.outbox.OrderOutboxPublisher}
     * will pick it up and relay it to Kafka topic "order-events".
     */
    private void saveOrderOutboxEvent(OrderEntity order) {
        try {
            Map<String, Object> payload = Map.of(
                    "orderId", order.getId().toString(),
                    "userId", order.getUserId(),
                    "status", order.getStatus().name(),
                    "totalAmount", order.getTotalAmount(),
                    "itemCount", order.getItems().size(),
                    "placedAt", order.getCreatedAt().toString()
            );

            Instant now = Instant.now();
            OrderOutboxEvent outbox = OrderOutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateId(order.getId())
                    .eventType("ORDER_PLACED")
                    .payload(objectMapper.writeValueAsString(payload))
                    .status("NEW")
                    .attempts(0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            orderOutboxRepository.save(outbox);
            log.info("Saved ORDER_PLACED outbox event for order {}", order.getId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize order outbox payload", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NEW METHODS: Get Order, List Orders, Update Status
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get a single order by ID with full details.
     */
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderById(UUID orderId) {
        OrderEntity order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));
        
        return mapToDetailResponse(order);
    }

    /**
     * Get all orders for a user (paginated).
     */
    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getOrdersByUserId(String userId, int page, int size) {
        Page<OrderEntity> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
        
        return orders.stream()
                .map(this::mapToSummaryResponse)
                .toList();
    }

    /**
     * Update order status (admin operation).
     * Validates the status transition is valid.
     */
    @Transactional
    public OrderDetailResponse updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        OrderEntity order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));
        
        validateStatusTransition(order.getStatus(), newStatus);
        
        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());
        
        // Set deliveredAt timestamp when order is delivered
        if (newStatus == OrderStatus.DELIVERED) {
            order.setDeliveredAt(Instant.now());
        }
        
        orderRepository.save(order);
        
        // Publish status change event
        saveStatusChangeOutboxEvent(order, newStatus);
        
        log.info("Order {} status updated to {}", orderId, newStatus);
        return mapToDetailResponse(order);
    }

    /**
     * Cancel an order (only if not yet out for delivery).
     */
    @Transactional
    public OrderDetailResponse cancelOrder(UUID orderId) {
        OrderEntity order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));
        
        // Can only cancel if not already delivered or out for delivery
        if (order.getStatus() == OrderStatus.OUT_FOR_DELIVERY || 
            order.getStatus() == OrderStatus.DELIVERED) {
            throw new InvalidOrderStatusTransitionException(
                    order.getStatus().name(), OrderStatus.CANCELLED.name());
        }
        
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new InvalidOrderStatusTransitionException("CANCELLED", "CANCELLED");
        }
        
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        
        // TODO: Release reserved stock back to inventory
        // inventoryClient.releaseStock(order.getItems());
        
        saveStatusChangeOutboxEvent(order, OrderStatus.CANCELLED);
        
        log.info("Order {} cancelled", orderId);
        return mapToDetailResponse(order);
    }

    /**
     * Get orders by status (admin dashboard).
     */
    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(this::mapToSummaryResponse)
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════════════════════

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        // Define valid transitions
        boolean valid = switch (current) {
            case PLACED -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.PACKED || next == OrderStatus.CANCELLED;
            case PACKED -> next == OrderStatus.OUT_FOR_DELIVERY || next == OrderStatus.CANCELLED;
            case OUT_FOR_DELIVERY -> next == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false; // Terminal states
        };
        
        if (!valid) {
            throw new InvalidOrderStatusTransitionException(current.name(), next.name());
        }
    }

    private void saveStatusChangeOutboxEvent(OrderEntity order, OrderStatus newStatus) {
        try {
            Map<String, Object> payload = Map.of(
                    "orderId", order.getId().toString(),
                    "userId", order.getUserId(),
                    "previousStatus", order.getStatus().name(),
                    "newStatus", newStatus.name(),
                    "updatedAt", Instant.now().toString()
            );

            Instant now = Instant.now();
            OrderOutboxEvent outbox = OrderOutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateId(order.getId())
                    .eventType("ORDER_STATUS_CHANGED")
                    .payload(objectMapper.writeValueAsString(payload))
                    .status("NEW")
                    .attempts(0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            orderOutboxRepository.save(outbox);
            log.info("Saved ORDER_STATUS_CHANGED outbox event for order {}", order.getId());
        } catch (Exception e) {
            log.error("Failed to save status change outbox event", e);
        }
    }

    private OrderDetailResponse mapToDetailResponse(OrderEntity order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice().doubleValue()
                ))
                .toList();
        
        return new OrderDetailResponse(
                order.getId().toString(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getDeliveryAddress(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getDeliveredAt()
        );
    }

    private OrderSummaryResponse mapToSummaryResponse(OrderEntity order) {
        return new OrderSummaryResponse(
                order.getId().toString(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getItems().size(),
                order.getPaymentStatus(),
                order.getCreatedAt()
        );
    }
}
