package com.englishflow.review.scheduler;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface SrsScheduler {
    SchedulingResult calculate(
        int currentIntervalDays,
        BigDecimal currentEaseFactor,
        int currentRepetitionCount,
        ReviewRating rating,
        LocalDate referenceDate
    );
}
