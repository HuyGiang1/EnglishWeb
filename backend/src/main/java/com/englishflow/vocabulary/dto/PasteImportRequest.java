package com.englishflow.vocabulary.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record PasteImportRequest(
    @NotNull String text,
    String delimiter,
    @Size(max = 16) List<String> mapping,
    boolean skipHeader,
    String collectionId
) {}
