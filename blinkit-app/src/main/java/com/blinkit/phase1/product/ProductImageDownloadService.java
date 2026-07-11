package com.blinkit.phase1.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

/**
 * Downloads product images from Open Food Facts ONCE and stores them locally.
 * After download, products serve images from this server — no dependency on OFF at runtime.
 *
 * Security measures:
 *  - Content-Type validated: only image/jpeg, image/png, image/webp, image/gif accepted
 *  - File size capped at 5 MB
 *  - Filename derived from UUID only (no user input in path)
 *  - Path traversal blocked at serve time (see ProductImageController)
 */
@Slf4j
@Service
public class ProductImageDownloadService {

    private static final String OFF_SEARCH = "https://world.openfoodfacts.org/cgi/search.pl";

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024; // 5 MB hard cap

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;
    private final RestClient http;
    private final Path storageDir;
    private final String baseUrl;

    public ProductImageDownloadService(
            ProductRepository productRepository,
            ObjectMapper objectMapper,
            @Value("${blinkit.images.storage-dir:./product-images}") String storageDir,
            @Value("${blinkit.images.base-url:http://localhost:8080/api/images}") String baseUrl
    ) throws IOException {
        this.productRepository = productRepository;
        this.objectMapper = objectMapper;
        this.http = RestClient.create();
        this.storageDir = Paths.get(storageDir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl;
        Files.createDirectories(this.storageDir);
        log.info("Product images directory: {}", this.storageDir);
    }

    public record DownloadResult(int total, int downloaded, int skipped) {}

    /**
     * For every product in the DB with a null imageUrl:
     *  1. Looks up the product on Open Food Facts
     *  2. Downloads the image binary to disk
     *  3. Updates imageUrl to point to this server
     *
     * Safe to call multiple times — only processes products with null imageUrl.
     */
    public DownloadResult downloadMissingImages() {
        List<ProductEntity> products = productRepository.findByImageUrlIsNull();
        log.info("Starting image download for {} products with missing imageUrl", products.size());

        int downloaded = 0;
        int skipped = 0;

        for (ProductEntity p : products) {
            try {
                String offImageUrl = fetchImageUrlFromOFF(p.getName(), p.getBrand());
                if (offImageUrl == null) {
                    log.debug("No OFF image found for '{}'", p.getName());
                    skipped++;
                    Thread.sleep(250); // be polite even on miss
                    continue;
                }

                String filename = downloadAndSave(offImageUrl, p.getId().toString());
                if (filename != null) {
                    p.setImageUrl(baseUrl + "/" + filename);
                    productRepository.save(p);
                    downloaded++;
                    log.info("Saved image for '{}' -> {}", p.getName(), filename);
                } else {
                    skipped++;
                }

                Thread.sleep(250); // ~4 req/s — respectful to the free API

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Download interrupted after {}/{} images", downloaded, products.size());
                break;
            } catch (Exception e) {
                log.warn("Error processing '{}': {}", p.getName(), e.getMessage());
                skipped++;
            }
        }

        log.info("Image download complete: downloaded={}, skipped={}, total={}", downloaded, skipped, products.size());
        return new DownloadResult(products.size(), downloaded, skipped);
    }

    // ── Step 1: Ask OFF for the product's image URL ────────────────────────────

    private String fetchImageUrlFromOFF(String name, String brand) {
        try {
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
                    .header("User-Agent", "Blinkit-Phase1-Demo/1.0 (educational project)")
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode arr = root.path("products");
            if (!arr.isArray() || arr.isEmpty()) return null;

            JsonNode product = arr.get(0);
            for (String field : new String[]{"image_front_url", "image_url"}) {
                String url = product.path(field).asText(null);
                if (url != null && url.startsWith("https://")) return url; // only https
            }
        } catch (Exception e) {
            log.warn("OFF search failed for '{}': {}", name, e.getMessage());
        }
        return null;
    }

    // ── Step 2: Download the binary and save to disk ────────────────────────────

    private String downloadAndSave(String imageUrl, String productId) {
        try {
            ResponseEntity<byte[]> resp = http.get()
                    .uri(URI.create(imageUrl))
                    .header("User-Agent", "Blinkit-Phase1-Demo/1.0")
                    .retrieve()
                    .toEntity(byte[].class);

            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("Non-2xx response downloading image for productId={}", productId);
                return null;
            }

            // Security: validate Content-Type — reject anything that is not an image
            String contentType = resp.getHeaders().getFirst("Content-Type");
            if (contentType == null || ALLOWED_CONTENT_TYPES.stream().noneMatch(contentType::startsWith)) {
                log.warn("Rejected non-image content-type '{}' for productId={}", contentType, productId);
                return null;
            }

            // Security: enforce file size cap
            byte[] bytes = resp.getBody();
            if (bytes.length > MAX_IMAGE_BYTES) {
                log.warn("Image too large ({} bytes) for productId={}, skipping", bytes.length, productId);
                return null;
            }

            // Extension from content-type (never from user input)
            String ext = contentType.startsWith("image/png")  ? ".png"
                       : contentType.startsWith("image/webp") ? ".webp"
                       : contentType.startsWith("image/gif")  ? ".gif"
                       : ".jpg";

            // Filename = UUID only — zero user input involved
            String filename = productId + ext;
            Path target = storageDir.resolve(filename);
            Files.write(target, bytes);
            return filename;

        } catch (Exception e) {
            log.warn("Failed to download/save image from '{}': {}", imageUrl, e.getMessage());
            return null;
        }
    }
}
