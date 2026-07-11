package com.blinkit.phase1.order;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEntity {

    @Id
    private UUID id;

    /** Identifies who placed the order (cart ID or user ID). */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** Current status of the order. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private OrderStatus status = OrderStatus.PLACED;

    /** Snapshot of the total at the time the order was placed. */
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** Delivery address provided by the customer. */
    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    /** e.g. UPI, CARD, COD */
    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    /** e.g. PENDING, PAID, FAILED, REFUNDED */
    @Column(name = "payment_status", length = 30)
    @Builder.Default
    private String paymentStatus = "PENDING";

    /** Line items — each captures product ID, quantity & price at order time. */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItemEntity> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;
}

