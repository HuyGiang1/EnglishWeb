package com.englishflow.quiz.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuizCompleteResponse(
    UUID quizAttemptId,
    UUID attemptId,
    UUID contentId,
    String title,
    BigDecimal scorePercentage,
    int correctCount,
    int totalQuestions,
    int incorrectCount,
    int unansweredCount,
    boolean verifiedActivityCreated,
    Instant completedAt,
    List<QuizQuestionResultDto> questions
) {}
