package com.englishflow.dictation.dto;

import java.util.UUID;

public record DictationExerciseSummaryResponse(
    UUID contentId,
    String title,
    String cefrLevel,
    String topicTag,
    String audioUrl,
    Double audioDurationSeconds,
    boolean isCompleted,
    Double lastAccuracyScore,
    boolean isAudioAvailable
) {
    public DictationExerciseSummaryResponse(
        UUID contentId,
        String title,
        String cefrLevel,
        String topicTag,
        String audioUrl,
        Double audioDurationSeconds,
        boolean isCompleted,
        Double lastAccuracyScore
    ) {
        this(contentId, title, cefrLevel, topicTag, audioUrl, audioDurationSeconds, isCompleted, lastAccuracyScore, true);
    }
}
