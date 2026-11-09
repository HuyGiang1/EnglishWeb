package com.englishflow.user.dto;

public record UpdateSettingsRequest(
    String displayName,
    String timezone,
    Integer dailyGoalItems,
    Double preferredAudioSpeed,
    String preferredLocale
) {
    public UpdateSettingsRequest(String displayName, String timezone, Integer dailyGoalItems, Double preferredAudioSpeed) {
        this(displayName, timezone, dailyGoalItems, preferredAudioSpeed, null);
    }
}
