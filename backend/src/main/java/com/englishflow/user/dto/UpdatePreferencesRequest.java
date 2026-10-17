package com.englishflow.user.dto;

public record UpdatePreferencesRequest(
    String timezone,
    Integer dailyGoalItems,
    Double preferredAudioSpeed
) {
}
