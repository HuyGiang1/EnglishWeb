package com.englishflow.quiz.dto;

import java.util.UUID;

public record QuizSubmitAnswerRequest(
    UUID questionId,
    UUID attemptId,
    String selectedAnswer
) {}
