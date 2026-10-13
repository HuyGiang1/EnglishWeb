package com.englishflow.vocabulary.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record AssignVocabularyRequest(
    @NotEmpty(message = "vocabularyIds list cannot be empty")
    List<UUID> vocabularyIds
) {}
