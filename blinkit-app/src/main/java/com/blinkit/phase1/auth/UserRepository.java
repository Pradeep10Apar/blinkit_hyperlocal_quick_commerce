package com.blinkit.phase1.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Find user by email (case-insensitive).
     */
    Optional<UserEntity> findByEmailIgnoreCase(String email);

    /**
     * Find user by phone number.
     */
    Optional<UserEntity> findByPhone(String phone);

    /**
     * Check if email already exists.
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Check if phone already exists.
     */
    boolean existsByPhone(String phone);
}
