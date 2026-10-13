package com.englishflow.review.dto;

public record SubmissionResult(
    ReviewSubmissionResponse response,
    boolean isReplay
) {}
