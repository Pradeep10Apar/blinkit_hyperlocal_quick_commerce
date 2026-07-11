package com.blinkit.phase1.order;

/**
 * Lifecycle stages of a Blinkit order.
 */
public enum OrderStatus {
    PLACED,
    CONFIRMED,
    PACKED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED
}
