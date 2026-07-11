package com.blinkit.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Fallback controller for Circuit Breaker.
 * When a circuit is OPEN, requests are routed here instead of failing.
 * Provides graceful degradation with user-friendly error messages.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    /**
     * Fallback for blinkit-app (Products, Cart, Orders)
     * Called when blinkitAppCircuitBreaker is OPEN
     */
    @GetMapping("/blinkit-app")
    public ResponseEntity<Map<String, Object>> blinkitAppFallbackGet() {
        return buildFallbackResponse(
                "Main service temporarily unavailable",
                "We're experiencing high traffic. Please try again in a moment.",
                30
        );
    }

    @PostMapping("/blinkit-app")
    public ResponseEntity<Map<String, Object>> blinkitAppFallbackPost() {
        return buildFallbackResponse(
                "Main service temporarily unavailable",
                "Unable to process your request right now. Please try again shortly.",
                30
        );
    }

    /**
     * Fallback for inventory-service
     * Called when inventoryCircuitBreaker is OPEN
     */
    @GetMapping("/inventory")
    public ResponseEntity<Map<String, Object>> inventoryFallbackGet() {
        return buildFallbackResponse(
                "Inventory service unavailable",
                "Unable to check stock availability. Your order may still be placed.",
                60
        );
    }

    @PostMapping("/inventory")
    public ResponseEntity<Map<String, Object>> inventoryFallbackPost() {
        return buildFallbackResponse(
                "Inventory service unavailable",
                "Unable to reserve stock at this time. Please try again later.",
                60
        );
    }

    /**
     * Build a consistent fallback response
     */
    private ResponseEntity<Map<String, Object>> buildFallbackResponse(
            String error, 
            String message, 
            int retryAfterSeconds) {
        
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", String.valueOf(retryAfterSeconds))
                .body(Map.of(
                        "error", error,
                        "message", message,
                        "fallback", true,
                        "retryAfterSeconds", retryAfterSeconds,
                        "timestamp", Instant.now().toString()
                ));
    }
}
