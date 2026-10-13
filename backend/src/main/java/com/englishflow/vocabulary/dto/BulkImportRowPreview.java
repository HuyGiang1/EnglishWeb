package com.englishflow.vocabulary.dto;

public record BulkImportRowPreview(
    int rowNumber,
    String term,
    String partOfSpeech,
    String vietnameseMeaning,
    String phoneticIpa,
    String englishDefinition,
    String exampleSentence,
    String exampleTranslation,
    String notes,
    String source,
    String difficultyLevel,
    String status, // "VALID", "DUPLICATE_FILE", "DUPLICATE_LIBRARY", "INVALID"
    String validationMessage
) {}
