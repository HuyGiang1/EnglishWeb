package com.englishflow.analytics;

import com.englishflow.analytics.dto.HeatmapResponse;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.englishflow.vocabulary.Vocabulary;
import com.englishflow.vocabulary.VocabularyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class HeatmapIntegrationTest {

    private static final String MOCK_CSRF = "heatmap-test-csrf-token";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("englishflow_test")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LearningActivityEventRepository learningActivityEventRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        learningActivityEventRepository.deleteAll();
        vocabularyRepository.deleteAll();
        userRepository.deleteAll();

        userA = new User(
            "heatmap.user.a@example.com",
            passwordEncoder.encode("Password123!"),
            "Learner A",
            "Asia/Ho_Chi_Minh",
            20
        );
        userA = userRepository.save(userA);

        userB = new User(
            "heatmap.user.b@example.com",
            passwordEncoder.encode("Password123!"),
            "Learner B",
            "America/New_York",
            10
        );
        userB = userRepository.save(userB);
    }

    @AfterEach
    void tearDown() {
        learningActivityEventRepository.deleteAll();
        vocabularyRepository.deleteAll();
        userRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    private MockHttpSession createAuthSession(User user) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            user.getEmail(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        securityContext.setAuthentication(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        return session;
    }

    private LearningActivityEvent createEvent(User user, String eventType, LocalDate localDate, int duration) {
        return new LearningActivityEvent(
            user.getId(),
            eventType,
            UUID.randomUUID(),
            Instant.now(clock),
            user.getTimezone() != null ? user.getTimezone() : "UTC",
            localDate,
            duration
        );
    }

    @Test
    @DisplayName("01. Unauthorized access to canonical and alias routes is rejected with 401")
    void testUnauthorizedAccessRejected() throws Exception {
        // Canonical route
        mockMvc.perform(get("/api/v1/analytics/activity-heatmap"))
            .andExpect(status().isUnauthorized());

        // Supported aliases
        mockMvc.perform(get("/api/v1/dashboard/activity-heatmap"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/dashboard/heatmap"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("02. Authenticated user retrieves typed activity-heatmap via canonical and alias routes")
    void testAuthenticatedUserRetrievesHeatmap() throws Exception {
        MockHttpSession session = createAuthSession(userA);

        // Canonical route
        mockMvc.perform(get("/api/v1/analytics/activity-heatmap")
                .session(session)
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userTimezone", is("Asia/Ho_Chi_Minh")))
            .andExpect(jsonPath("$.currentStreakDays", is(0)))
            .andExpect(jsonPath("$.streakStatus", is("BROKEN_RESET")))
            .andExpect(jsonPath("$.longestStreakDays", is(0)))
            .andExpect(jsonPath("$.totalActiveDays", is(0)));

        // Supported alias route: /api/v1/dashboard/activity-heatmap
        mockMvc.perform(get("/api/v1/dashboard/activity-heatmap")
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userTimezone", is("Asia/Ho_Chi_Minh")));

        // Supported alias route: /api/v1/dashboard/heatmap
        mockMvc.perform(get("/api/v1/dashboard/heatmap")
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userTimezone", is("Asia/Ho_Chi_Minh")));
    }

    @Test
    @DisplayName("03. Empty history produces zero-filled calendar grid")
    void testEmptyHistoryZeroFilledGrid() throws Exception {
        MockHttpSession session = createAuthSession(userA);

        MvcResult result = mockMvc.perform(get("/api/v1/analytics/activity-heatmap")
                .session(session))
            .andExpect(status().isOk())
            .andReturn();

        HeatmapResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), HeatmapResponse.class);
        assertThat(response.days()).isNotEmpty();
        assertThat(response.days().size()).isGreaterThanOrEqualTo(364);

        ZoneId zone = ZoneId.of(userA.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        var todayEntry = response.days().get(today.toString());
        assertThat(todayEntry).isNotNull();
        assertThat(todayEntry.actionCount()).isEqualTo(0);
        assertThat(todayEntry.intensityLevel()).isEqualTo(0);
        assertThat(todayEntry.state()).isEqualTo("ZERO_ACTIVITY");
        assertThat(todayEntry.goalCompleted()).isFalse();
    }

    @Test
    @DisplayName("04. Single activity today updates actionCount, intensity 1, and active streak")
    void testSingleActivityToday() throws Exception {
        ZoneId zone = ZoneId.of(userA.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));

        learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, today, 15));

        MockHttpSession session = createAuthSession(userA);
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/activity-heatmap").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreakDays", is(1)))
            .andExpect(jsonPath("$.streakStatus", is("ACTIVE_STUDIED_TODAY")))
            .andExpect(jsonPath("$.totalActiveDays", is(1)))
            .andExpect(jsonPath("$.longestStreakDays", is(1)))
            .andReturn();

        HeatmapResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), HeatmapResponse.class);
        var todayEntry = response.days().get(today.toString());
        assertThat(todayEntry.actionCount()).isEqualTo(1);
        assertThat(todayEntry.intensityLevel()).isEqualTo(1);
        assertThat(todayEntry.state()).isEqualTo("ACTIVE_DAY");
        assertThat(todayEntry.breakdown().flashcardReviews()).isEqualTo(1);
    }

    @Test
    @DisplayName("05. Multiple activities on one day aggregate all modalities correctly")
    void testMultipleActivitiesOnOneDay() throws Exception {
        ZoneId zone = ZoneId.of(userA.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));

        // 12 Flashcards, 3 Dictations, 2 Shadowings, 1 Quiz = 18 total actions (Level 2: 10-19)
        for (int i = 0; i < 12; i++) {
            learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, today, 10));
        }
        for (int i = 0; i < 3; i++) {
            learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_DICTATION_SENTENCE, today, 25));
        }
        for (int i = 0; i < 2; i++) {
            learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_SHADOWING_TAKE, today, 30));
        }
        learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_QUIZ_QUESTION, today, 60));

        MockHttpSession session = createAuthSession(userA);
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/activity-heatmap").session(session))
            .andExpect(status().isOk())
            .andReturn();

        HeatmapResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), HeatmapResponse.class);
        var todayEntry = response.days().get(today.toString());
        assertThat(todayEntry.actionCount()).isEqualTo(18);
        assertThat(todayEntry.intensityLevel()).isEqualTo(2); // 10–19 actions is Level 2
        assertThat(todayEntry.breakdown().flashcardReviews()).isEqualTo(12);
        assertThat(todayEntry.breakdown().dictationExercises()).isEqualTo(3);
        assertThat(todayEntry.breakdown().shadowingTakes()).isEqualTo(2);
        assertThat(todayEntry.breakdown().quizQuestions()).isEqualTo(1);
        assertThat(todayEntry.breakdown().quizzesCompleted()).isEqualTo(1);
        // User A target is 20, 18 actions -> goalCompleted = false
        assertThat(todayEntry.goalCompleted()).isFalse();
    }

    @Test
    @DisplayName("06. Daily goal completion threshold marks goalCompleted true")
    void testDailyGoalCompletionThreshold() throws Exception {
        ZoneId zone = ZoneId.of(userA.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));

        // User A daily target is 20. Log 20 reviews.
        for (int i = 0; i < 20; i++) {
            learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, today, 10));
        }

        MockHttpSession session = createAuthSession(userA);
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/activity-heatmap").session(session))
            .andExpect(status().isOk())
            .andReturn();

        HeatmapResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), HeatmapResponse.class);
        var todayEntry = response.days().get(today.toString());
        assertThat(todayEntry.actionCount()).isEqualTo(20);
        assertThat(todayEntry.intensityLevel()).isEqualTo(3); // 20–39 actions is Level 3
        assertThat(todayEntry.goalCompleted()).isTrue();
    }

    @Test
    @DisplayName("07. Intensity thresholds: 0, 1..9 (L1), 10..19 (L2), 20..39 (L3), 40+ (L4)")
    void testIntensityThresholds() throws Exception {
        ZoneId zone = ZoneId.of(userA.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));

        // Day -4: 1 action -> Level 1
        learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, today.minusDays(4), 10));
        // Day -3: 10 actions -> Level 2
        for (int i = 0; i < 10; i++) {
            learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, today.minusDays(3), 10));
        }
        // Day -2: 25 actions -> Level 3
        for (int i = 0; i < 25; i++) {
            learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, today.minusDays(2), 10));
        }
        // Day -1: 42 actions -> Level 4
        for (int i = 0; i < 42; i++) {
            learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, today.minusDays(1), 10));
        }

        MockHttpSession session = createAuthSession(userA);
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/activity-heatmap").session(session))
            .andExpect(status().isOk())
            .andReturn();

        HeatmapResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), HeatmapResponse.class);
        assertThat(response.days().get(today.minusDays(4).toString()).intensityLevel()).isEqualTo(1);
        assertThat(response.days().get(today.minusDays(3).toString()).intensityLevel()).isEqualTo(2);
        assertThat(response.days().get(today.minusDays(2).toString()).intensityLevel()).isEqualTo(3);
        assertThat(response.days().get(today.minusDays(1).toString()).intensityLevel()).isEqualTo(4);
    }

    @Test
    @DisplayName("08. Multi-day activity tracks current and longest streaks across historical gaps")
    void testLongestAndCurrentStreaks() throws Exception {
        ZoneId zone = ZoneId.of(userA.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));

        // Gap 1: 5-day streak 20 days ago (days -24 to -20)
        for (int i = 24; i >= 20; i--) {
            learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, today.minusDays(i), 10));
        }

        // Current streak: 2 days (yesterday and today)
        learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, today.minusDays(1), 10));
        learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, today, 10));

        MockHttpSession session = createAuthSession(userA);
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/activity-heatmap").session(session))
            .andExpect(status().isOk())
            .andReturn();

        HeatmapResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), HeatmapResponse.class);
        assertThat(response.currentStreakDays()).isEqualTo(2);
        assertThat(response.streakStatus()).isEqualTo("ACTIVE_STUDIED_TODAY");
        assertThat(response.longestStreakDays()).isEqualTo(5); // Historical 5-day streak is longest
        assertThat(response.totalActiveDays()).isEqualTo(7);   // 5 + 2 = 7 days
    }

    @Test
    @DisplayName("09. Events older than 52-week window are excluded from calendar counts but included in totalActiveDays")
    void testEventsOlderThan52Weeks() throws Exception {
        ZoneId zone = ZoneId.of(userA.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate oldDate = today.minusYears(2); // 2 years ago

        learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, oldDate, 10));
        learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, today, 10));

        MockHttpSession session = createAuthSession(userA);
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/activity-heatmap").session(session))
            .andExpect(status().isOk())
            .andReturn();

        HeatmapResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), HeatmapResponse.class);
        assertThat(response.totalActiveDays()).isEqualTo(2);
        assertThat(response.days().get(oldDate.toString())).isNull(); // Not within 52-week window
        assertThat(response.days().get(today.toString()).actionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("10. ISO-8601 calendar alignment: startDate is guaranteed to be a Monday")
    void testCalendarAlignmentStartsOnMonday() throws Exception {
        MockHttpSession session = createAuthSession(userA);
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/activity-heatmap").session(session))
            .andExpect(status().isOk())
            .andReturn();

        HeatmapResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), HeatmapResponse.class);
        LocalDate start = LocalDate.parse(response.startDate());
        assertThat(start.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    @DisplayName("11. Future dates padded to end of current week have FUTURE_DATE state and 0 count")
    void testFuturePaddingState() throws Exception {
        ZoneId zone = ZoneId.of(userA.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate currentWeekSunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(6);

        MockHttpSession session = createAuthSession(userA);
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/activity-heatmap").session(session))
            .andExpect(status().isOk())
            .andReturn();

        HeatmapResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), HeatmapResponse.class);
        if (today.isBefore(currentWeekSunday)) {
            LocalDate futureDay = today.plusDays(1);
            var futureEntry = response.days().get(futureDay.toString());
            assertThat(futureEntry).isNotNull();
            assertThat(futureEntry.state()).isEqualTo("FUTURE_DATE");
            assertThat(futureEntry.actionCount()).isEqualTo(0);
            assertThat(futureEntry.intensityLevel()).isEqualTo(0);
            assertThat(futureEntry.goalCompleted()).isFalse();
        }
    }

    @Test
    @DisplayName("12. Frozen historical local date integrity across month and year boundaries")
    void testYearBoundaryAndFrozenLocalDate() throws Exception {
        // User studies on Dec 31 and Jan 1
        LocalDate dec31 = LocalDate.of(2025, 12, 31);
        LocalDate jan1 = LocalDate.of(2026, 1, 1);

        learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, dec31, 10));
        learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_DICTATION_SENTENCE, jan1, 15));

        MockHttpSession session = createAuthSession(userA);
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/activity-heatmap").session(session))
            .andExpect(status().isOk())
            .andReturn();

        HeatmapResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), HeatmapResponse.class);
        if (response.days().containsKey(dec31.toString())) {
            assertThat(response.days().get(dec31.toString()).actionCount()).isEqualTo(1);
            assertThat(response.days().get(dec31.toString()).state()).isEqualTo("ACTIVE_DAY");
        }
        if (response.days().containsKey(jan1.toString())) {
            assertThat(response.days().get(jan1.toString()).actionCount()).isEqualTo(1);
            assertThat(response.days().get(jan1.toString()).state()).isEqualTo("ACTIVE_DAY");
        }
    }

    @Test
    @DisplayName("13. Cross-user isolation: User B cannot see User A's activity events")
    void testCrossUserIsolation() throws Exception {
        ZoneId zoneA = ZoneId.of(userA.getTimezone());
        LocalDate todayA = LocalDate.now(clock.withZone(zoneA));

        // User A completes 5 activities
        for (int i = 0; i < 5; i++) {
            learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, todayA, 10));
        }

        // Query as User B
        MockHttpSession sessionB = createAuthSession(userB);
        MvcResult resultB = mockMvc.perform(get("/api/v1/analytics/activity-heatmap").session(sessionB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreakDays", is(0)))
            .andExpect(jsonPath("$.totalActiveDays", is(0)))
            .andReturn();

        HeatmapResponse responseB = objectMapper.readValue(resultB.getResponse().getContentAsString(), HeatmapResponse.class);
        ZoneId zoneB = ZoneId.of(userB.getTimezone());
        LocalDate todayB = LocalDate.now(clock.withZone(zoneB));
        assertThat(responseB.days().get(todayB.toString()).actionCount()).isEqualTo(0);
        assertThat(responseB.totalActiveDays()).isEqualTo(0);
    }

    @Test
    @DisplayName("14. Adding vocabulary words creates zero learning activity events")
    void testVocabularyAdditionCreatesZeroEvents() throws Exception {
        Vocabulary v = new Vocabulary(
            userA.getId(),
            "resilient",
            "adjective",
            "/rɪˈzɪl.jənt/",
            "kiên cường",
            "able to quickly recover from difficulties",
            "She is resilient under pressure.",
            "Cô ấy kiên cường dưới áp lực.",
            "Notes",
            "TEST",
            "B2"
        );
        vocabularyRepository.save(v);

        // Events count must still be 0
        assertThat(learningActivityEventRepository.count()).isEqualTo(0);

        MockHttpSession session = createAuthSession(userA);
        mockMvc.perform(get("/api/v1/analytics/activity-heatmap").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalActiveDays", is(0)));
    }

    @Test
    @DisplayName("15. Heatmap action count on today matches Dashboard dailyGoalCompletedCount")
    void testHeatmapAndDashboardSummaryAgreement() throws Exception {
        ZoneId zone = ZoneId.of(userA.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));

        for (int i = 0; i < 7; i++) {
            learningActivityEventRepository.save(createEvent(userA, LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, today, 10));
        }

        MockHttpSession session = createAuthSession(userA);

        // Fetch Dashboard Summary
        MvcResult dashResult = mockMvc.perform(get("/api/v1/dashboard/summary").session(session))
            .andExpect(status().isOk())
            .andReturn();
        var dashResponse = objectMapper.readValue(dashResult.getResponse().getContentAsString(), com.englishflow.analytics.dto.DashboardSummaryResponse.class);

        // Fetch Heatmap
        MvcResult heatResult = mockMvc.perform(get("/api/v1/analytics/activity-heatmap").session(session))
            .andExpect(status().isOk())
            .andReturn();
        HeatmapResponse heatResponse = objectMapper.readValue(heatResult.getResponse().getContentAsString(), HeatmapResponse.class);

        assertThat(heatResponse.days().get(today.toString()).actionCount())
            .isEqualTo(dashResponse.dailyGoalCompletedCount());
        assertThat(heatResponse.currentStreakDays())
            .isEqualTo(dashResponse.currentStreakDays());
        assertThat(heatResponse.streakStatus())
            .isEqualTo(dashResponse.streakStatus());
    }
}
