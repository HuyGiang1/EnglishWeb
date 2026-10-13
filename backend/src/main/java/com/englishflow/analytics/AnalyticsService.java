package com.englishflow.analytics;

import com.englishflow.analytics.dto.DashboardSummaryResponse;
import com.englishflow.analytics.dto.RecentSessionDto;
import com.englishflow.review.SrsItemRepository;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.englishflow.vocabulary.VocabularyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AnalyticsService {

    public static final String STREAK_STATUS_STUDIED_TODAY = "ACTIVE_STUDIED_TODAY";
    public static final String STREAK_STATUS_PENDING_TODAY = "ACTIVE_PENDING_TODAY";
    public static final String STREAK_STATUS_BROKEN_RESET = "BROKEN_RESET";

    private final UserRepository userRepository;
    private final VocabularyRepository vocabularyRepository;
    private final SrsItemRepository srsItemRepository;
    private final LearningActivityEventRepository learningActivityEventRepository;
    private final Clock clock;

    public AnalyticsService(
        UserRepository userRepository,
        VocabularyRepository vocabularyRepository,
        SrsItemRepository srsItemRepository,
        LearningActivityEventRepository learningActivityEventRepository,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.vocabularyRepository = vocabularyRepository;
        this.srsItemRepository = srsItemRepository;
        this.learningActivityEventRepository = learningActivityEventRepository;
        this.clock = clock;
    }

    /**
     * Compute authoritative dashboard summary for the authenticated user.
     * Strictly read-only: does not modify state, write history, or advance streaks.
     */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        ZoneId userZone = resolveUserZone(user.getTimezone());
        LocalDate userLocalDate = LocalDate.now(clock.withZone(userZone));

        // 1. Vocabulary totals (excluding soft-deleted)
        long vocabularyTotal = vocabularyRepository.countByUserIdAndDeletedAtIsNull(userId);

        // 2. Due review count (active due SRS cards + unprovisioned active vocabularies)
        long unprovisionedCount = vocabularyRepository.countUnprovisionedVocabularies(userId);
        long dueReviewCount = srsItemRepository.countDueCards(userId, userLocalDate) + unprovisionedCount;

        // 3. Completed verified learning actions today
        long dailyGoalCompletedCount = learningActivityEventRepository.countByUserIdAndHistoricalLocalDate(userId, userLocalDate);

        // 4. Daily goal calculation
        int dailyGoalTarget = Math.max(1, user.getDailyGoalItems());
        int goalProgressPercentage = (int) Math.min(100, Math.round((dailyGoalCompletedCount * 100.0) / dailyGoalTarget));

        // 5. Streak calculation based on local calendar dates
        List<LocalDate> distinctDates = learningActivityEventRepository.findDistinctHistoricalLocalDates(userId);
        Set<LocalDate> activeDateSet = new HashSet<>(distinctDates);

        int currentStreakDays = 0;
        String streakStatus;

        if (activeDateSet.contains(userLocalDate)) {
            streakStatus = STREAK_STATUS_STUDIED_TODAY;
            currentStreakDays = 1;
            LocalDate checkDate = userLocalDate.minusDays(1);
            while (activeDateSet.contains(checkDate)) {
                currentStreakDays++;
                checkDate = checkDate.minusDays(1);
            }
        } else if (activeDateSet.contains(userLocalDate.minusDays(1))) {
            streakStatus = STREAK_STATUS_PENDING_TODAY;
            currentStreakDays = 1;
            LocalDate checkDate = userLocalDate.minusDays(2);
            while (activeDateSet.contains(checkDate)) {
                currentStreakDays++;
                checkDate = checkDate.minusDays(1);
            }
        } else {
            streakStatus = STREAK_STATUS_BROKEN_RESET;
            currentStreakDays = 0;
        }

        // 6. Recent sessions summary
        List<Object[]> sessionRows = learningActivityEventRepository.findRecentSessionSummaries(userId);
        List<RecentSessionDto> recentSessions = new ArrayList<>(sessionRows.size());
        for (Object[] row : sessionRows) {
            String type = (String) row[0];
            long count = ((Number) row[1]).longValue();
            Instant completedAt = (Instant) row[2];
            recentSessions.add(new RecentSessionDto(type, count, completedAt));
        }

        String displayName = (user.getDisplayName() != null && !user.getDisplayName().isBlank())
            ? user.getDisplayName()
            : "Learner";
        String greeting = "Welcome back, " + displayName;

        return new DashboardSummaryResponse(
            greeting,
            userLocalDate,
            vocabularyTotal,
            dueReviewCount,
            dailyGoalTarget,
            dailyGoalCompletedCount,
            goalProgressPercentage,
            currentStreakDays,
            streakStatus,
            recentSessions
        );
    }

    private ZoneId resolveUserZone(String timezoneStr) {
        if (timezoneStr == null || timezoneStr.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timezoneStr);
        } catch (Exception ex) {
            return ZoneId.of("UTC");
        }
    }
}
