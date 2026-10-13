package com.englishflow.quiz.dto;

import java.util.UUID;

public record QuizSubmitAnswerResponse(
    UUID questionId,
    boolean saved
) {}
