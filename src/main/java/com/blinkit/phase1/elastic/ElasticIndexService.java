package com.blinkit.phase1.elastic;

import com.blinkit.phase1.product.ProductEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Phase 1 Elasticsearch integration:
 * - Uses Elasticsearch REST API via WebClient (no ES Java client dependency).
 * - Synchronous indexing (called from ProductService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticIndexService {

    private final ElasticProperties props;
    private final ObjectMapper objectMapper;

    private WebClient client() {
        return WebClient.builder()
                .baseUrl(props.baseUrl())
                .build();
    }

    public void ensureIndexExists(String indexName) {
        try {
            Boolean exists = client()
                    .head()
                    .uri("/{index}", indexName)
                    .exchangeToMono(resp -> Mono.just(resp.statusCode().is2xxSuccessful()))
                    .timeout(Duration.ofSeconds(3))
                    .block();

            if (Boolean.TRUE.equals(exists)) {
                log.info("Elasticsearch index '{}' already exists.", indexName);
                return;
            }

            // Minimal index creation. Later phases will add analyzers/mappings for search/fuzzy.
            Map<String, Object> payload = Map.of(
                    "settings", Map.of("number_of_shards", 1, "number_of_replicas", 0)
            );

            client()
                    .put()
                    .uri("/{index}", indexName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class)
                            .map(body -> new RuntimeException("Failed to create index: " + body)))
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            log.info("Created Elasticsearch index '{}'.", indexName);

        } catch (Exception e) {
            log.warn("Could not ensure Elasticsearch index exists. App will still run, but indexing may fail. Error: {}", e.getMessage());
        }
    }

    public void indexProduct(ProductEntity product) {
        String index = props.index();
        String id = product.getId().toString();

        try {
            // Keep document shape stable (explicit map) rather than dumping entity blindly.
            Map<String, Object> doc = Map.of(
                    "id", id,
                    "name", product.getName(),
                    "brand", product.getBrand(),
                    "category", product.getCategory(),
                    "price", product.getPrice(),
                    "active", product.isActive(),
                    "createdAt", product.getCreatedAt(),
                    "updatedAt", product.getUpdatedAt()
            );

            client()
                    .put()
                    .uri("/{index}/_doc/{id}", index, id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(doc)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class)
                            .map(body -> new RuntimeException("Elasticsearch indexing failed: " + body)))
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            log.info("Indexed product {} into Elasticsearch index '{}'.", id, index);

        } catch (Exception e) {
            // Phase 1 behavior: if ES indexing fails, we still keep DB write (source of truth).
            // Later phases: switch to outbox + retries + DLQ.
            log.error("Failed to index product {} into Elasticsearch. Error: {}", id, e.getMessage());
        }
    }
}
