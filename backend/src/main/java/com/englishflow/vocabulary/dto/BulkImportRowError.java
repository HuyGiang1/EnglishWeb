package com.englishflow.vocabulary.dto;

public record BulkImportRowError(
    int rowNumber,
    String reason,
    String field
) {}
