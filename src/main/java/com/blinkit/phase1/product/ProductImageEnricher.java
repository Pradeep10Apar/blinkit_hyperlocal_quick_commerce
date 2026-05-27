package com.blinkit.phase1.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Enriches products that have no imageUrl by querying the
 * Open Food Facts public API (https://world.openfoodfacts.org).
 *
 * No API key required. Images are licensed under ODbL — safe for demos.
 *
 * Security:
 *  - Only https:// URLs are accepted and stored.
 *  - This service only STORES the URL; it never proxies/fetches the image
 *    server-side, so there is no SSRF risk.
 */
@Slf4j
@Service
public class ProductImageEnricher {

    private static final String OFF_SEARCH = "https://world.openfoodfacts.org/cgi/search.pl";

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;
    private final RestClient http;

    public ProductImageEnricher(ProductRepository productRepository, ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.objectMapper = objectMapper;
        this.http = RestClient.create();
    }

    /**
     * Queries Open Food Facts for every product in the DB that has no imageUrl,
     * and persists the best matching image URL.
     *
     * @return number of products successfully enriched
     */
    public int enrichMissingImages() {
        List<ProductEntity> products = productRepository.findByImageUrlIsNull();
        log.info("Enriching images for {} products with missing imageUrl", products.size());

        int enriched = 0;
        for (ProductEntity p : products) {
            try {
                String imageUrl = fetchFromOpenFoodFacts(p.getName(), p.getBrand());
                if (imageUrl != null) {
                    p.setImageUrl(imageUrl);
                    productRepository.save(p);
                    enriched++;
                    log.debug("Enriched '{}' -> {}", p.getName(), imageUrl);
                } else {
                    log.debug("No image found for '{}'", p.getName());
                }
                // Be polite to the free API — 200 ms between requests
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Enrichment interrupted after {} products", enriched);
                break;
            } catch (Exception e) {
                log.warn("Failed to enrich image for '{}': {}", p.getName(), e.getMessage());
            }
        }

        log.info("Image enrichment complete: {}/{} products updated", enriched, products.size());
        return enriched;
    }

    private String fetchFromOpenFoodFacts(String name, String brand) {
        try {
            // Combine brand + name for a more precise search (e.g. "Amul Butter 100g")
            String query = (brand != null && !brand.isBlank()) ? brand + " " + name : name;

            URI uri = UriComponentsBuilder.fromHttpUrl(OFF_SEARCH)
                    .queryParam("search_terms", query)
                    .queryParam("search_simple", "1")
                    .queryParam("action", "process")
                    .queryParam("json", "1")
                    .queryParam("page_size", "1")
                    .encode()
                    .build()
                    .toUri();

            String response = http.get()
                    .uri(uri)
                    // Open Food Facts asks that bots identify themselves
                    .header("User-Agent", "Blinkit-Phase1-Demo/1.0 (educational project)")
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode productsArr = root.path("products");
            if (!productsArr.isArray() || productsArr.isEmpty()) return null;

            JsonNode product = productsArr.get(0);

            // Prefer the front-facing product image; fall back to generic image_url
            for (String field : new String[]{"image_front_url", "image_url"}) {
                String url = product.path(field).asText(null);
                // Security: only store https:// URLs — never http or data URIs
                if (url != null && url.startsWith("https://")) {
                    return url;
                }
            }
        } catch (Exception e) {
            log.warn("Open Food Facts lookup failed for '{}': {}", name, e.getMessage());
        }
        return null;
    }
}
