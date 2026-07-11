package com.blinkit.phase1.auth.dto;

/**
 * Simple message response for operations that don't return data.
 */
public record MessageResponse(
    String message
) {
    public static MessageResponse of(String message) {
        return new MessageResponse(message);
    }
}
