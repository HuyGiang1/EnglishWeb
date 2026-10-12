package com.englishflow.auth.dto;

import com.englishflow.user.User;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String displayName,
    String timezone,
    int dailyGoalItems
) {
    public static UserResponse fromUser(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getTimezone(),
            user.getDailyGoalItems()
        );
    }
}
