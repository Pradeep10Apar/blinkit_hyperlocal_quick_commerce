package com.blinkit.phase1.order;

import java.util.List;

/**
 * Exception thrown when order cannot be placed due to insufficient stock.
 */
public class InsufficientStockException extends RuntimeException {

    private final List<String> failureReasons;

    public InsufficientStockException(String message, List<String> failureReasons) {
        super(message);
        this.failureReasons = failureReasons;
    }

    public List<String> getFailureReasons() {
        return failureReasons;
    }
}
