package com.blinkit.phase1.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an invalid order status transition is attempted.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidOrderStatusTransitionException extends RuntimeException {
    
    public InvalidOrderStatusTransitionException(String currentStatus, String newStatus) {
        super(String.format("Invalid status transition from %s to %s", currentStatus, newStatus));
    }
}
