package com.blinkit.phase1.order;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single line item in an order — captures the product snapshot
 * (ID, name, price) at the time the order was placed.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemEntity {

    @Id
    private UUID id;

    /** The order this item belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    /** Reference to the product catalogue entry. */
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    /** Snapshot of the product name at order time (won't change if product is renamed). */
    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    /** Number of units ordered. */
    @Column(nullable = false)
    private int quantity;

    /** Price per unit at the time of order (snapshot — not affected by future price changes). */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
}
