package com.englishflow.vocabulary.dto;

import com.englishflow.vocabulary.Vocabulary;
import java.time.Instant;
import java.util.UUID;

public record VocabularyResponse(
    UUID id,
    String term,
    String partOfSpeech,
    String phoneticIpa,
    String vietnameseMeaning,
    String englishDefinition,
    String exampleSentence,
    String exampleTranslation,
    String notes,
    String source,
    String difficultyLevel,
    Instant createdAt,
    Instant updatedAt,
    SrsStatusDto srsStatus
) {
    public static VocabularyResponse fromEntity(Vocabulary v) {
        return new VocabularyResponse(
            v.getId(),
            v.getTerm(),
            v.getPartOfSpeech(),
            v.getPhoneticIpa(),
            v.getVietnameseMeaning(),
            v.getEnglishDefinition(),
            v.getExampleSentence(),
            v.getExampleTranslation(),
            v.getNotes(),
            v.getSource(),
            v.getDifficultyLevel(),
            v.getCreatedAt(),
            v.getUpdatedAt(),
            SrsStatusDto.initial()
        );
    }
}
