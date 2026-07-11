package com.blinkit.phase1.auth.jwt;

import com.blinkit.phase1.auth.UserEntity;
import com.blinkit.phase1.auth.UserRole;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * JWT utility class for generating and validating tokens.
 * 
 * Token types:
 * - ACCESS: Short-lived (15 min), carries user info, used for API auth
 * - REFRESH: Long-lived (7 days), used only to get new access token
 */
@Component
@Slf4j
public class JwtUtil {

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtUtil(JwtProperties properties) {
        this.properties = properties;
        // Generate SecretKey from the secret string
        this.secretKey = Keys.hmacShaKeyFor(
            properties.secret().getBytes(StandardCharsets.UTF_8)
        );
        log.info("JWT utility initialized with issuer: {}", properties.issuer());
    }

    // ==================== Token Generation ====================

    /**
     * Generate an access token for a user.
     * Contains user ID, email, and role for authorization decisions.
     */
    public String generateAccessToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenExpirationMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
            .subject(user.getId().toString())
            .issuer(properties.issuer())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            // Custom claims
            .claim("type", "ACCESS")
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())
            .claim("name", user.getName())
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact();
    }

    /**
     * Generate a refresh token for a user.
     * Minimal claims - just enough to identify user and token.
     */
    public String generateRefreshToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.refreshTokenExpirationDays(), ChronoUnit.DAYS);

        return Jwts.builder()
            .subject(user.getId().toString())
            .issuer(properties.issuer())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .id(UUID.randomUUID().toString()) // Unique ID for revocation tracking
            .claim("type", "REFRESH")
            .signWith(secretKey, Jwts.SIG.HS256)
            .compact();
    }

    // ==================== Token Validation ====================

    /**
     * Validate and parse a JWT token.
     * @return Claims if valid, throws exception if invalid
     */
    public Claims validateAndGetClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .requireIssuer(properties.issuer())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Validate token and check if it's an access token.
     */
    public boolean isValidAccessToken(String token) {
        try {
            Claims claims = validateAndGetClaims(token);
            return "ACCESS".equals(claims.get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid access token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate token and check if it's a refresh token.
     */
    public boolean isValidRefreshToken(String token) {
        try {
            Claims claims = validateAndGetClaims(token);
            return "REFRESH".equals(claims.get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid refresh token: {}", e.getMessage());
            return false;
        }
    }

    // ==================== Claim Extraction ====================

    /**
     * Extract user ID from token.
     */
    public UUID extractUserId(String token) {
        Claims claims = validateAndGetClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Extract email from token (access token only).
     */
    public String extractEmail(String token) {
        Claims claims = validateAndGetClaims(token);
        return claims.get("email", String.class);
    }

    /**
     * Extract role from token (access token only).
     */
    public UserRole extractRole(String token) {
        Claims claims = validateAndGetClaims(token);
        String roleName = claims.get("role", String.class);
        return UserRole.valueOf(roleName);
    }

    /**
     * Extract token type.
     */
    public String extractTokenType(String token) {
        Claims claims = validateAndGetClaims(token);
        return claims.get("type", String.class);
    }

    /**
     * Get refresh token expiration in days (for storage calculation).
     */
    public int getRefreshTokenExpirationDays() {
        return properties.refreshTokenExpirationDays();
    }

    /**
     * Hash a token for storage (we don't store plain tokens).
     * Using simple SHA-256 for now.
     */
    public String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
