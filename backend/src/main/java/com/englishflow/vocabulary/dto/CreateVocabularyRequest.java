package com.englishflow.vocabulary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateVocabularyRequest(
    @NotBlank(message = "Term is required")
    @Size(max = 150, message = "Term must not exceed 150 characters")
    String term,

    @NotBlank(message = "Part of speech is required")
    @Pattern(
        regexp = "^(?i)(noun|verb|adjective|adverb|idiom|phrase|preposition|conjunction|other)$",
        message = "Invalid part of speech. Allowed values: noun, verb, adjective, adverb, idiom, phrase, preposition, conjunction, other"
    )
    String partOfSpeech,

    @Size(max = 100, message = "Phonetic IPA must not exceed 100 characters")
    String phoneticIpa,

    @NotBlank(message = "Vietnamese meaning is required")
    String vietnameseMeaning,

    String englishDefinition,

    String exampleSentence,

    String exampleTranslation,

    String notes,

    @Size(max = 100, message = "Source must not exceed 100 characters")
    String source,

    @Pattern(
        regexp = "^(A1|A2|B1|B2|C1|C2)$",
        message = "Difficulty level must be one of: A1, A2, B1, B2, C1, C2"
    )
    String difficultyLevel
) {}
