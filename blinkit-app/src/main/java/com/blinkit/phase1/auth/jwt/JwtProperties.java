package com.blinkit.phase1.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration properties.
 * Loaded from application.yml under 'app.jwt' prefix.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    /**
     * Secret key for signing JWTs (minimum 256 bits / 32 chars for HS256).
     * In production, use environment variable: APP_JWT_SECRET
     */
    String secret,
    
    /**
     * Access token expiration in minutes (default: 15).
     */
    int accessTokenExpirationMinutes,
    
    /**
     * Refresh token expiration in days (default: 7).
     */
    int refreshTokenExpirationDays,
    
    /**
     * Token issuer (your app name).
     */
    String issuer
) {
    /**
     * Default constructor with sensible defaults.
     */
    public JwtProperties {
        if (accessTokenExpirationMinutes <= 0) {
            accessTokenExpirationMinutes = 15;
        }
        if (refreshTokenExpirationDays <= 0) {
            refreshTokenExpirationDays = 7;
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "blinkit-app";
        }
    }
}
