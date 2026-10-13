package com.englishflow.quiz.dto;

import java.util.List;
import java.util.UUID;

public record QuizQuestionDto(
    UUID questionId,
    String prompt,
    String questionType,
    List<String> options,
    int orderIndex
) {}
