package com.blinkit.phase1.inventory;

/**
 * Exception thrown when inventory service communication fails.
 */
public class InventoryServiceException extends RuntimeException {

    public InventoryServiceException(String message) {
        super(message);
    }

    public InventoryServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
