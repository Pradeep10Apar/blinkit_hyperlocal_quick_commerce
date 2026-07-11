package com.blinkit.phase1.product;

import com.blinkit.phase1.product.bulk_upload.*;
import com.blinkit.phase1.product.dto.CreateProductRequest;
import com.blinkit.phase1.product.dto.ProductResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService service;

    private final ProductSearchService productSearchService;

    private final ProductBulkUploadService productBulkUploadService;

    private final ProductImageDownloadService productImageDownloadService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest req) {
        return ProductMapper.toResponse(service.create(req));
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return ProductMapper.toResponse(service.get(id));
    }

    @GetMapping("/search")
    public List<ProductResponse> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return productSearchService.search(q, page, size);
    }

    @PostMapping(value = "/bulk/upload-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BulkUploadResponse uploadCsv(@RequestPart("file") MultipartFile file) throws Exception {
        return productBulkUploadService.upload(file);
    }

    /**
     * One-time trigger: downloads images from Open Food Facts for all products
     * missing an imageUrl and stores them locally on this server.
     * Safe to call multiple times — only processes products with null imageUrl.
     */
    @PostMapping("/download-images")
    public ProductImageDownloadService.DownloadResult downloadImages() {
        return productImageDownloadService.downloadMissingImages();
    }

}
