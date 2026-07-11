package com.blinkit.phase1.product.bulk_upload;

import com.blinkit.phase1.product.ProductEntity;
import com.blinkit.phase1.product.ProductRepository;
import com.blinkit.phase1.product.outbox.ProductOutboxEvent;
import com.blinkit.phase1.product.outbox.ProductOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductBulkUploadService {

    private final ProductRepository productRepository;
    private final ProductOutboxRepository outboxRepository;
    private final EntityManager em;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    @Transactional
    public BulkUploadResponse upload(MultipartFile file) throws Exception {

        if (file == null || file.isEmpty()) {
            return new BulkUploadResponse(0, 0, 0, List.of(new BulkUploadResponse.RowError(0, "Empty file")));
        }

        var errors = new ArrayList<BulkUploadResponse.RowError>();
        var now = Instant.now();

        int inserted = 0;
        int total = 0;

        var format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        var productsBatch = new ArrayList<ProductEntity>(200);
        var outboxBatch = new ArrayList<ProductOutboxEvent>(200);

        try (var reader = new InputStreamReader(file.getInputStream())) {
            Iterable<CSVRecord> records = format.parse(reader);

            for (CSVRecord r : records) {
                total++;
                int rowNumber = (int) r.getRecordNumber() + 1; // +1 for header approx

                try {
                    ProductCsvRow row = toRow(r);
                    validateRow(row, rowNumber);

                    UUID id = UUID.randomUUID();
                    ProductEntity p = new ProductEntity();
                    p.setId(id);
                    p.setName(row.name());
                    p.setBrand(row.brand());
                    p.setCategory(row.category());
                    p.setPrice(row.price());
                    p.setActive(row.active());
                    p.setImageUrl(row.imageUrl());
                    p.setCreatedAt(now);
                    p.setUpdatedAt(now);

                    productsBatch.add(p);

                    // payload contains ES-ready document
                    String payload = objectMapper.writeValueAsString(Map.of(
                            "id", id.toString(),
                            "name", p.getName(),
                            "brand", p.getBrand(),
                            "category", p.getCategory(),
                            "price", p.getPrice(),
                            "active", p.isActive(),
                            "imageUrl", p.getImageUrl() != null ? p.getImageUrl() : "",
                            "createdAt", p.getCreatedAt().toString(),
                            "updatedAt", p.getUpdatedAt().toString()
                    ));

                    outboxBatch.add(ProductOutboxEvent.builder()
                            .id(UUID.randomUUID())
                            .aggregateId(id)
                            .eventType("PRODUCT_UPSERTED")
                            .payload(payload)
                            .status("NEW")
                            .attempts(0)
                            .createdAt(now)
                            .updatedAt(now)
                            .build());

                    if (productsBatch.size() >= 200) {
                        inserted += flushBatch(productsBatch, outboxBatch);
                    }
                } catch (Exception ex) {
                    errors.add(new BulkUploadResponse.RowError(rowNumber, ex.getMessage()));
                }
            }
        }

        if (!productsBatch.isEmpty()) {
            inserted += flushBatch(productsBatch, outboxBatch);
        }

        return new BulkUploadResponse(total, inserted, errors.size(), errors);
    }

    private int flushBatch(List<ProductEntity> products, List<ProductOutboxEvent> outbox) {
        productRepository.saveAll(products);
        outboxRepository.saveAll(outbox);

        em.flush();
        em.clear();

        int count = products.size();
        products.clear();
        outbox.clear();
        return count;
    }

    private ProductCsvRow toRow(CSVRecord r) {
        String name = r.get("name");
        String brand = getOpt(r, "brand");
        String category = getOpt(r, "category");
        BigDecimal price = new BigDecimal(r.get("price"));
        boolean active = Boolean.parseBoolean(getOpt(r, "active", "true"));
        String imageUrl = getOpt(r, "image_url");  // optional CSV column
        return new ProductCsvRow(name, brand, category, price, active, imageUrl);
    }

    private void validateRow(ProductCsvRow row, int rowNumber) {
        Set<ConstraintViolation<ProductCsvRow>> v = validator.validate(row);
        if (!v.isEmpty()) {
            String msg = v.iterator().next().getPropertyPath() + " " + v.iterator().next().getMessage();
            throw new IllegalArgumentException("Row " + rowNumber + " invalid: " + msg);
        }
    }

    private static String getOpt(CSVRecord r, String key) {
        return r.isMapped(key) ? r.get(key) : null;
    }
    private static String getOpt(CSVRecord r, String key, String def) {
        String v = r.isMapped(key) ? r.get(key) : null;
        return (v == null || v.isBlank()) ? def : v;
    }
}
