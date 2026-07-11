package com.blinkit.phase1.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RefreshToken entity.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    /**
     * Find a valid (not revoked, not expired) refresh token by its hash.
     */
    @Query("""
        SELECT rt FROM RefreshTokenEntity rt
        WHERE rt.tokenHash = :tokenHash
        AND rt.revoked = false
        AND rt.expiresAt > CURRENT_TIMESTAMP
        """)
    Optional<RefreshTokenEntity> findValidByTokenHash(String tokenHash);

    /**
     * Revoke all refresh tokens for a user (logout from all devices).
     */
    @Modifying
    @Query("UPDATE RefreshTokenEntity rt SET rt.revoked = true WHERE rt.user.id = :userId")
    void revokeAllByUserId(UUID userId);

    /**
     * Revoke a specific token by its hash.
     */
    @Modifying
    @Query("UPDATE RefreshTokenEntity rt SET rt.revoked = true WHERE rt.tokenHash = :tokenHash")
    void revokeByTokenHash(String tokenHash);

    /**
     * Delete expired tokens (cleanup job).
     */
    @Modifying
    @Query("DELETE FROM RefreshTokenEntity rt WHERE rt.expiresAt < CURRENT_TIMESTAMP")
    int deleteExpiredTokens();
}
