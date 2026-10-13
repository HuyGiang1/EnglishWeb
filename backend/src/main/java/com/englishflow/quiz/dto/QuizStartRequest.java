package com.englishflow.quiz.dto;

import java.util.UUID;

public record QuizStartRequest(
    UUID attemptId
) {}
