package com.englishflow.review.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class Sm2SchedulerTest {

    private Sm2Scheduler scheduler;
    private final LocalDate today = LocalDate.of(2026, 10, 12);

    @BeforeEach
    void setUp() {
        scheduler = new Sm2Scheduler();
    }

    @Test
    @DisplayName("Initial review: AGAIN sets interval 1, resets repetitions, drops ease factor")
    void initialAgain() {
        SchedulingResult result = scheduler.calculate(0, new BigDecimal("2.50"), 0, ReviewRating.AGAIN, today);

        assertThat(result.newIntervalDays()).isEqualTo(1);
        assertThat(result.newRepetitionCount()).isEqualTo(0);
        assertThat(result.newEaseFactor()).isEqualByComparingTo(new BigDecimal("2.30"));
        assertThat(result.nextState()).isEqualTo("LEARNING");
        assertThat(result.nextDueDate()).isEqualTo(today.plusDays(1));
    }

    @Test
    @DisplayName("Initial review: HARD sets interval 1, increments repetitions")
    void initialHard() {
        SchedulingResult result = scheduler.calculate(0, new BigDecimal("2.50"), 0, ReviewRating.HARD, today);

        assertThat(result.newIntervalDays()).isEqualTo(1);
        assertThat(result.newRepetitionCount()).isEqualTo(1);
        assertThat(result.newEaseFactor()).isEqualByComparingTo(new BigDecimal("2.35"));
        assertThat(result.nextState()).isEqualTo("REVIEW");
        assertThat(result.nextDueDate()).isEqualTo(today.plusDays(1));
    }

    @Test
    @DisplayName("Initial review: GOOD sets interval 1, keeps default ease factor 2.50")
    void initialGood() {
        SchedulingResult result = scheduler.calculate(0, new BigDecimal("2.50"), 0, ReviewRating.GOOD, today);

        assertThat(result.newIntervalDays()).isEqualTo(1);
        assertThat(result.newRepetitionCount()).isEqualTo(1);
        assertThat(result.newEaseFactor()).isEqualByComparingTo(new BigDecimal("2.50"));
        assertThat(result.nextState()).isEqualTo("REVIEW");
        assertThat(result.nextDueDate()).isEqualTo(today.plusDays(1));
    }

    @Test
    @DisplayName("Initial review: EASY sets interval 4, increases ease factor to 2.65")
    void initialEasy() {
        SchedulingResult result = scheduler.calculate(0, new BigDecimal("2.50"), 0, ReviewRating.EASY, today);

        assertThat(result.newIntervalDays()).isEqualTo(4);
        assertThat(result.newRepetitionCount()).isEqualTo(1);
        assertThat(result.newEaseFactor()).isEqualByComparingTo(new BigDecimal("2.65"));
        assertThat(result.nextState()).isEqualTo("REVIEW");
        assertThat(result.nextDueDate()).isEqualTo(today.plusDays(4));
    }

    @Test
    @DisplayName("Second consecutive GOOD review sets interval 6")
    void secondGood() {
        SchedulingResult result = scheduler.calculate(1, new BigDecimal("2.50"), 1, ReviewRating.GOOD, today);

        assertThat(result.newIntervalDays()).isEqualTo(6);
        assertThat(result.newRepetitionCount()).isEqualTo(2);
        assertThat(result.nextDueDate()).isEqualTo(today.plusDays(6));
    }

    @Test
    @DisplayName("Third consecutive GOOD review multiplies interval by ease factor")
    void thirdGood() {
        // 6 days * 2.50 = 15 days
        SchedulingResult result = scheduler.calculate(6, new BigDecimal("2.50"), 2, ReviewRating.GOOD, today);

        assertThat(result.newIntervalDays()).isEqualTo(15);
        assertThat(result.newRepetitionCount()).isEqualTo(3);
        assertThat(result.nextDueDate()).isEqualTo(today.plusDays(15));
    }

    @Test
    @DisplayName("Ease factor cannot drop below defined minimum 1.30")
    void easeFactorMinimumBoundary() {
        BigDecimal ef = new BigDecimal("1.35");
        SchedulingResult result = scheduler.calculate(10, ef, 3, ReviewRating.AGAIN, today);

        assertThat(result.newEaseFactor()).isEqualByComparingTo(new BigDecimal("1.30"));
        assertThat(result.newRepetitionCount()).isEqualTo(0);
        assertThat(result.newIntervalDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("Failed review on mature card resets repetitions and sets interval to 1")
    void matureCardAgainReset() {
        SchedulingResult result = scheduler.calculate(45, new BigDecimal("2.40"), 5, ReviewRating.AGAIN, today);

        assertThat(result.newIntervalDays()).isEqualTo(1);
        assertThat(result.newRepetitionCount()).isEqualTo(0);
        assertThat(result.nextState()).isEqualTo("LEARNING");
    }
}
