package com.blinkit.phase1.auth.jwt;

import com.blinkit.phase1.auth.UserRole;

import java.util.UUID;

/**
 * Represents an authenticated user extracted from JWT.
 * Used as the principal in Spring Security context.
 */
public record AuthenticatedUser(
    UUID id,
    String email,
    UserRole role
) {
    /**
     * Check if user has admin role.
     */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
