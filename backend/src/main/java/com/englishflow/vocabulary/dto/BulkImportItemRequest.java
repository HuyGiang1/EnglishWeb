package com.englishflow.vocabulary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BulkImportItemRequest(
    @NotBlank
    @Size(max = 100)
    String term,

    @Size(max = 30)
    String partOfSpeech,

    @NotBlank
    @Size(max = 500)
    String vietnameseMeaning,

    @Size(max = 100)
    String phoneticIpa,

    @Size(max = 1000)
    String englishDefinition,

    @Size(max = 1000)
    String exampleSentence,

    @Size(max = 1000)
    String exampleTranslation,

    String notes,

    @Size(max = 100)
    String source,

    @Size(max = 10)
    String difficultyLevel
) {}
