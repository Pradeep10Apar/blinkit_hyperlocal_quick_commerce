package com.blinkit.phase1.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String brand,
        String category,
        BigDecimal price,
        boolean active,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt
) {


    public ProductResponse(UUID id, String name, String brand, String category,
                           BigDecimal price, boolean active, String imageUrl,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.price = price;
        this.active = active;
        this.imageUrl = imageUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
