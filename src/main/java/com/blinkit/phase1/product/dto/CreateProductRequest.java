package com.blinkit.phase1.product.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 120) String brand,
        @Size(max = 120) String category,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull Boolean active,
        @Size(max = 1024) String imageUrl
) {}
