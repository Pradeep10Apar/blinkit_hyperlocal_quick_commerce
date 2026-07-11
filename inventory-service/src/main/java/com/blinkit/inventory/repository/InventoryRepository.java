package com.blinkit.inventory.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.blinkit.inventory.entity.Inventory;


public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    List<Inventory> findAllByProductIdIn(List<UUID> productIds);

    Optional<Inventory> findByProductId(UUID productId);

    boolean existsByProductId(UUID productId);
}
