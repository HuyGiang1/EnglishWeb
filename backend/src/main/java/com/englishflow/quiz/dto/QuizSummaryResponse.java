package com.englishflow.quiz.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record QuizSummaryResponse(
    UUID id,
    String title,
    String cefrLevel,
    String topicTag,
    String description,
    int questionCount,
    int attemptCount,
    BigDecimal bestScore,
    BigDecimal latestScore
) {}
