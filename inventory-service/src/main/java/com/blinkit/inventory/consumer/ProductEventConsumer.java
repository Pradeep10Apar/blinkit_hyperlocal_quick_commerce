package com.blinkit.inventory.consumer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.blinkit.inventory.entity.Inventory;
import com.blinkit.inventory.repository.InventoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumes product-events from Kafka in batches and creates stock entries.
 * This ensures inventory is always in sync with products.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventConsumer {

    private final InventoryRepository inventoryRepository;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_QUANTITY = 1;
    private static final String DEFAULT_WAREHOUSE = "DEFAULT";

    @KafkaListener(topics = "product-events", groupId = "inventory-service", batch = "true")
    public void onProductEvents(List<String> payloads) {
        log.info("Received batch of {} product events", payloads.size());
        
        Instant now = Instant.now();
        List<UUID> productIds = new ArrayList<>();

        // Parse all product IDs from the batch
        for (String payload : payloads) {
            try {
                JsonNode productJson = objectMapper.readTree(payload);
                UUID productId = UUID.fromString(productJson.get("id").asText());
                productIds.add(productId);
            } catch (Exception e) {
                log.error("Failed to parse product event: {}", e.getMessage());
            }
        }

        if (productIds.isEmpty()) {
            log.warn("No valid product IDs found in batch");
            return;
        }

        // Find which products already have stock entries (single DB call)
        Set<UUID> existingProductIds = inventoryRepository.findAllByProductIdIn(productIds)
                .stream()
                .map(Inventory::getProductId)
                .collect(Collectors.toSet());

        log.info("Found {} existing stock entries out of {} products", existingProductIds.size(), productIds.size());

        // Create stock entries only for new products
        List<Inventory> stocks = productIds.stream()
                .filter(id -> !existingProductIds.contains(id))
                .map(productId -> Inventory.builder()
                        .id(UUID.randomUUID())
                        .productId(productId)
                        .quantity(DEFAULT_QUANTITY)
                        .reserved(0)
                        .warehouse(DEFAULT_WAREHOUSE)
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
                .toList();

        if (!stocks.isEmpty()) {
            inventoryRepository.saveAll(stocks);
            log.info("Created {} new stock entries in batch", stocks.size());
        } else {
            log.info("No new stock entries to create (all products already have stock)");
        }
    }
}
