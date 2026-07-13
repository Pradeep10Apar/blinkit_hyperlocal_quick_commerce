package com.blinkit.phase1.auth;

import com.blinkit.phase1.auth.dto.*;
import com.blinkit.phase1.auth.jwt.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller for Email/Password flow.
 * 
 * Endpoints:
 * - POST /auth/register - Register new user
 * - POST /auth/login - Login with email/password
 * - POST /auth/refresh - Refresh access token
 * - POST /auth/logout - Logout (revoke refresh token)
 * - GET  /auth/me - Get current user info
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new user with email and password.
     * 
     * 
     * 
     * Response: AuthResponse with tokens and user info
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request for email: {}", request.email());
        return authService.register(request);
    }

    /**
     * Login with email and password.
     * 
     
     * 
     * Response: AuthResponse with tokens and user info
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        log.debug("Login request for email: {}", request.email());
        return authService.login(request);
    }

    /**
     * Refresh access token using refresh token.
     * 
     
     * 
     * Response: New AuthResponse with fresh tokens
     */
    @PostMapping("/refresh")
    public AuthResponse refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.debug("Token refresh request");
        return authService.refreshToken(request);
    }

    /**
     * Logout - revoke the refresh token.
     * 
     * Request:
     * {
     *   "refreshToken": "eyJhbGci..."  (optional)
     * }
     */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
        @RequestBody(required = false) RefreshTokenRequest request
    ) {
        String refreshToken = request != null ? request.refreshToken() : null;
        authService.logout(refreshToken);
        return ResponseEntity.ok(MessageResponse.of("Logged out successfully"));
    }

    /**
     * Logout from all devices - revoke all refresh tokens.
     * Requires authentication.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<MessageResponse> logoutAllDevices(
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(MessageResponse.of("Not authenticated"));
        }
        
        authService.logoutAllDevices(user.id());
        return ResponseEntity.ok(MessageResponse.of("Logged out from all devices"));
    }

    /**
     * Get current authenticated user info.
     * Requires valid access token in Authorization header.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
        @AuthenticationPrincipal AuthenticatedUser user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        UserResponse response = authService.getCurrentUser(user.id());
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint for auth service.
     */
    @GetMapping("/health")
    public ResponseEntity<MessageResponse> health() {
        return ResponseEntity.ok(MessageResponse.of("Auth service is healthy"));
    }
}
