package com.englishflow.quiz.dto;

import java.util.List;
import java.util.UUID;

public record QuizQuestionResultDto(
    UUID questionId,
    String prompt,
    String questionType,
    List<String> options,
    String selectedAnswer,
    String canonicalAnswer,
    boolean isCorrect,
    String explanation,
    int orderIndex
) {}
