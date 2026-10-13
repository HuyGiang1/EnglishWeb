package com.englishflow.analytics.dto;

import java.time.Instant;
import java.util.Map;

public record HeatmapResponse(
    String startDate,
    String endDate,
    String userTimezone,
    Instant accountCreatedAt,
    int currentStreakDays,
    String streakStatus,
    int longestStreakDays,
    int totalActiveDays,
    Map<String, DayActivityDto> days
) {}
