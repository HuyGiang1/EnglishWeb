package com.englishflow.dictation.dto;

import java.util.UUID;

public record DictationExerciseDetailResponse(
    UUID contentId,
    String title,
    String cefrLevel,
    String topicTag,
    String audioUrl,
    Double audioDurationSeconds,
    boolean isAudioAvailable
) {
    public DictationExerciseDetailResponse(
        UUID contentId,
        String title,
        String cefrLevel,
        String topicTag,
        String audioUrl,
        Double audioDurationSeconds
    ) {
        this(contentId, title, cefrLevel, topicTag, audioUrl, audioDurationSeconds, true);
    }
}
