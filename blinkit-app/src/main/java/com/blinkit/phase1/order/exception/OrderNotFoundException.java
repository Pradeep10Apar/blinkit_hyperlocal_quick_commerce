package com.blinkit.phase1.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an order is not found by ID.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class OrderNotFoundException extends RuntimeException {
    
    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
    }
}
