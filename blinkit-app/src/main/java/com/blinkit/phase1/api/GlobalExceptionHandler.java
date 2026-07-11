package com.blinkit.phase1.api;

import com.blinkit.phase1.auth.exception.AuthenticationException;
import com.blinkit.phase1.auth.exception.EmailAlreadyExistsException;
import com.blinkit.phase1.cart.CartItemNotFoundException;
import com.blinkit.phase1.order.InsufficientStockException;
import com.blinkit.phase1.product.ProductNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== Auth Exceptions ====================

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError(Instant.now(), 401, "UNAUTHORIZED", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleEmailExists(EmailAlreadyExistsException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(Instant.now(), 409, "CONFLICT", ex.getMessage(), req.getRequestURI()));
    }

    // ==================== Existing Exceptions ====================

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ProductNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(Instant.now(), 404, "NOT_FOUND", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<ApiError> handleCartItemNotFound(CartItemNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(Instant.now(), 404, "NOT_FOUND", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(Instant.now(), 400, "BAD_REQUEST", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<InsufficientStockError> handleInsufficientStock(InsufficientStockException ex, HttpServletRequest req) {
        System.out.println(">>> InsufficientStockException handler called!");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new InsufficientStockError(
                        Instant.now(), 
                        409, 
                        "INSUFFICIENT_STOCK", 
                        ex.getMessage(), 
                        req.getRequestURI(),
                        ex.getFailureReasons()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(this::fmt)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(Instant.now(), 400, "BAD_REQUEST", msg, req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        System.out.println(">>> CATCH-ALL handler called! Exception type: " + ex.getClass().getName());
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(Instant.now(), 500, "INTERNAL_SERVER_ERROR", ex.getMessage(), req.getRequestURI()));
    }

    private String fmt(FieldError fe) {
        return fe.getField() + " " + fe.getDefaultMessage();
    }

    /**
     * Response DTO for insufficient stock errors - includes failed items details.
     */
    public record InsufficientStockError(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path,
            List<String> failedItems
    ) {}
}
