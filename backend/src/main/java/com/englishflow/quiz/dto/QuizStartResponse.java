package com.englishflow.quiz.dto;

import java.util.List;
import java.util.UUID;

public record QuizStartResponse(
    UUID quizAttemptId,
    UUID attemptId,
    UUID contentId,
    String title,
    int totalQuestions,
    List<QuizQuestionDto> questions,
    List<QuizSavedAnswerDto> savedAnswers
) {}
