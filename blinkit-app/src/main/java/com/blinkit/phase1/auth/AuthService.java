package com.blinkit.phase1.auth;

import com.blinkit.phase1.auth.dto.*;
import com.blinkit.phase1.auth.exception.AuthenticationException;
import com.blinkit.phase1.auth.exception.EmailAlreadyExistsException;
import com.blinkit.phase1.auth.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Authentication service for Email/Password flow.
 * 
 * Handles:
 * - User registration with password hashing
 * - User login with password verification
 * - Token refresh with rotation
 * - Logout (token revocation)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // Security settings
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 15;

    // ==================== Registration ====================

    /**
     * Register a new user with email and password.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.email());

        // 1. Check if email already exists
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        // 2. Validate password strength (basic validation, can be enhanced)
        validatePasswordStrength(request.password());

        // 3. Create user with hashed password
        UserEntity user = UserEntity.builder()
            .email(request.email().toLowerCase().trim())
            .passwordHash(passwordEncoder.encode(request.password()))
            .name(request.name())
            .role(UserRole.USER)
            .emailVerified(false)  // Email verification can be added later
            .build();

        user = userRepository.save(user);
        log.info("User registered successfully: {}", user.getId());

        // 4. Generate tokens
        return generateAuthResponse(user);
    }

    // ==================== Login ====================

    /**
     * Authenticate user with email and password.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.debug("Login attempt for email: {}", request.email());

        // 1. Find user by email
        UserEntity user = userRepository.findByEmailIgnoreCase(request.email())
            .orElseThrow(AuthenticationException::invalidCredentials);

        // 2. Check if account is locked
        if (user.isLocked()) {
            log.warn("Login attempt for locked account: {}", request.email());
            throw AuthenticationException.accountLocked();
        }

        // 3. Check if account is active
        if (!user.isActive()) {
            throw AuthenticationException.accountDisabled();
        }

        // 4. Verify password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // Record failed attempt
            user.recordFailedLogin(MAX_FAILED_ATTEMPTS, LOCKOUT_DURATION_MINUTES);
            userRepository.save(user);
            
            log.warn("Failed login attempt for: {}. Attempts: {}", 
                request.email(), user.getFailedLoginAttempts());
            
            throw AuthenticationException.invalidCredentials();
        }

        // 5. Successful login - reset failed attempts
        user.recordSuccessfulLogin();
        userRepository.save(user);

        log.info("User logged in successfully: {}", user.getId());

        // 6. Generate tokens
        return generateAuthResponse(user);
    }

    // ==================== Token Refresh ====================

    /**
     * Refresh access token using a valid refresh token.
     * Implements token rotation for security.
     */
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();

        // 1. Validate the refresh token (JWT validation)
        if (!jwtUtil.isValidRefreshToken(refreshToken)) {
            throw AuthenticationException.invalidToken();
        }

        // 2. Check if token exists in DB and is not revoked
        String tokenHash = jwtUtil.hashToken(refreshToken);
        RefreshTokenEntity storedToken = refreshTokenRepository.findValidByTokenHash(tokenHash)
            .orElseThrow(AuthenticationException::invalidToken);

        // 3. Get the user
        UserEntity user = storedToken.getUser();

        // 4. Check if user is still active
        if (!user.isActive()) {
            throw AuthenticationException.accountDisabled();
        }

        // 5. Revoke the old refresh token (rotation)
        storedToken.revoke();
        refreshTokenRepository.save(storedToken);

        log.info("Token refreshed for user: {}", user.getId());

        // 6. Generate new tokens
        return generateAuthResponse(user);
    }

    // ==================== Logout ====================

    /**
     * Logout user by revoking the refresh token.
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            String tokenHash = jwtUtil.hashToken(refreshToken);
            refreshTokenRepository.revokeByTokenHash(tokenHash);
            log.debug("Refresh token revoked");
        }
    }

    /**
     * Logout from all devices by revoking all refresh tokens.
     */
    @Transactional
    public void logoutAllDevices(java.util.UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("All refresh tokens revoked for user: {}", userId);
    }

    // ==================== Get Current User ====================

    /**
     * Get user by ID (for /auth/me endpoint).
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(java.util.UUID userId) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new AuthenticationException("User not found"));
        
        return mapToUserResponse(user);
    }

    // ==================== Helper Methods ====================

    /**
     * Generate auth response with access and refresh tokens.
     */
    private AuthResponse generateAuthResponse(UserEntity user) {
        // Generate tokens
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        // Store refresh token hash in DB
        saveRefreshToken(user, refreshToken);

        // Calculate expiry in seconds
        long expiresIn = 15 * 60; // 15 minutes in seconds

        return new AuthResponse(
            accessToken,
            refreshToken,
            expiresIn,
            mapToUserResponse(user)
        );
    }

    /**
     * Save refresh token hash to database.
     */
    private void saveRefreshToken(UserEntity user, String refreshToken) {
        RefreshTokenEntity tokenEntity = RefreshTokenEntity.builder()
            .user(user)
            .tokenHash(jwtUtil.hashToken(refreshToken))
            .expiresAt(Instant.now().plus(jwtUtil.getRefreshTokenExpirationDays(), ChronoUnit.DAYS))
            .build();

        refreshTokenRepository.save(tokenEntity);
    }

    /**
     * Map user entity to response DTO.
     */
    private UserResponse mapToUserResponse(UserEntity user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getRole(),
            user.isEmailVerified()
        );
    }

    /**
     * Validate password strength.
     * Basic validation - can be enhanced with more rules.
     */
    private void validatePasswordStrength(String password) {
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        
        // Check for at least one digit
        if (!password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Password must contain at least one digit");
        }
        
        // Check for at least one letter
        if (!password.matches(".*[a-zA-Z].*")) {
            throw new IllegalArgumentException("Password must contain at least one letter");
        }
    }
}
