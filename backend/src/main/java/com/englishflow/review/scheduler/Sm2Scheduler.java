package com.englishflow.review.scheduler;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * SuperMemo SM-2 Scheduling Algorithm Engine.
 * Adapted for 4-button review modality: AGAIN, HARD, GOOD, EASY.
 */
@Component
public class Sm2Scheduler implements SrsScheduler {

    public static final BigDecimal MIN_EASE_FACTOR = new BigDecimal("1.30");
    public static final BigDecimal DEFAULT_EASE_FACTOR = new BigDecimal("2.50");

    @Override
    public SchedulingResult calculate(
        int currentIntervalDays,
        BigDecimal currentEaseFactor,
        int currentRepetitionCount,
        ReviewRating rating,
        LocalDate referenceDate
    ) {
        BigDecimal ef = (currentEaseFactor != null && currentEaseFactor.compareTo(MIN_EASE_FACTOR) >= 0)
            ? currentEaseFactor
            : DEFAULT_EASE_FACTOR;

        int newIntervalDays;
        BigDecimal newEaseFactor;
        int newRepetitionCount;
        String nextState;

        switch (rating) {
            case AGAIN -> {
                newRepetitionCount = 0;
                newIntervalDays = 1;
                newEaseFactor = ef.subtract(new BigDecimal("0.20")).max(MIN_EASE_FACTOR);
                nextState = "LEARNING";
            }
            case HARD -> {
                newRepetitionCount = currentRepetitionCount + 1;
                if (newRepetitionCount == 1) {
                    newIntervalDays = 1;
                } else if (newRepetitionCount == 2) {
                    newIntervalDays = 3;
                } else {
                    newIntervalDays = Math.max(currentIntervalDays + 1, (int) Math.round(currentIntervalDays * 1.2));
                }
                newEaseFactor = ef.subtract(new BigDecimal("0.15")).max(MIN_EASE_FACTOR);
                nextState = "REVIEW";
            }
            case GOOD -> {
                newRepetitionCount = currentRepetitionCount + 1;
                if (newRepetitionCount == 1) {
                    newIntervalDays = 1;
                } else if (newRepetitionCount == 2) {
                    newIntervalDays = 6;
                } else {
                    newIntervalDays = Math.max(
                        currentIntervalDays + 1,
                        (int) Math.round(currentIntervalDays * ef.doubleValue())
                    );
                }
                newEaseFactor = ef;
                nextState = "REVIEW";
            }
            case EASY -> {
                newRepetitionCount = currentRepetitionCount + 1;
                if (newRepetitionCount == 1) {
                    newIntervalDays = 4;
                } else if (newRepetitionCount == 2) {
                    newIntervalDays = 8;
                } else {
                    newIntervalDays = Math.max(
                        currentIntervalDays + 2,
                        (int) Math.round(currentIntervalDays * ef.doubleValue() * 1.3)
                    );
                }
                newEaseFactor = ef.add(new BigDecimal("0.15"));
                nextState = "REVIEW";
            }
            default -> throw new IllegalArgumentException("Unknown rating: " + rating);
        }

        // Clamp ease factor and round to 2 decimal places
        newEaseFactor = newEaseFactor.max(MIN_EASE_FACTOR).setScale(2, RoundingMode.HALF_UP);
        newIntervalDays = Math.max(1, newIntervalDays);

        LocalDate nextDueDate = referenceDate.plusDays(newIntervalDays);
        String label = SchedulingResult.formatIntervalLabel(newIntervalDays);

        return new SchedulingResult(
            newIntervalDays,
            newEaseFactor,
            newRepetitionCount,
            nextState,
            nextDueDate,
            label
        );
    }
}
