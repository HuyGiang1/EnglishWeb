package com.englishflow.vocabulary.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record PasteBatchRequest(
    @NotNull @Size(min = 1, max = 1000) List<PasteImportRow> rows,
    String collectionId,
    String conflictPolicy
) {}
