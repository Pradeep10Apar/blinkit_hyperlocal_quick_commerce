package com.blinkit.phase1.auth.dto;

/**
 * Response DTO for successful authentication.
 * Contains both tokens and user information.
 */
public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,  // Access token expiration in seconds
    UserResponse user
) {
    /**
     * Convenience constructor with default token type.
     */
    public AuthResponse(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
        this(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
