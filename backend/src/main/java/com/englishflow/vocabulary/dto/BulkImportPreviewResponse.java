package com.englishflow.vocabulary.dto;

import java.util.List;

public record BulkImportPreviewResponse(
    int totalRows,
    int validRows,
    int duplicateRows,
    int errorRows,
    List<BulkImportRowError> errors,
    List<BulkImportRowPreview> previewRows,
    List<BulkImportRowPreview> previewSample
) {}
