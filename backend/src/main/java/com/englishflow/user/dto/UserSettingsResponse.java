package com.englishflow.user.dto;

import com.englishflow.user.User;
import java.time.Instant;
import java.util.UUID;

public record UserSettingsResponse(
    UUID id,
    String email,
    String displayName,
    String timezone,
    int dailyGoalItems,
    Double preferredAudioSpeed,
    Instant createdAt,
    Instant updatedAt,
    String preferredLocale
) {
    public static UserSettingsResponse fromUser(User user) {
        return new UserSettingsResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getTimezone(),
            user.getDailyGoalItems(),
            user.getPreferredAudioSpeed() != null ? user.getPreferredAudioSpeed() : 1.0,
            user.getCreatedAt(),
            user.getUpdatedAt(),
            user.getPreferredLocale()
        );
    }
}
