package com.englishflow.review.scheduler;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SchedulingResult(
    int newIntervalDays,
    BigDecimal newEaseFactor,
    int newRepetitionCount,
    String nextState,
    LocalDate nextDueDate,
    String nextReviewLabel
) {
    public static String formatIntervalLabel(int intervalDays) {
        if (intervalDays <= 1) {
            return "1 day";
        }
        return intervalDays + " days";
    }
}
