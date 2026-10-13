package com.englishflow.analytics;

import com.englishflow.analytics.dto.DashboardSummaryResponse;
import com.englishflow.review.ReviewService;
import com.englishflow.review.SrsItem;
import com.englishflow.review.SrsItemRepository;
import com.englishflow.review.dto.ReviewSubmissionRequest;
import com.englishflow.review.scheduler.ReviewRating;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.englishflow.vocabulary.Vocabulary;
import com.englishflow.vocabulary.VocabularyRepository;
import com.englishflow.vocabulary.VocabularyService;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DashboardIntegrationTest {

    private static final String MOCK_CSRF = "dashboard-test-csrf-token";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private VocabularyService vocabularyService;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private SrsItemRepository srsItemRepository;

    @Autowired
    private LearningActivityEventRepository learningActivityEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    private User userA;
    private User userB;
    private MockHttpSession sessionA;
    private MockHttpSession sessionB;

    @BeforeEach
    void setUp() {
        cleanUpAll();

        userA = userRepository.save(new User(
            "dashboard.a@example.com",
            passwordEncoder.encode("Password123!"),
            "Alice Learner",
            "Asia/Ho_Chi_Minh",
            20
        ));

        userB = userRepository.save(new User(
            "dashboard.b@example.com",
            passwordEncoder.encode("Password123!"),
            "Bob Scholar",
            "America/New_York",
            10
        ));

        sessionA = createSessionFor(userA);
        sessionB = createSessionFor(userB);
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

    private MockHttpSession createSessionFor(User user) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            user.getEmail(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        context.setAuthentication(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private Vocabulary createVocab(User user, String term) {
        Vocabulary v = new Vocabulary(
            user.getId(),
            term,
            "noun",
            "/ipa/",
            "nghĩa " + term,
            "def " + term,
            "ex " + term,
            null,
            null,
            null,
            "B2"
        );
        Vocabulary saved = vocabularyRepository.save(v);
        reviewService.provisionCardForVocabulary(user.getId(), saved.getId(), null);
        return saved;
    }

    @Test
    @DisplayName("Dashboard summary requires authentication (401 when anonymous)")
    void testDashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("New user has authoritative empty metrics (0 vocab, 0 due, 0 actions, 0 streak)")
    void testNewUserHasCorrectEmptyMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.greeting").value("Welcome back, Alice Learner"))
            .andExpect(jsonPath("$.vocabularyTotal").value(0))
            .andExpect(jsonPath("$.dueReviewCount").value(0))
            .andExpect(jsonPath("$.dailyGoalTarget").value(20))
            .andExpect(jsonPath("$.dailyGoalCompletedCount").value(0))
            .andExpect(jsonPath("$.goalProgressPercentage").value(0))
            .andExpect(jsonPath("$.currentStreakDays").value(0))
            .andExpect(jsonPath("$.streakStatus").value("BROKEN_RESET"))
            .andExpect(jsonPath("$.recentSessions.length()").value(0));
    }

    @Test
    @DisplayName("User A cannot access User B's metrics; metrics remain strictly multi-tenant isolated")
    void testUserCannotAccessAnotherUsersAnalytics() throws Exception {
        createVocab(userA, "serendipity");
        createVocab(userA, "perseverance");
        createVocab(userB, "lucid");

        // User A sees 2 vocab
        mockMvc.perform(get("/api/v1/dashboard/summary").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vocabularyTotal").value(2))
            .andExpect(jsonPath("$.dueReviewCount").value(2))
            .andExpect(jsonPath("$.greeting").value("Welcome back, Alice Learner"));

        // User B sees 1 vocab
        mockMvc.perform(get("/api/v1/dashboard/summary").session(sessionB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vocabularyTotal").value(1))
            .andExpect(jsonPath("$.dueReviewCount").value(1))
            .andExpect(jsonPath("$.greeting").value("Welcome back, Bob Scholar"));
    }

    @Test
    @DisplayName("Vocabulary counts exclude soft-deleted entries")
    void testVocabularyCountsExcludeSoftDeletedEntries() throws Exception {
        Vocabulary v1 = createVocab(userA, "active-one");
        Vocabulary v2 = createVocab(userA, "active-two");

        mockMvc.perform(get("/api/v1/dashboard/summary").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vocabularyTotal").value(2));

        // Soft-delete v1
        vocabularyService.deleteVocabulary(userA.getId(), v1.getId());

        mockMvc.perform(get("/api/v1/dashboard/summary").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vocabularyTotal").value(1));
    }

    @Test
    @DisplayName("Due counts exclude suspended cards and soft-deleted vocabulary")
    void testDueCountsExcludeSuspendedAndDeletedCards() throws Exception {
        Vocabulary v1 = createVocab(userA, "card-a");
        Vocabulary v2 = createVocab(userA, "card-b");

        mockMvc.perform(get("/api/v1/dashboard/summary").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dueReviewCount").value(2));

        // Soft-delete v2 (which also suspends its card)
        vocabularyService.deleteVocabulary(userA.getId(), v2.getId());

        mockMvc.perform(get("/api/v1/dashboard/summary").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dueReviewCount").value(1));
    }

    @Test
    @DisplayName("Valid flashcard activity increments today's completed count and updates daily goal progress")
    void testValidFlashcardActivityIncrementsTodayCount() throws Exception {
        Vocabulary v = createVocab(userA, "resilience");
        SrsItem item = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v.getId()).orElseThrow();

        // Initially 0 actions today
        mockMvc.perform(get("/api/v1/dashboard/summary").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyGoalCompletedCount").value(0))
            .andExpect(jsonPath("$.goalProgressPercentage").value(0));

        // Submit review
        ReviewSubmissionRequest req = new ReviewSubmissionRequest(item.getId(), UUID.randomUUID(), ReviewRating.GOOD, 8);
        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk());

        // Now 1 action completed today, progress = 5% (1 / 20)
        mockMvc.perform(get("/api/v1/dashboard/summary").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyGoalCompletedCount").value(1))
            .andExpect(jsonPath("$.goalProgressPercentage").value(5))
            .andExpect(jsonPath("$.currentStreakDays").value(1))
            .andExpect(jsonPath("$.streakStatus").value("ACTIVE_STUDIED_TODAY"));
    }

    @Test
    @DisplayName("Exact attempt retry does NOT increment today's completed count")
    void testExactAttemptRetryDoesNotIncrementCount() throws Exception {
        Vocabulary v = createVocab(userA, "tenacity");
        SrsItem item = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v.getId()).orElseThrow();

        UUID attemptId = UUID.randomUUID();
        ReviewSubmissionRequest req = new ReviewSubmissionRequest(item.getId(), attemptId, ReviewRating.GOOD, 10);

        // Grade card
        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk());

        // Retry exact same attempt
        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk());

        // Count must still be exactly 1
        mockMvc.perform(get("/api/v1/dashboard/summary").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyGoalCompletedCount").value(1));
    }

    @Test
    @DisplayName("Goal completion caps percentage at 100% while preserving true completed count")
    void testGoalCompletionHandlesAtAndAboveTarget() throws Exception {
        // User B has daily goal target of 10
        LocalDate todayB = LocalDate.now(clock.withZone(ZoneId.of(userB.getTimezone())));

        // Record 12 events for User B today
        for (int i = 0; i < 12; i++) {
            learningActivityEventRepository.save(new LearningActivityEvent(
                userB.getId(),
                LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW,
                UUID.randomUUID(),
                Instant.now(clock),
                userB.getTimezone(),
                todayB,
                5
            ));
        }

        mockMvc.perform(get("/api/v1/dashboard/summary").session(sessionB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyGoalTarget").value(10))
            .andExpect(jsonPath("$.dailyGoalCompletedCount").value(12))
            .andExpect(jsonPath("$.goalProgressPercentage").value(100))
            .andExpect(jsonPath("$.currentStreakDays").value(1))
            .andExpect(jsonPath("$.streakStatus").value("ACTIVE_STUDIED_TODAY"));
    }

    @Test
    @DisplayName("Consecutive-day streak works across 3 consecutive days")
    void testConsecutiveDayStreakWorks() throws Exception {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(userA.getTimezone())));
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);

        learningActivityEventRepository.save(new LearningActivityEvent(
            userA.getId(), LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, UUID.randomUUID(), Instant.now(clock), userA.getTimezone(), twoDaysAgo, 10
        ));
        learningActivityEventRepository.save(new LearningActivityEvent(
            userA.getId(), LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, UUID.randomUUID(), Instant.now(clock), userA.getTimezone(), yesterday, 10
        ));
        learningActivityEventRepository.save(new LearningActivityEvent(
            userA.getId(), LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, UUID.randomUUID(), Instant.now(clock), userA.getTimezone(), today, 10
        ));

        mockMvc.perform(get("/api/v1/dashboard/summary").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreakDays").value(3))
            .andExpect(jsonPath("$.streakStatus").value("ACTIVE_STUDIED_TODAY"));
    }

    @Test
    @DisplayName("Yesterday-only activity preserves streak pending today (ACTIVE_PENDING_TODAY)")
    void testYesterdayOnlyActivityPreservesStreakPendingToday() throws Exception {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(userA.getTimezone())));
        LocalDate yesterday = today.minusDays(1);

        learningActivityEventRepository.save(new LearningActivityEvent(
            userA.getId(), LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, UUID.randomUUID(), Instant.now(clock), userA.getTimezone(), yesterday, 15
        ));

        mockMvc.perform(get("/api/v1/dashboard/summary").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreakDays").value(1))
            .andExpect(jsonPath("$.streakStatus").value("ACTIVE_PENDING_TODAY"));
    }

    @Test
    @DisplayName("Missed day breaks streak back to 0 (BROKEN_RESET)")
    void testMissingDaysBreakStreak() throws Exception {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(userA.getTimezone())));
        LocalDate twoDaysAgo = today.minusDays(2); // Skipped yesterday!

        learningActivityEventRepository.save(new LearningActivityEvent(
            userA.getId(), LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, UUID.randomUUID(), Instant.now(clock), userA.getTimezone(), twoDaysAgo, 15
        ));

        mockMvc.perform(get("/api/v1/dashboard/summary").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreakDays").value(0))
            .andExpect(jsonPath("$.streakStatus").value("BROKEN_RESET"));
    }

    @Test
    @DisplayName("Multiple events on one day count correctly in daily total and as 1 streak day")
    void testMultipleEventsOnOneDayCountCorrectly() throws Exception {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(userA.getTimezone())));

        for (int i = 0; i < 5; i++) {
            learningActivityEventRepository.save(new LearningActivityEvent(
                userA.getId(), LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW, UUID.randomUUID(), Instant.now(clock), userA.getTimezone(), today, 8
            ));
        }

        mockMvc.perform(get("/api/v1/dashboard/summary").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyGoalCompletedCount").value(5))
            .andExpect(jsonPath("$.currentStreakDays").value(1));
    }

    @Test
    @DisplayName("Dashboard GET is strictly read-only and never creates activity events or mutates state")
    void testDashboardGetDoesNotWriteNewActivityRecords() throws Exception {
        long eventsBefore = learningActivityEventRepository.count();
        long vocabBefore = vocabularyRepository.count();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/dashboard/summary").session(sessionA))
                .andExpect(status().isOk());
        }

        assertThat(learningActivityEventRepository.count()).isEqualTo(eventsBefore);
        assertThat(vocabularyRepository.count()).isEqualTo(vocabBefore);
    }
}
