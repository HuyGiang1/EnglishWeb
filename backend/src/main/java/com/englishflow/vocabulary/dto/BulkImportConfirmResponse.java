package com.englishflow.vocabulary.dto;

public record BulkImportConfirmResponse(
    int importedCount,
    int skippedCount,
    int rejectedCount
) {}
