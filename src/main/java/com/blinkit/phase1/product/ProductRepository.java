package com.blinkit.phase1.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {
    List<ProductEntity> findByImageUrlIsNull();
}
