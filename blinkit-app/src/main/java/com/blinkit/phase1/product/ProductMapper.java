package com.blinkit.phase1.product;

import com.blinkit.phase1.product.dto.ProductResponse;

public class ProductMapper {
    private ProductMapper() {}

    public static ProductResponse toResponse(ProductEntity e) {
        return new ProductResponse(
                e.getId(),
                e.getName(),
                e.getBrand(),
                e.getCategory(),
                e.getPrice(),
                e.isActive(),
                e.getImageUrl(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
