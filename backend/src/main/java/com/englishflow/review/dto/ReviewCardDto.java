package com.englishflow.review.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ReviewCardDto(
    UUID srsItemId,
    UUID vocabularyId,
    String term,
    String partOfSpeech,
    String phoneticIpa,
    String vietnameseMeaning,
    String englishDefinition,
    String exampleSentence,
    int intervalDays,
    BigDecimal easeFactor
) {}
