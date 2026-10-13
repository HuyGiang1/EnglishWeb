package com.englishflow.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardSummaryResponse(
    String greeting,
    LocalDate currentDate,
    long vocabularyTotal,
    long dueReviewCount,
    int dailyGoalTarget,
    long dailyGoalCompletedCount,
    int goalProgressPercentage,
    int currentStreakDays,
    String streakStatus,
    List<RecentSessionDto> recentSessions
) {}
