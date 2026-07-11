package com.blinkit.phase1.auth.dto;

import com.blinkit.phase1.auth.UserRole;

import java.util.UUID;

/**
 * Response DTO for user information.
 */
public record UserResponse(
    UUID id,
    String email,
    String name,
    UserRole role,
    boolean emailVerified
) {}
