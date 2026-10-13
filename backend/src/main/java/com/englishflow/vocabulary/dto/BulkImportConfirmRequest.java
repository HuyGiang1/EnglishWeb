package com.englishflow.vocabulary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkImportConfirmRequest(
    @NotNull
    @Size(min = 1, max = 1000)
    List<@Valid BulkImportItemRequest> items,

    UUID collectionId,

    String duplicatePolicy
) {
    public static final String POLICY_SKIP = "SKIP";
    public static final String POLICY_REJECT = "REJECT";
}
