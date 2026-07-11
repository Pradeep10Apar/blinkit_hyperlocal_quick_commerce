package com.blinkit.phase1.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCartItemQuantityRequest(
        @NotNull @Min(1) Integer quantity
) {
}
