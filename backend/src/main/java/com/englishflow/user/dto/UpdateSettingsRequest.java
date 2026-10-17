package com.englishflow.user.dto;

public record UpdateSettingsRequest(
    String displayName,
    String timezone,
    Integer dailyGoalItems,
    Double preferredAudioSpeed
) {
}
