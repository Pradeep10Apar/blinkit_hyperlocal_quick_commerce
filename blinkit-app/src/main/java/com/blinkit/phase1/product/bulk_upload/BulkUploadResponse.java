package com.blinkit.phase1.product.bulk_upload;


import java.util.List;

public record BulkUploadResponse(
        int totalRows,
        int inserted,
        int rejected,
        List<RowError> errors
) {
    public record RowError(int rowNumber, String message) {}
}

