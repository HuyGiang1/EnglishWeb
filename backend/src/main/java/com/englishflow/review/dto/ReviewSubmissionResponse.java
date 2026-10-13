package com.englishflow.review.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReviewSubmissionResponse(
    UUID srsItemId,
    UUID attemptId,
    int newIntervalDays,
    BigDecimal newEaseFactor,
    LocalDate dueDate,
    String nextReviewLabel
) {}
