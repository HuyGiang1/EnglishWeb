package com.englishflow.quiz.dto;

import java.util.List;
import java.util.UUID;

public record QuizCompleteRequest(
    UUID attemptId,
    List<QuizAnswerItemDto> answers
) {}
