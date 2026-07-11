package com.blinkit.phase1.product;

import com.blinkit.phase1.elastic.ElasticIndexService;
import com.blinkit.phase1.product.dto.CreateProductRequest;
import com.blinkit.phase1.product.outbox.ProductOutboxEvent;
import com.blinkit.phase1.product.outbox.ProductOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repo;
    private final ElasticIndexService elasticIndexService;
    private final ObjectMapper objectMapper;
    private final ProductOutboxRepository outboxRepository;

    @Transactional
    public ProductEntity create(CreateProductRequest req) {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        ProductEntity entity = ProductEntity.builder()
                .id(id)
                .name(req.name())
                .brand(req.brand())
                .category(req.category())
                .price(req.price())
                .active(Boolean.TRUE.equals(req.active()))
                .imageUrl(req.imageUrl())
                .createdAt(now)
                .updatedAt(now)
                .build();

        ProductEntity saved = repo.save(entity);

        // Prepare outbox event payload
        String payload = null;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "id", id.toString(),
                    "name", entity.getName(),
                    "brand", entity.getBrand(),
                    "category", entity.getCategory(),
                    "price", entity.getPrice(),
                    "active", entity.isActive(),
                    "imageUrl", entity.getImageUrl() != null ? entity.getImageUrl() : "",
                    "createdAt", entity.getCreatedAt().toString(),
                    "updatedAt", entity.getUpdatedAt().toString()
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        ProductOutboxEvent outboxEvent = ProductOutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(id)
                .eventType("PRODUCT_UPSERTED")
                .payload(payload)
                .status("NEW")
                .attempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        outboxRepository.save(outboxEvent);

        return saved;
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}")
    public ProductEntity get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));
    }

    public ProductEntity findProductEntity(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));
    } 
}
