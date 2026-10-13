package com.englishflow.analytics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Breakdown of verified learning activities completed on a local calendar day across all four learning modalities.
 *
 * <p><b>Granularity Semantics</b>:
 * <ul>
 *   <li>{@code flashcardReviews}: 1 count per graded SRS flashcard submission (FLASHCARD_REVIEW).</li>
 *   <li>{@code dictationExercises}: 1 count per submitted and evaluated sentence transcription (DICTATION_SENTENCE).</li>
 *   <li>{@code shadowingTakes}: 1 count per completed and self-rated speech take (SHADOWING_TAKE).</li>
 *   <li>{@code quizQuestions}: Legacy event-type counter reflecting atomic completed quiz assessment sessions
 *       (QUIZ_QUESTION event type). In Milestone M2, each completed quiz attempt logs exactly 1 event.</li>
 * </ul>
 */
public record DayActivityBreakdownDto(
    long flashcardReviews,
    long dictationExercises,
    long shadowingTakes,
    long quizQuestions
) {
    /**
     * Backward-compatible alias for {@link #quizQuestions()}, clarifying that each completed
     * quiz assessment contributes 1 atomic qualifying unit in MVP.
     */
    @JsonProperty("quizzesCompleted")
    public long quizzesCompleted() {
        return quizQuestions;
    }
}
