package com.englishflow.quiz.dto;

import java.util.UUID;

public record QuizAnswerItemDto(
    UUID questionId,
    String selectedAnswer
) {}
