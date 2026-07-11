package com.blinkit.phase1.auth;

/**
 * User roles for authorization.
 * Stored as STRING in database for readability and safety.
 */
public enum UserRole {
    USER,       // Regular customer
    ADMIN       // Admin with elevated privileges
}
