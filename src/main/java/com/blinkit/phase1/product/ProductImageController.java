package com.blinkit.phase1.product;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves locally downloaded product images.
 *
 * Security:
 *  - Filename validated against strict regex (UUID + known extension only)
 *  - Path traversal blocked: resolved path must remain inside storageDir
 *  - Content-Type set explicitly from extension — never trusted from disk metadata
 */
@Slf4j
@RestController
@RequestMapping("/api/images")
public class ProductImageController {

    // Only allow UUID filenames with known image extensions — nothing else
    private static final String SAFE_FILENAME_REGEX = "[0-9a-fA-F\\-]{36}\\.(jpg|jpeg|png|webp|gif)";

    private final Path storageDir;

    public ProductImageController(
            @Value("${blinkit.images.storage-dir:./product-images}") String storageDir
    ) {
        this.storageDir = Paths.get(storageDir).toAbsolutePath().normalize();
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {

        // Security: strict whitelist — UUID + known extension only
        if (!filename.matches(SAFE_FILENAME_REGEX)) {
            log.warn("Blocked suspicious image request: '{}'", filename);
            return ResponseEntity.badRequest().build();
        }

        // Security: resolve then verify still inside storageDir (blocks ../ traversal)
        Path file = storageDir.resolve(filename).normalize();
        if (!file.startsWith(storageDir)) {
            log.warn("Path traversal attempt blocked: '{}'", filename);
            return ResponseEntity.badRequest().build();
        }

        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        // Determine media type from extension only (never from file content or request)
        MediaType mediaType = filename.endsWith(".png")  ? MediaType.IMAGE_PNG
                            : filename.endsWith(".webp") ? MediaType.parseMediaType("image/webp")
                            : filename.endsWith(".gif")  ? MediaType.IMAGE_GIF
                            : MediaType.IMAGE_JPEG;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(new FileSystemResource(file));
    }
}
