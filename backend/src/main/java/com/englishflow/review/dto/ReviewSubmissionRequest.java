package com.englishflow.review.dto;

import com.englishflow.review.scheduler.ReviewRating;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record ReviewSubmissionRequest(
    @NotNull(message = "srsItemId is mandatory")
    UUID srsItemId,

    @NotNull(message = "attemptId is mandatory")
    UUID attemptId,

    @NotNull(message = "grade is mandatory and must be AGAIN, HARD, GOOD, or EASY")
    ReviewRating grade,

    @PositiveOrZero(message = "durationSeconds must be non-negative")
    Integer durationSeconds
) {
    @JsonCreator
    public static ReviewSubmissionRequest create(
        @JsonProperty("srsItemId") UUID srsItemId,
        @JsonProperty("attemptId") UUID attemptId,
        @JsonProperty("grade") Object rawGrade,
        @JsonProperty("durationSeconds") Integer durationSeconds
    ) {
        ReviewRating parsedRating = null;
        if (rawGrade instanceof String str) {
            parsedRating = ReviewRating.fromString(str);
        } else if (rawGrade instanceof Number num) {
            parsedRating = ReviewRating.fromGrade(num.intValue());
        } else if (rawGrade instanceof ReviewRating rr) {
            parsedRating = rr;
        }

        return new ReviewSubmissionRequest(
            srsItemId,
            attemptId,
            parsedRating,
            durationSeconds != null ? durationSeconds : 0
        );
    }
}
