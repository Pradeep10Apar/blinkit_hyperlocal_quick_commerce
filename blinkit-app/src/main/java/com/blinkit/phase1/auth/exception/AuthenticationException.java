package com.blinkit.phase1.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when authentication fails.
 * Returns 401 Unauthorized.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class AuthenticationException extends RuntimeException {
    
    public AuthenticationException(String message) {
        super(message);
    }

    public static AuthenticationException invalidCredentials() {
        return new AuthenticationException("Invalid email or password");
    }

    public static AuthenticationException accountLocked() {
        return new AuthenticationException("Account is locked due to too many failed attempts. Please try again later.");
    }

    public static AuthenticationException accountDisabled() {
        return new AuthenticationException("Account is disabled");
    }

    public static AuthenticationException invalidToken() {
        return new AuthenticationException("Invalid or expired token");
    }
}
