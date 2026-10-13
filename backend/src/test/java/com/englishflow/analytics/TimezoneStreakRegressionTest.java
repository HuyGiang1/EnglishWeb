package com.englishflow.analytics;

import com.englishflow.analytics.dto.DashboardSummaryResponse;
import com.englishflow.review.SrsItem;
import com.englishflow.review.SrsItemRepository;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.englishflow.vocabulary.Vocabulary;
import com.englishflow.vocabulary.VocabularyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TimezoneStreakRegressionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("englishflow_test")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private SrsItemRepository srsItemRepository;

    @Autowired
    private LearningActivityEventRepository learningActivityEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        cleanUpAll();
    }

    @AfterEach
    void tearDown() {
        cleanUpAll();
    }

    private void cleanUpAll() {
        learningActivityEventRepository.deleteAll();
        srsItemRepository.deleteAll();
        vocabularyRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createUser(String email, String timezone, int goal) {
        return userRepository.save(new User(
            email,
            passwordEncoder.encode("Password123!"),
            "Test Learner",
            timezone,
            goal
        ));
    }

    @Test
    @DisplayName("UTC boundaries: Correct local dates resolved across Tokyo (+9) and New York (-4)")
    void testUtcDateBoundariesAcrossTimezones() {
        // Instant: 2026-10-13 23:30:00 UTC
        Instant lateNightUtc = Instant.parse("2026-10-13T23:30:00Z");
        Clock fixedClockLate = Clock.fixed(lateNightUtc, ZoneId.of("UTC"));

        AnalyticsService analyticsService = new AnalyticsService(
            userRepository, vocabularyRepository, srsItemRepository, learningActivityEventRepository, fixedClockLate
        );

        User tokyoUser = createUser("tokyo@example.com", "Asia/Tokyo", 20);
        User nyUser = createUser("newyork@example.com", "America/New_York", 20);

        // In Tokyo (+9), 23:30 UTC is already 2026-10-14 08:30:00
        DashboardSummaryResponse tokyoSummary = analyticsService.getDashboardSummary(tokyoUser.getId());
        assertThat(tokyoSummary.currentDate()).isEqualTo(LocalDate.of(2026, 10, 14));

        // In New York (-4 EDT), 23:30 UTC is 2026-10-13 19:30:00
        DashboardSummaryResponse nySummary = analyticsService.getDashboardSummary(nyUser.getId());
        assertThat(nySummary.currentDate()).isEqualTo(LocalDate.of(2026, 10, 13));

        // Next instant: 2026-10-14 02:30:00 UTC
        Instant earlyMorningUtc = Instant.parse("2026-10-14T02:30:00Z");
        Clock fixedClockEarly = Clock.fixed(earlyMorningUtc, ZoneId.of("UTC"));
        AnalyticsService earlyAnalytics = new AnalyticsService(
            userRepository, vocabularyRepository, srsItemRepository, learningActivityEventRepository, fixedClockEarly
        );

        // In New York, 02:30 UTC is 2026-10-13 22:30:00 (still Oct 13 before midnight)
        DashboardSummaryResponse nyEarlySummary = earlyAnalytics.getDashboardSummary(nyUser.getId());
        assertThat(nyEarlySummary.currentDate()).isEqualTo(LocalDate.of(2026, 10, 13));
    }

    @Test
    @DisplayName("DST Spring Forward (23-hour day) preserves 3-day consecutive calendar streak")
    void testDstSpringForwardTransitionPreservesStreak() {
        // America/New_York springs forward on Sunday, March 8, 2026 (clock skips 02:00 -> 03:00)
        User nyUser = createUser("dst.spring@example.com", "America/New_York", 10);

        LocalDate preDst = LocalDate.of(2026, 3, 7);
        LocalDate dstDay = LocalDate.of(2026, 3, 8); // 23-hour day
        LocalDate postDst = LocalDate.of(2026, 3, 9);

        // Record activity on all 3 consecutive calendar days
        Instant inst1 = Instant.parse("2026-03-07T18:00:00Z"); // March 7 in NY
        Instant inst2 = Instant.parse("2026-03-08T18:00:00Z"); // March 8 in NY (DST transition day)
        Instant inst3 = Instant.parse("2026-03-09T18:00:00Z"); // March 9 in NY

        learningActivityEventRepository.save(new LearningActivityEvent(
            nyUser.getId(), LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, UUID.randomUUID(), inst1, "America/New_York", preDst, 10
        ));
        learningActivityEventRepository.save(new LearningActivityEvent(
            nyUser.getId(), LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, UUID.randomUUID(), inst2, "America/New_York", dstDay, 10
        ));
        learningActivityEventRepository.save(new LearningActivityEvent(
            nyUser.getId(), LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, UUID.randomUUID(), inst3, "America/New_York", postDst, 10
        ));

        // Evaluate dashboard at postDst (March 9, 2026 at 20:00 UTC / 16:00 EDT)
        Clock postDstClock = Clock.fixed(Instant.parse("2026-03-09T20:00:00Z"), ZoneId.of("UTC"));
        AnalyticsService analytics = new AnalyticsService(
            userRepository, vocabularyRepository, srsItemRepository, learningActivityEventRepository, postDstClock
        );

        DashboardSummaryResponse summary = analytics.getDashboardSummary(nyUser.getId());
        assertThat(summary.currentDate()).isEqualTo(postDst);
        assertThat(summary.currentStreakDays()).isEqualTo(3);
        assertThat(summary.streakStatus()).isEqualTo("ACTIVE_STUDIED_TODAY");
    }

    @Test
    @DisplayName("DST Fall Back (25-hour day) preserves 3-day consecutive calendar streak")
    void testDstFallBackTransitionPreservesStreak() {
        // America/New_York falls back on Sunday, November 1, 2026 (clock repeats 01:00 -> 01:00, 25 hours)
        User nyUser = createUser("dst.fall@example.com", "America/New_York", 10);

        LocalDate preFall = LocalDate.of(2026, 10, 31);
        LocalDate fallDay = LocalDate.of(2026, 11, 1); // 25-hour day
        LocalDate postFall = LocalDate.of(2026, 11, 2);

        Instant inst1 = Instant.parse("2026-10-31T18:00:00Z");
        Instant inst2 = Instant.parse("2026-11-01T18:00:00Z");
        Instant inst3 = Instant.parse("2026-11-02T18:00:00Z");

        learningActivityEventRepository.save(new LearningActivityEvent(
            nyUser.getId(), LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, UUID.randomUUID(), inst1, "America/New_York", preFall, 12
        ));
        learningActivityEventRepository.save(new LearningActivityEvent(
            nyUser.getId(), LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, UUID.randomUUID(), inst2, "America/New_York", fallDay, 12
        ));
        learningActivityEventRepository.save(new LearningActivityEvent(
            nyUser.getId(), LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, UUID.randomUUID(), inst3, "America/New_York", postFall, 12
        ));

        Clock postFallClock = Clock.fixed(Instant.parse("2026-11-02T20:00:00Z"), ZoneId.of("UTC"));
        AnalyticsService analytics = new AnalyticsService(
            userRepository, vocabularyRepository, srsItemRepository, learningActivityEventRepository, postFallClock
        );

        DashboardSummaryResponse summary = analytics.getDashboardSummary(nyUser.getId());
        assertThat(summary.currentDate()).isEqualTo(postFall);
        assertThat(summary.currentStreakDays()).isEqualTo(3);
        assertThat(summary.streakStatus()).isEqualTo("ACTIVE_STUDIED_TODAY");
    }

    @Test
    @DisplayName("Historical local dates remain immutable after user changes timezone")
    void testHistoricalLocalDatesRemainImmutableAfterTimezoneChange() {
        User user = createUser("traveler@example.com", "America/New_York", 20);

        // User studied in New York on Oct 12
        LocalDate nyDate = LocalDate.of(2026, 10, 12);
        UUID attemptId = UUID.randomUUID();
        LearningActivityEvent saved = learningActivityEventRepository.save(new LearningActivityEvent(
            user.getId(),
            LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW,
            attemptId,
            Instant.parse("2026-10-12T20:00:00Z"),
            "America/New_York",
            nyDate,
            15
        ));

        // User relocates to Asia/Ho_Chi_Minh and updates profile timezone
        user.setTimezone("Asia/Ho_Chi_Minh");
        userRepository.save(user);

        // Verify the database record's historical_local_date is strictly unchanged
        LearningActivityEvent fetched = learningActivityEventRepository.findById(saved.getId()).orElseThrow();
        assertThat(fetched.getHistoricalLocalDate()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(fetched.getTimezoneAtEvent()).isEqualTo("America/New_York");

        // When evaluating on Oct 13 in Vietnam, yesterday's NY activity preserves streak pending today
        Clock vietnamClock = Clock.fixed(Instant.parse("2026-10-13T03:00:00Z"), ZoneId.of("UTC")); // Oct 13 10:00 AM in Vietnam
        AnalyticsService analytics = new AnalyticsService(
            userRepository, vocabularyRepository, srsItemRepository, learningActivityEventRepository, vietnamClock
        );

        DashboardSummaryResponse summary = analytics.getDashboardSummary(user.getId());
        assertThat(summary.currentDate()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(summary.currentStreakDays()).isEqualTo(1);
        assertThat(summary.streakStatus()).isEqualTo("ACTIVE_PENDING_TODAY");
    }

    @Test
    @DisplayName("Missing day breaks streak to 0 (BROKEN_RESET)")
    void testMissingDayBreaksStreak() {
        User user = createUser("gap@example.com", "UTC", 20);
        LocalDate twoDaysAgo = LocalDate.of(2026, 10, 10);
        LocalDate today = LocalDate.of(2026, 10, 12);

        learningActivityEventRepository.save(new LearningActivityEvent(
            user.getId(), LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, UUID.randomUUID(),
            Instant.parse("2026-10-10T12:00:00Z"), "UTC", twoDaysAgo, 10
        ));

        Clock clockToday = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneId.of("UTC"));
        AnalyticsService analytics = new AnalyticsService(
            userRepository, vocabularyRepository, srsItemRepository, learningActivityEventRepository, clockToday
        );

        DashboardSummaryResponse summary = analytics.getDashboardSummary(user.getId());
        assertThat(summary.currentDate()).isEqualTo(today);
        assertThat(summary.currentStreakDays()).isEqualTo(0);
        assertThat(summary.streakStatus()).isEqualTo("BROKEN_RESET");
    }

    @Test
    @DisplayName("Unrelated unique constraint violation on srs_items (uq_srs_user_vocab) raises DataIntegrityViolationException")
    void testUnrelatedConstraintViolationDoesNotMapToIdempotencyConflict() {
        User user = createUser("constraint@example.com", "UTC", 20);
        Vocabulary vocab = vocabularyRepository.save(new Vocabulary(
            user.getId(), "test", "noun", "/t/", "nghia", "def", "ex", null, null, null, "B1"
        ));

        // Create first SRS item for (user, vocab)
        srsItemRepository.save(new SrsItem(user.getId(), vocab.getId(), LocalDate.of(2026, 10, 13)));

        // Attempting to directly insert a second SRS item with same (user_id, vocabulary_id) violates uq_srs_user_vocab
        SrsItem duplicateItem = new SrsItem(user.getId(), vocab.getId(), LocalDate.of(2026, 10, 13));
        assertThatThrownBy(() -> srsItemRepository.saveAndFlush(duplicateItem))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("uq_srs_user_vocab");
    }
}
