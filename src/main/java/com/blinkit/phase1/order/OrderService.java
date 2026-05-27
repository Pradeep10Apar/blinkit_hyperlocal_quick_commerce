package com.blinkit.phase1.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blinkit.phase1.cart.CartService;
import com.blinkit.phase1.cart.dto.CartItemResponse;
import com.blinkit.phase1.cart.dto.CartResponse;
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

        // 3. Build order, skipping unavailable products gracefully
        List<CartItemResponse> skippedItems = new ArrayList<>();

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

            if (product == null) {
                skippedItems.add(cartItem);
                continue;
            }
            if (!product.isActive()) {
                skippedItems.add(cartItem);
                continue;
            }
                
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

        if (orderItems.isEmpty()) {
            throw new IllegalStateException(
                    "None of the items in the cart are available. Skipped: " + skippedItems);
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
        String message = skippedItems.isEmpty()
                ? "Order placed successfully! Total: ₹" + totalAmount
                : "Order placed with some items skipped. Total: ₹" + totalAmount;

        return new OrderResponse(
                order.getId().toString(),
                order.getStatus(),
                message,
                skippedItems
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
}
