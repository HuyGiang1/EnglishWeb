package com.englishflow.quiz.dto;

public record QuizSubmissionResult(
    QuizCompleteResponse response,
    boolean isReplay
) {}
