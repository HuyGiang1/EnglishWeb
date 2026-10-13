package com.englishflow.quiz.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record QuizHistoryItemDto(
    UUID attemptId,
    UUID quizAttemptId,
    UUID contentId,
    String title,
    String cefrLevel,
    String topicTag,
    BigDecimal scorePercentage,
    int correctAnswers,
    int totalQuestions,
    Instant completedAt
) {}
