package com.englishflow.user.dto;

public record UpdatePreferencesRequest(
    String timezone,
    Integer dailyGoalItems,
    Double preferredAudioSpeed,
    String preferredLocale
) {
    public UpdatePreferencesRequest(String timezone, Integer dailyGoalItems, Double preferredAudioSpeed) {
        this(timezone, dailyGoalItems, preferredAudioSpeed, null);
    }
}
