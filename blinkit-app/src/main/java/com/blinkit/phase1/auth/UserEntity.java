package com.blinkit.phase1.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * User entity supporting both OTP and Email/Password authentication.
 * 
 * Design decisions:
 * - Unified table for both auth methods (phone OR email required)
 * - BCrypt for password hashing (handled in service layer)
 * - Account lockout after failed attempts (security)
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ==================== Common Fields ====================
    
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    // ==================== OTP Auth Fields (Future) ====================
    
    @Column(unique = true, length = 15)
    private String phone;

    @Column(name = "phone_verified")
    @Builder.Default
    private boolean phoneVerified = false;

    // ==================== Email/Password Auth Fields ====================
    
    @Column(unique = true)
    private String email;

    @Column(name = "email_verified")
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "failed_login_attempts")
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    // ==================== Lifecycle Callbacks ====================

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ==================== Helper Methods ====================

    /**
     * Check if account is currently locked due to failed attempts.
     */
    public boolean isLocked() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    /**
     * Increment failed login attempts and lock if threshold exceeded.
     * @param maxAttempts Maximum allowed attempts before lockout
     * @param lockDurationMinutes How long to lock the account
     */
    public void recordFailedLogin(int maxAttempts, int lockDurationMinutes) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = Instant.now().plusSeconds(lockDurationMinutes * 60L);
        }
    }

    /**
     * Reset failed attempts after successful login.
     */
    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
    }
}
