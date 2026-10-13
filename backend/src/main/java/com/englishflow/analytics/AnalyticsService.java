package com.englishflow.analytics;

import com.englishflow.analytics.dto.DashboardSummaryResponse;
import com.englishflow.analytics.dto.DayActivityBreakdownDto;
import com.englishflow.analytics.dto.DayActivityDto;
import com.englishflow.analytics.dto.HeatmapResponse;
import com.englishflow.analytics.dto.RecentSessionDto;
import com.englishflow.review.SrsItemRepository;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.englishflow.vocabulary.VocabularyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Compute authoritative 52-week activity heatmap for the authenticated user.
     * Strictly read-only: derives calendar grid from immutable learning activity events.
     */
    @Transactional(readOnly = true)
    public HeatmapResponse getActivityHeatmap(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        ZoneId userZone = resolveUserZone(user.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(userZone));

        // Account creation date in user local timezone
        Instant createdAtInstant = user.getCreatedAt() != null ? user.getCreatedAt() : Instant.now(clock);
        LocalDate accountCreatedLocalDate = createdAtInstant.atZone(userZone).toLocalDate();

        // 52-Week Rolling Calendar Window aligned to ISO-8601 weeks (Monday to Sunday)
        LocalDate currentWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate currentWeekSunday = currentWeekMonday.plusDays(6);
        LocalDate startMonday = currentWeekMonday.minusWeeks(51);

        // Daily goal target
        int dailyGoalTarget = Math.max(1, user.getDailyGoalItems());

        // Single aggregation query retrieving compact daily counts by event type
        List<DailyActivityProjection> projections = learningActivityEventRepository
            .findActivityCountsByDateRange(userId, startMonday, today);

        Map<LocalDate, Map<String, Long>> dateTypeMap = new HashMap<>();
        for (DailyActivityProjection p : projections) {
            dateTypeMap.computeIfAbsent(p.getHistoricalLocalDate(), k -> new HashMap<>())
                .put(p.getEventType(), p.getActionCount());
        }

        // Generate complete ordered zero-filled calendar grid
        Map<String, DayActivityDto> daysMap = new LinkedHashMap<>();
        for (LocalDate d = startMonday; !d.isAfter(currentWeekSunday); d = d.plusDays(1)) {
            Map<String, Long> typeMap = dateTypeMap.getOrDefault(d, Collections.emptyMap());
            long flashcards = typeMap.getOrDefault(LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, 0L);
            long dictations = typeMap.getOrDefault(LearningActivityEvent.EVENT_TYPE_DICTATION_SENTENCE, 0L);
            long shadowings = typeMap.getOrDefault(LearningActivityEvent.EVENT_TYPE_SHADOWING_TAKE, 0L);
            long quizzes = typeMap.getOrDefault(LearningActivityEvent.EVENT_TYPE_QUIZ_QUESTION, 0L)
                + typeMap.getOrDefault(LearningActivityEvent.EVENT_TYPE_QUIZ_COMPLETION, 0L);
            long actionCount = flashcards + dictations + shadowings + quizzes;

            String state;
            int intensity;
            boolean goalCompleted;

            if (d.isAfter(today)) {
                state = "FUTURE_DATE";
                intensity = 0;
                goalCompleted = false;
                actionCount = 0;
            } else if (actionCount > 0) {
                state = "ACTIVE_DAY";
                intensity = computeIntensityLevel(actionCount);
                goalCompleted = actionCount >= dailyGoalTarget;
            } else if (d.isBefore(accountCreatedLocalDate)) {
                state = "PRE_REGISTRATION";
                intensity = 0;
                goalCompleted = false;
            } else {
                state = "ZERO_ACTIVITY";
                intensity = 0;
                goalCompleted = false;
            }

            DayActivityBreakdownDto breakdown = new DayActivityBreakdownDto(
                flashcards, dictations, shadowings, quizzes
            );
            daysMap.put(d.toString(), new DayActivityDto(
                d.toString(), state, actionCount, intensity, goalCompleted, breakdown
            ));
        }

        // Streak & active day calculations based on distinct historical local dates
        List<LocalDate> distinctDates = learningActivityEventRepository.findDistinctHistoricalLocalDates(userId);
        Set<LocalDate> activeDateSet = new HashSet<>(distinctDates);

        int currentStreakDays = 0;
        String streakStatus;

        if (activeDateSet.contains(today)) {
            streakStatus = STREAK_STATUS_STUDIED_TODAY;
            currentStreakDays = 1;
            LocalDate checkDate = today.minusDays(1);
            while (activeDateSet.contains(checkDate)) {
                currentStreakDays++;
                checkDate = checkDate.minusDays(1);
            }
        } else if (activeDateSet.contains(today.minusDays(1))) {
            streakStatus = STREAK_STATUS_PENDING_TODAY;
            currentStreakDays = 1;
            LocalDate checkDate = today.minusDays(2);
            while (activeDateSet.contains(checkDate)) {
                currentStreakDays++;
                checkDate = checkDate.minusDays(1);
            }
        } else {
            streakStatus = STREAK_STATUS_BROKEN_RESET;
            currentStreakDays = 0;
        }

        int totalActiveDays = distinctDates.size();
        int longestStreakDays = 0;
        if (!distinctDates.isEmpty()) {
            List<LocalDate> sortedDates = new ArrayList<>(distinctDates);
            Collections.sort(sortedDates);
            int currentRun = 0;
            LocalDate prev = null;
            for (LocalDate d : sortedDates) {
                if (prev == null) {
                    currentRun = 1;
                } else if (prev.plusDays(1).equals(d)) {
                    currentRun++;
                } else {
                    currentRun = 1;
                }
                if (currentRun > longestStreakDays) {
                    longestStreakDays = currentRun;
                }
                prev = d;
            }
        }

        return new HeatmapResponse(
            startMonday.toString(),
            today.toString(),
            user.getTimezone() != null ? user.getTimezone() : "UTC",
            createdAtInstant,
            currentStreakDays,
            streakStatus,
            longestStreakDays,
            totalActiveDays,
            daysMap
        );
    }

    private int computeIntensityLevel(long count) {
        if (count <= 0) return 0;
        if (count <= 9) return 1;
        if (count <= 19) return 2;
        if (count <= 39) return 3;
        return 4;
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
