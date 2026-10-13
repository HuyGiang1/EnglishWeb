package com.englishflow.quiz.dto;

import java.util.UUID;

public record QuizSavedAnswerDto(
    UUID questionId,
    String selectedAnswer
) {}
