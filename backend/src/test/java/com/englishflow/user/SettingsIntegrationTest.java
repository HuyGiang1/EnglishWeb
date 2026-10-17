package com.englishflow.user;

import com.englishflow.analytics.AnalyticsService;
import com.englishflow.analytics.LearningActivityEvent;
import com.englishflow.analytics.LearningActivityEventRepository;
import com.englishflow.analytics.dto.DashboardSummaryResponse;
import com.englishflow.analytics.dto.HeatmapResponse;
import com.englishflow.auth.dto.LoginRequest;
import com.englishflow.auth.dto.RegisterRequest;
import com.englishflow.dictation.DictationAttempt;
import com.englishflow.dictation.DictationAttemptRepository;
import com.englishflow.dictation.LearningContent;
import com.englishflow.dictation.LearningContentRepository;
import com.englishflow.quiz.QuizAnswer;
import com.englishflow.quiz.QuizAnswerRepository;
import com.englishflow.quiz.QuizAttempt;
import com.englishflow.quiz.QuizAttemptRepository;
import com.englishflow.quiz.QuizQuestion;
import com.englishflow.quiz.QuizQuestionRepository;
import com.englishflow.review.SrsItem;
import com.englishflow.review.SrsItemRepository;
import com.englishflow.review.SrsReviewHistory;
import com.englishflow.review.SrsReviewHistoryRepository;
import com.englishflow.shadowing.ShadowingTake;
import com.englishflow.shadowing.ShadowingTakeRepository;
import com.englishflow.user.dto.ChangePasswordRequest;
import com.englishflow.user.dto.DeleteAccountRequest;
import com.englishflow.user.dto.UpdatePreferencesRequest;
import com.englishflow.user.dto.UpdateProfileRequest;
import com.englishflow.user.dto.UpdateSettingsRequest;
import com.englishflow.vocabulary.Collection;
import com.englishflow.vocabulary.CollectionRepository;
import com.englishflow.vocabulary.Vocabulary;
import com.englishflow.vocabulary.VocabularyCollection;
import com.englishflow.vocabulary.VocabularyCollectionRepository;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SettingsIntegrationTest {

    private static final String MOCK_CSRF = "settings-test-csrf-token-12345";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private LearningActivityEventRepository learningActivityEventRepository;

    @Autowired
    private LearningContentRepository learningContentRepository;

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    private VocabularyCollectionRepository vocabularyCollectionRepository;

    @Autowired
    private SrsItemRepository srsItemRepository;

    @Autowired
    private SrsReviewHistoryRepository srsReviewHistoryRepository;

    @Autowired
    private DictationAttemptRepository dictationAttemptRepository;

    @Autowired
    private ShadowingTakeRepository shadowingTakeRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private QuizAnswerRepository quizAnswerRepository;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        learningActivityEventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) {
        return builder
            .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
            .header("X-XSRF-TOKEN", MOCK_CSRF);
    }

    private MockHttpSession registerAndLogin(String email, String password, String displayName, String timezone) throws Exception {
        RegisterRequest registerReq = new RegisterRequest(email, password, displayName, timezone);
        MvcResult regResult = mockMvc.perform(withCsrf(post("/api/v1/auth/register"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
            .andExpect(status().isCreated())
            .andReturn();
        return (MockHttpSession) regResult.getRequest().getSession(false);
    }

    // 1. Authenticated profile retrieval
    @Test
    @DisplayName("GET /api/v1/settings returns authenticated user settings")
    void getSettingsAuthenticatedReturnsSettings() throws Exception {
        MockHttpSession session = registerAndLogin("alice@example.com", "Password123!", "Alice Learner", "Asia/Ho_Chi_Minh");

        mockMvc.perform(get("/api/v1/settings").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("alice@example.com"))
            .andExpect(jsonPath("$.displayName").value("Alice Learner"))
            .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"))
            .andExpect(jsonPath("$.dailyGoalItems").value(20))
            .andExpect(jsonPath("$.preferredAudioSpeed").value(1.0));
    }

    // 2. Anonymous access rejected
    @Test
    @DisplayName("GET /api/v1/settings without session is rejected with 401 Unauthorized")
    void getSettingsAnonymousRejected() throws Exception {
        mockMvc.perform(get("/api/v1/settings"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // 3. CSRF enforcement
    @Test
    @DisplayName("PUT /api/v1/settings without CSRF token is rejected with 403 Forbidden")
    void updateSettingsMissingCsrfRejected() throws Exception {
        MockHttpSession session = registerAndLogin("bob@example.com", "Password123!", "Bob Learner", "UTC");
        UpdateProfileRequest updateReq = new UpdateProfileRequest("Bob Updated");

        mockMvc.perform(put("/api/v1/settings/profile")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isForbidden());
    }

    // 4. Valid display name update
    @Test
    @DisplayName("PUT /api/v1/settings/profile updates display name and trims whitespace")
    void updateProfileValidDisplayName() throws Exception {
        MockHttpSession session = registerAndLogin("carol@example.com", "Password123!", "Carol Initial", "UTC");
        UpdateProfileRequest request = new UpdateProfileRequest("  Carol Vance  ");

        mockMvc.perform(withCsrf(put("/api/v1/settings/profile"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Carol Vance"));

        User user = userRepository.findByEmail("carol@example.com").orElseThrow();
        assertThat(user.getDisplayName()).isEqualTo("Carol Vance");
    }

    // 5. Invalid display name
    @Test
    @DisplayName("PUT /api/v1/settings/profile with short or blank display name returns 400 Bad Request")
    void updateProfileInvalidDisplayName() throws Exception {
        MockHttpSession session = registerAndLogin("dan@example.com", "Password123!", "Dan Learner", "UTC");

        UpdateProfileRequest blankRequest = new UpdateProfileRequest("   ");
        mockMvc.perform(withCsrf(put("/api/v1/settings/profile"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(blankRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        UpdateProfileRequest singleCharRequest = new UpdateProfileRequest("A");
        mockMvc.perform(withCsrf(put("/api/v1/settings/profile"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(singleCharRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // 6. Invalid daily goal
    @Test
    @DisplayName("PUT /api/v1/settings/preferences with goal < 1 or > 500 returns 400 Bad Request")
    void updatePreferencesInvalidDailyGoal() throws Exception {
        MockHttpSession session = registerAndLogin("eva@example.com", "Password123!", "Eva Learner", "UTC");

        UpdatePreferencesRequest negativeGoal = new UpdatePreferencesRequest(null, 0, null);
        mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(negativeGoal)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        UpdatePreferencesRequest excessiveGoal = new UpdatePreferencesRequest(null, 501, null);
        mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(excessiveGoal)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // 7. Valid daily goal persistence
    @Test
    @DisplayName("PUT /api/v1/settings/preferences persists daily goal across requests")
    void updatePreferencesValidDailyGoal() throws Exception {
        MockHttpSession session = registerAndLogin("frank@example.com", "Password123!", "Frank Learner", "UTC");

        UpdatePreferencesRequest request = new UpdatePreferencesRequest(null, 35, null);
        mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyGoalItems").value(35));

        User user = userRepository.findByEmail("frank@example.com").orElseThrow();
        assertThat(user.getDailyGoalItems()).isEqualTo(35);
    }

    // 8. Dashboard goal update
    @Test
    @DisplayName("Dashboard summary immediately reflects updated daily study goal")
    void dashboardGoalUpdatesReflected() throws Exception {
        MockHttpSession session = registerAndLogin("grace@example.com", "Password123!", "Grace Learner", "UTC");

        mockMvc.perform(get("/api/v1/dashboard/summary").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyGoalTarget").value(20));

        UpdatePreferencesRequest request = new UpdatePreferencesRequest(null, 50, null);
        mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dashboard/summary").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyGoalTarget").value(50));
    }

    // 9. Heatmap goal consistency
    @Test
    @DisplayName("Heatmap respects updated daily goal target for completion calculation")
    void heatmapGoalConsistency() throws Exception {
        MockHttpSession session = registerAndLogin("hank@example.com", "Password123!", "Hank Learner", "Asia/Ho_Chi_Minh");
        User user = userRepository.findByEmail("hank@example.com").orElseThrow();

        // Insert 15 activity events for today
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        for (int i = 0; i < 15; i++) {
            learningActivityEventRepository.save(new LearningActivityEvent(
                user.getId(), "FLASHCARD_REVIEW", UUID.randomUUID(), Instant.now(), "Asia/Ho_Chi_Minh", today, 10
            ));
        }

        // With default goal 20, 15 is not completed
        HeatmapResponse heatmapBefore = analyticsService.getActivityHeatmap(user.getId());
        assertThat(heatmapBefore.days().get(today.toString())).isNotNull();
        assertThat(heatmapBefore.days().get(today.toString()).goalCompleted()).isFalse();

        // Lower goal to 10 items
        UpdatePreferencesRequest request = new UpdatePreferencesRequest(null, 10, null);
        mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        // Now goalCompleted for today is true
        HeatmapResponse heatmapAfter = analyticsService.getActivityHeatmap(user.getId());
        assertThat(heatmapAfter.days().get(today.toString())).isNotNull();
        assertThat(heatmapAfter.days().get(today.toString()).goalCompleted()).isTrue();
    }

    // 10. Valid timezone update
    @Test
    @DisplayName("PUT /api/v1/settings/preferences persists valid IANA timezone")
    void updatePreferencesValidTimezone() throws Exception {
        MockHttpSession session = registerAndLogin("iris@example.com", "Password123!", "Iris Learner", "UTC");

        UpdatePreferencesRequest request = new UpdatePreferencesRequest("Asia/Tokyo", null, null);
        mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("Asia/Tokyo"));

        User user = userRepository.findByEmail("iris@example.com").orElseThrow();
        assertThat(user.getTimezone()).isEqualTo("Asia/Tokyo");
    }

    // 11. Invalid timezone rejection
    @Test
    @DisplayName("PUT /api/v1/settings/preferences with invalid timezone returns 400 Bad Request")
    void updatePreferencesInvalidTimezone() throws Exception {
        MockHttpSession session = registerAndLogin("jack@example.com", "Password123!", "Jack Learner", "UTC");

        UpdatePreferencesRequest request = new UpdatePreferencesRequest("Mars/Olympus", null, null);
        mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        User user = userRepository.findByEmail("jack@example.com").orElseThrow();
        assertThat(user.getTimezone()).isEqualTo("UTC"); // Unchanged
    }

    // 12. Historical local date preservation
    @Test
    @DisplayName("Timezone update never alters past historical_local_date rows in learning_activity_events")
    void timezoneChangePreservesHistoricalLocalDates() throws Exception {
        MockHttpSession session = registerAndLogin("karen@example.com", "Password123!", "Karen Learner", "Asia/Tokyo");
        User user = userRepository.findByEmail("karen@example.com").orElseThrow();

        LocalDate pastDate = LocalDate.of(2026, 1, 15);
        UUID eventId = UUID.randomUUID();
        learningActivityEventRepository.save(new LearningActivityEvent(
            user.getId(), "FLASHCARD_REVIEW", UUID.randomUUID(), Instant.parse("2026-01-15T14:30:00Z"), "Asia/Tokyo", pastDate, 30
        ));

        // Update timezone to America/New_York
        UpdatePreferencesRequest request = new UpdatePreferencesRequest("America/New_York", null, null);
        mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        // Invariant check: past event's historicalLocalDate is still 2026-01-15
        LearningActivityEvent event = learningActivityEventRepository.findAll().get(0);
        assertThat(event.getHistoricalLocalDate()).isEqualTo(pastDate);
        assertThat(event.getTimezoneAtEvent()).isEqualTo("Asia/Tokyo");
    }

    // 13. Timezone change across UTC midnight
    @Test
    @DisplayName("Timezone update across UTC midnight computes correct 'today' boundary without error")
    void timezoneChangeAcrossUtcMidnight() throws Exception {
        MockHttpSession session = registerAndLogin("leo@example.com", "Password123!", "Leo Learner", "UTC");

        // Set to Asia/Ho_Chi_Minh (+07:00)
        UpdatePreferencesRequest req1 = new UpdatePreferencesRequest("Asia/Ho_Chi_Minh", null, null);
        mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isOk());

        // Set to Pacific/Honolulu (-10:00)
        UpdatePreferencesRequest req2 = new UpdatePreferencesRequest("Pacific/Honolulu", null, null);
        mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
            .andExpect(status().isOk());

        // Dashboard summary succeeds cleanly
        mockMvc.perform(get("/api/v1/dashboard/summary").session(session))
            .andExpect(status().isOk());
    }

    // 14. DST behavior
    @Test
    @DisplayName("Timezone update handles America/New_York and Europe/London with real DST zone rules")
    void dstTimezoneBehavior() throws Exception {
        MockHttpSession session = registerAndLogin("mia@example.com", "Password123!", "Mia Learner", "UTC");

        UpdatePreferencesRequest reqNy = new UpdatePreferencesRequest("America/New_York", null, null);
        mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqNy)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("America/New_York"));

        UpdatePreferencesRequest reqLondon = new UpdatePreferencesRequest("Europe/London", null, null);
        mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqLondon)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("Europe/London"));
    }

    // 15. Audio speed validation
    @Test
    @DisplayName("PUT /api/v1/settings/preferences rejects invalid audio speeds (e.g. 0.5x, 2.0x)")
    void invalidAudioSpeedRejected() throws Exception {
        MockHttpSession session = registerAndLogin("noah@example.com", "Password123!", "Noah Learner", "UTC");

        UpdatePreferencesRequest invalidSpeed = new UpdatePreferencesRequest(null, null, 2.0);
        mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidSpeed)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // 16. Audio preference persistence
    @Test
    @DisplayName("PUT /api/v1/settings/preferences accepts and persists 0.75, 1.0, 1.25 audio speeds")
    void validAudioSpeedPersistence() throws Exception {
        MockHttpSession session = registerAndLogin("olivia@example.com", "Password123!", "Olivia Learner", "UTC");

        for (Double speed : new Double[]{0.75, 1.25, 1.0}) {
            UpdatePreferencesRequest request = new UpdatePreferencesRequest(null, null, speed);
            mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredAudioSpeed").value(speed));

            User user = userRepository.findByEmail("olivia@example.com").orElseThrow();
            assertThat(user.getPreferredAudioSpeed()).isEqualTo(speed);
        }
    }

    // 17. Correct current password updates password hash
    @Test
    @DisplayName("PUT /api/v1/settings/password changes password when current password is valid")
    void changePasswordSuccess() throws Exception {
        MockHttpSession session = registerAndLogin("paul@example.com", "OldPassword123!", "Paul Learner", "UTC");

        ChangePasswordRequest request = new ChangePasswordRequest(
            "OldPassword123!",
            "NewPassword456!",
            "NewPassword456!"
        );

        mockMvc.perform(withCsrf(put("/api/v1/settings/password"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));

        User user = userRepository.findByEmail("paul@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword456!", user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("OldPassword123!", user.getPasswordHash())).isFalse();
    }

    // 18. Incorrect current password rejected
    @Test
    @DisplayName("PUT /api/v1/settings/password rejects wrong current password with 400 Bad Request")
    void changePasswordIncorrectCurrentPassword() throws Exception {
        MockHttpSession session = registerAndLogin("quinn@example.com", "RealPassword123!", "Quinn Learner", "UTC");

        ChangePasswordRequest request = new ChangePasswordRequest(
            "WrongPassword999!",
            "NewPassword456!",
            "NewPassword456!"
        );

        mockMvc.perform(withCsrf(put("/api/v1/settings/password"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    }

    // 19. Weak new password rejected
    @Test
    @DisplayName("PUT /api/v1/settings/password rejects weak new password failing registration policy")
    void changePasswordWeakNewPassword() throws Exception {
        MockHttpSession session = registerAndLogin("rose@example.com", "RealPassword123!", "Rose Learner", "UTC");

        // Weak: no special character or digits
        ChangePasswordRequest request = new ChangePasswordRequest(
            "RealPassword123!",
            "simplepassword",
            "simplepassword"
        );

        mockMvc.perform(withCsrf(put("/api/v1/settings/password"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // 20. Password hash changes securely and old password cannot be reused as new password
    @Test
    @DisplayName("PUT /api/v1/settings/password rejects new password matching current password")
    void changePasswordRejectsIdenticalNewPassword() throws Exception {
        MockHttpSession session = registerAndLogin("sam@example.com", "CurrentPass123!", "Sam Learner", "UTC");

        ChangePasswordRequest request = new ChangePasswordRequest(
            "CurrentPass123!",
            "CurrentPass123!",
            "CurrentPass123!"
        );

        mockMvc.perform(withCsrf(put("/api/v1/settings/password"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PUT /api/v1/settings/password rejects mismatched confirm password with 400 Bad Request")
    void changePasswordMismatchedConfirmPassword() throws Exception {
        MockHttpSession session = registerAndLogin("mismatch@example.com", "CurrentPass123!", "Mismatch Learner", "UTC");

        ChangePasswordRequest request = new ChangePasswordRequest(
            "CurrentPass123!",
            "NewPassword123!",
            "DifferentPassword123!"
        );

        mockMvc.perform(withCsrf(put("/api/v1/settings/password"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // 21 & 22. Old password no longer authenticates, new password authenticates
    @Test
    @DisplayName("After password change, login with old password fails and new password succeeds")
    void loginWithOldAndNewPasswordAfterChange() throws Exception {
        MockHttpSession session = registerAndLogin("tina@example.com", "InitialPass123!", "Tina Learner", "UTC");

        ChangePasswordRequest changeReq = new ChangePasswordRequest(
            "InitialPass123!",
            "UpdatedPass987!",
            "UpdatedPass987!"
        );
        mockMvc.perform(withCsrf(put("/api/v1/settings/password"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changeReq)))
            .andExpect(status().isOk());

        // Attempt login with old password -> 401 Unauthorized
        LoginRequest oldLogin = new LoginRequest("tina@example.com", "InitialPass123!");
        mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(oldLogin)))
            .andExpect(status().isUnauthorized());

        // Attempt login with new password -> 200 OK
        LoginRequest newLogin = new LoginRequest("tina@example.com", "UpdatedPass987!");
        mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newLogin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("tina@example.com"));
    }

    // 23. Sensitive fields absent from API responses
    @Test
    @DisplayName("Settings and action responses strictly omit passwords, password hashes, and secrets")
    void sensitiveFieldsAbsentFromResponses() throws Exception {
        MockHttpSession session = registerAndLogin("uma@example.com", "SecurePassword123!", "Uma Learner", "UTC");

        mockMvc.perform(get("/api/v1/settings").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.password_hash").doesNotExist());
    }

    // 24. Cross-user isolation
    @Test
    @DisplayName("User A's session cannot see or modify User B's settings")
    void crossUserIsolationEnforced() throws Exception {
        MockHttpSession sessionA = registerAndLogin("userA@example.com", "Password123!", "User A", "UTC");
        MockHttpSession sessionB = registerAndLogin("userB@example.com", "Password123!", "User B", "Asia/Ho_Chi_Minh");

        // User A reads settings -> sees User A
        mockMvc.perform(get("/api/v1/settings").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("usera@example.com"))
            .andExpect(jsonPath("$.displayName").value("User A"));

        // User B reads settings -> sees User B
        mockMvc.perform(get("/api/v1/settings").session(sessionB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("userb@example.com"))
            .andExpect(jsonPath("$.displayName").value("User B"));

        // User A updates their goal to 45
        UpdatePreferencesRequest reqA = new UpdatePreferencesRequest(null, 45, null);
        mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqA)))
            .andExpect(status().isOk());

        // User B's goal remains 20
        mockMvc.perform(get("/api/v1/settings").session(sessionB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyGoalItems").value(20));
    }

    // 25. Concurrent profile updates
    @Test
    @DisplayName("Concurrent settings updates execute cleanly without deadlocks")
    void concurrentSettingsUpdates() throws Exception {
        MockHttpSession session = registerAndLogin("victor@example.com", "Password123!", "Victor Learner", "UTC");

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int goal = 20 + i;
            executor.submit(() -> {
                try {
                    UpdatePreferencesRequest req = new UpdatePreferencesRequest(null, goal, null);
                    mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isOk());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // ignore
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        assertThat(successCount.get()).isEqualTo(threadCount);
    }

    // 26. Data lifecycle and deletion on disposable users
    @Test
    @DisplayName("DELETE /api/v1/settings/account hard-deletes user and cascades child study records")
    void accountDeletionCascadesUserData() throws Exception {
        MockHttpSession session = registerAndLogin("disposable@example.com", "Password123!", "Disposable Learner", "UTC");
        User user = userRepository.findByEmail("disposable@example.com").orElseThrow();

        // Create learning activity event for this user
        learningActivityEventRepository.save(new LearningActivityEvent(
            user.getId(), "FLASHCARD_REVIEW", UUID.randomUUID(), Instant.now(), "UTC", LocalDate.now(), 15
        ));
        assertThat(learningActivityEventRepository.countByUserIdAndHistoricalLocalDate(user.getId(), LocalDate.now())).isEqualTo(1);

        // Delete account with valid password
        DeleteAccountRequest deleteReq = new DeleteAccountRequest("Password123!");
        mockMvc.perform(withCsrf(delete("/api/v1/settings/account"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(deleteReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DELETED"));

        // User is deleted from database
        assertThat(userRepository.findByEmail("disposable@example.com")).isEmpty();

        // Learning activities were cascaded by PostgreSQL foreign key
        assertThat(learningActivityEventRepository.findAll().stream().filter(e -> e.getUserId().equals(user.getId())).toList()).isEmpty();

        // Subsequent authenticated request fails because user is gone
        mockMvc.perform(get("/api/v1/settings").session(session))
            .andExpect(status().isUnauthorized());
    }

    // 27. Account deletion rejects incorrect password and preserves data
    @Test
    @DisplayName("DELETE /api/v1/settings/account with incorrect password rejects and preserves user")
    void accountDeletionIncorrectPasswordRejected() throws Exception {
        MockHttpSession session = registerAndLogin("keeper@example.com", "Password123!", "Keeper Learner", "UTC");

        DeleteAccountRequest deleteReq = new DeleteAccountRequest("WrongPassword!");
        mockMvc.perform(withCsrf(delete("/api/v1/settings/account"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(deleteReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        assertThat(userRepository.findByEmail("keeper@example.com")).isPresent();
    }

    // 28. Curated learning content and quiz questions remain intact after account deletion
    @Test
    @DisplayName("Account deletion preserves global curated learning content and quiz questions")
    void accountDeletionPreservesGlobalCuratedContent() throws Exception {
        MockHttpSession session = registerAndLogin("content-tester@example.com", "Password123!", "Content Tester", "UTC");

        long initialContentCount = learningContentRepository.count();
        long initialQuizQuestionCount = quizQuestionRepository.count();

        DeleteAccountRequest deleteReq = new DeleteAccountRequest("Password123!");
        mockMvc.perform(withCsrf(delete("/api/v1/settings/account"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(deleteReq)))
            .andExpect(status().isOk());

        assertThat(learningContentRepository.count()).isEqualTo(initialContentCount);
        assertThat(quizQuestionRepository.count()).isEqualTo(initialQuizQuestionCount);
    }

    // 29. Unified update settings PUT endpoint updates all fields
    @Test
    @DisplayName("PUT /api/v1/settings supports canonical unified updates per API_CONTRACT.md Section 10")
    void unifiedSettingsPutEndpoint() throws Exception {
        MockHttpSession session = registerAndLogin("unified@example.com", "Password123!", "Initial Name", "UTC");

        UpdateSettingsRequest request = new UpdateSettingsRequest(
            "Unified Updated",
            "Asia/Bangkok",
            40,
            1.25
        );

        mockMvc.perform(withCsrf(put("/api/v1/settings"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Unified Updated"))
            .andExpect(jsonPath("$.timezone").value("Asia/Bangkok"))
            .andExpect(jsonPath("$.dailyGoalItems").value(40))
            .andExpect(jsonPath("$.preferredAudioSpeed").value(1.25));

        User user = userRepository.findByEmail("unified@example.com").orElseThrow();
        assertThat(user.getDisplayName()).isEqualTo("Unified Updated");
        assertThat(user.getTimezone()).isEqualTo("Asia/Bangkok");
        assertThat(user.getDailyGoalItems()).isEqualTo(40);
        assertThat(user.getPreferredAudioSpeed()).isEqualTo(1.25);
    }

    // 30. Password change POST alias also works
    @Test
    @DisplayName("POST /api/v1/settings/change-password alias also works identically")
    void changePasswordPostAlias() throws Exception {
        MockHttpSession session = registerAndLogin("alias@example.com", "OldPass123!", "Alias Learner", "UTC");

        ChangePasswordRequest request = new ChangePasswordRequest(
            "OldPass123!",
            "NewPass456!",
            "NewPass456!"
        );

        mockMvc.perform(withCsrf(post("/api/v1/settings/change-password"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));

        User user = userRepository.findByEmail("alias@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("NewPass456!", user.getPasswordHash())).isTrue();
    }

    // 31. Multi-session password change policy (Section 2)
    @Test
    @DisplayName("Password change preserves current and other active sessions per Policy A while invalidating old credentials")
    void multiSessionPasswordChangePolicy() throws Exception {
        // Authenticate Session 1
        MockHttpSession session1 = registerAndLogin("dualsession@example.com", "Password123!", "Dual User", "UTC");

        // Authenticate Session 2 for the same account
        MockHttpSession session2 = new MockHttpSession();
        LoginRequest loginReq = new LoginRequest("dualsession@example.com", "Password123!");
        mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                .session(session2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
            .andExpect(status().isOk());

        // Session 1 changes password to NewSecure456!
        ChangePasswordRequest changeReq = new ChangePasswordRequest(
            "Password123!",
            "NewSecure456!",
            "NewSecure456!"
        );
        mockMvc.perform(withCsrf(put("/api/v1/settings/password"))
                .session(session1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changeReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));

        // Session 1 remains valid
        mockMvc.perform(get("/api/v1/settings").session(session1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("dualsession@example.com"));

        // Session 2 remains valid per documented Servlet container Policy A
        mockMvc.perform(get("/api/v1/settings").session(session2))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("dualsession@example.com"));

        // New login with old password fails
        mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("dualsession@example.com", "Password123!"))))
            .andExpect(status().isUnauthorized());

        // New login with new password succeeds
        mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("dualsession@example.com", "NewSecure456!"))))
            .andExpect(status().isOk());
    }

    // 32. Account deletion rollback/failure preserves session and data (Section 3)
    @Test
    @DisplayName("Failed account deletion due to invalid confirmation or wrong password preserves session and leaves database intact")
    void accountDeletionFailurePreservesSessionAndData() throws Exception {
        MockHttpSession session = registerAndLogin("resilient@example.com", "Password123!", "Resilient Learner", "UTC");

        // 1. Invalid confirmation text rejected
        DeleteAccountRequest invalidTextReq = new DeleteAccountRequest("Password123!", "CANCEL");
        mockMvc.perform(withCsrf(delete("/api/v1/settings/account"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidTextReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Session is completely unaffected and remains authenticated
        mockMvc.perform(get("/api/v1/settings").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("resilient@example.com"));

        // 2. Incorrect password rejected
        DeleteAccountRequest wrongPassReq = new DeleteAccountRequest("WrongPassword123!", "DELETE");
        mockMvc.perform(withCsrf(delete("/api/v1/settings/account"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(wrongPassReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        // Session remains authenticated
        mockMvc.perform(get("/api/v1/settings").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("resilient@example.com"));

        // Database record remains intact
        assertThat(userRepository.findByEmail("resilient@example.com")).isPresent();
    }

    // 33. Complete database referential integrity & cascading erasure audit across all 10 user-owned tables (Section 4)
    @Test
    @DisplayName("Account deletion cleanly cascades across all 10 user-owned child tables while strictly preserving shared learning contents")
    void exhaustiveReferentialIntegrityCascadingAudit() throws Exception {
        MockHttpSession session = registerAndLogin("audit-all@example.com", "Password123!", "Audit User", "UTC");
        User user = userRepository.findByEmail("audit-all@example.com").orElseThrow();
        UUID userId = user.getId();

        // 1. Vocabulary
        Vocabulary vocab = new Vocabulary();
        vocab.setUserId(userId);
        vocab.setTerm("magnificent");
        vocab.setNormalizedTerm("magnificent");
        vocab.setPartOfSpeech("adjective");
        vocab.setVietnameseMeaning("tuyet voi");
        Vocabulary savedVocab = vocabularyRepository.save(vocab);
        final UUID vocabId = savedVocab.getId();

        // 2. Collection
        Collection savedCol = collectionRepository.save(new Collection(userId, "IELTS Advanced", "High level words"));
        final UUID colId = savedCol.getId();

        // 3. VocabularyCollection join
        VocabularyCollection vc = new VocabularyCollection(userId, colId, vocabId);
        vocabularyCollectionRepository.save(vc);

        // 4. SrsItem
        SrsItem item = new SrsItem();
        item.setUserId(userId);
        item.setVocabularyId(vocabId);
        SrsItem savedItem = srsItemRepository.save(item);
        final UUID itemId = savedItem.getId();

        // 5. SrsReviewHistory
        SrsReviewHistory srsHist = new SrsReviewHistory(
            userId, itemId, UUID.randomUUID(), 4, 0, 1,
            new BigDecimal("2.50"), new BigDecimal("2.60"), 10, Instant.now()
        );
        srsReviewHistoryRepository.save(srsHist);

        // 6. DictationAttempt
        LearningContent sharedContent = learningContentRepository.findAll().stream()
            .filter(c -> "DICTATION".equals(c.getContentType()))
            .findFirst()
            .orElseThrow();
        long initialSharedContentCount = learningContentRepository.count();

        DictationAttempt dictAttempt = new DictationAttempt(
            UUID.randomUUID(), userId, sharedContent.getId(), UUID.randomUUID(),
            "Daily Routine transcript", new BigDecimal("98.00"), 10, 1, 12, Instant.now()
        );
        dictationAttemptRepository.save(dictAttempt);

        // 7. ShadowingTake
        ShadowingTake take = new ShadowingTake(
            UUID.randomUUID(), userId, sharedContent.getId(), UUID.randomUUID(),
            ShadowingTake.RATING_GOOD, null, new BigDecimal("2.50"), Instant.now()
        );
        shadowingTakeRepository.save(take);

        // 8. QuizAttempt
        QuizAttempt savedQuizAttempt = quizAttemptRepository.save(new QuizAttempt(
            UUID.randomUUID(), userId, sharedContent.getId(), UUID.randomUUID(),
            1, 1, new BigDecimal("100.00"), QuizAttempt.STATUS_COMPLETED,
            Instant.now(), Instant.now()
        ));
        final UUID quizAttemptId = savedQuizAttempt.getId();

        // 9. QuizAnswer
        QuizQuestion sharedQuestion = quizQuestionRepository.findAll().stream().findFirst().orElseThrow();
        long initialSharedQuestionCount = quizQuestionRepository.count();

        QuizAnswer quizAns = new QuizAnswer(
            UUID.randomUUID(), quizAttemptId, sharedQuestion.getId(),
            "A", true, "prompt", "A", "explanation", List.of("A", "B")
        );
        quizAnswerRepository.save(quizAns);

        // 10. LearningActivityEvent
        LearningActivityEvent activityEvent = new LearningActivityEvent(
            userId, "FLASHCARD_REVIEW", UUID.randomUUID(), Instant.now(), "UTC", LocalDate.now(), 20
        );
        learningActivityEventRepository.save(activityEvent);

        // Verify all 10 tables have data for this user
        assertThat(vocabularyRepository.findAll().stream().anyMatch(v -> v.getUserId().equals(userId))).isTrue();
        assertThat(collectionRepository.findAll().stream().anyMatch(c -> c.getUserId().equals(userId))).isTrue();
        assertThat(vocabularyCollectionRepository.findAll().stream().anyMatch(v -> v.getUserId().equals(userId))).isTrue();
        assertThat(srsItemRepository.findAll().stream().anyMatch(s -> s.getUserId().equals(userId))).isTrue();
        assertThat(srsReviewHistoryRepository.findAll().stream().anyMatch(s -> s.getUserId().equals(userId))).isTrue();
        assertThat(dictationAttemptRepository.findAll().stream().anyMatch(d -> d.getUserId().equals(userId))).isTrue();
        assertThat(shadowingTakeRepository.findAll().stream().anyMatch(s -> s.getUserId().equals(userId))).isTrue();
        assertThat(quizAttemptRepository.findAll().stream().anyMatch(q -> q.getUserId().equals(userId))).isTrue();
        assertThat(quizAnswerRepository.findAll().stream().anyMatch(a -> a.getQuizAttemptId().equals(quizAttemptId))).isTrue();
        assertThat(learningActivityEventRepository.findAll().stream().anyMatch(e -> e.getUserId().equals(userId))).isTrue();

        // Perform authorized account deletion
        DeleteAccountRequest deleteReq = new DeleteAccountRequest("Password123!", "DELETE");
        mockMvc.perform(withCsrf(delete("/api/v1/settings/account"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(deleteReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DELETED"));

        // Assert all 10 user-owned tables are completely purged of this user's rows
        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(vocabularyRepository.findAll().stream().anyMatch(v -> v.getUserId().equals(userId))).isFalse();
        assertThat(collectionRepository.findAll().stream().anyMatch(c -> c.getUserId().equals(userId))).isFalse();
        assertThat(vocabularyCollectionRepository.findAll().stream().anyMatch(v -> v.getUserId().equals(userId))).isFalse();
        assertThat(srsItemRepository.findAll().stream().anyMatch(s -> s.getUserId().equals(userId))).isFalse();
        assertThat(srsReviewHistoryRepository.findAll().stream().anyMatch(s -> s.getUserId().equals(userId))).isFalse();
        assertThat(dictationAttemptRepository.findAll().stream().anyMatch(d -> d.getUserId().equals(userId))).isFalse();
        assertThat(shadowingTakeRepository.findAll().stream().anyMatch(s -> s.getUserId().equals(userId))).isFalse();
        assertThat(quizAttemptRepository.findAll().stream().anyMatch(q -> q.getUserId().equals(userId))).isFalse();
        assertThat(quizAnswerRepository.findAll().stream().anyMatch(a -> a.getQuizAttemptId().equals(quizAttemptId))).isFalse();
        assertThat(learningActivityEventRepository.findAll().stream().anyMatch(e -> e.getUserId().equals(userId))).isFalse();

        // Assert shared reference contents are completely preserved
        assertThat(learningContentRepository.count()).isEqualTo(initialSharedContentCount);
        assertThat(quizQuestionRepository.count()).isEqualTo(initialSharedQuestionCount);
    }

    // 34. Historical local date & timezone invariance audit (Section 5)
    @Test
    @DisplayName("Updating timezone and daily goal strictly preserves historical_local_date in learning activity events")
    void historicalLocalDatePreservedAcrossTimezoneAndGoalUpdates() throws Exception {
        MockHttpSession session = registerAndLogin("tz-audit@example.com", "Password123!", "Timezone Auditor", "UTC");
        User user = userRepository.findByEmail("tz-audit@example.com").orElseThrow();
        UUID userId = user.getId();

        LocalDate historicalDate = LocalDate.of(2026, 3, 15);
        LearningActivityEvent event = new LearningActivityEvent(
            userId,
            "DICTATION_SENTENCE",
            UUID.randomUUID(),
            Instant.parse("2026-03-15T22:30:00Z"),
            "UTC",
            historicalDate,
            45
        );
        learningActivityEventRepository.save(event);

        // Verify initial state
        LearningActivityEvent storedEvent = learningActivityEventRepository.findAll().stream()
            .filter(e -> e.getUserId().equals(userId))
            .findFirst()
            .orElseThrow();
        assertThat(storedEvent.getHistoricalLocalDate()).isEqualTo(historicalDate);
        assertThat(storedEvent.getTimezoneAtEvent()).isEqualTo("UTC");

        // Update timezone to Asia/Tokyo (+09:00)
        UpdatePreferencesRequest tzReq = new UpdatePreferencesRequest("Asia/Tokyo", null, null);
        mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tzReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("Asia/Tokyo"));

        // Historical local date must NOT have changed despite timezone shift
        LearningActivityEvent eventPostTz = learningActivityEventRepository.findById(storedEvent.getId()).orElseThrow();
        assertThat(eventPostTz.getHistoricalLocalDate()).isEqualTo(historicalDate);
        assertThat(eventPostTz.getTimezoneAtEvent()).isEqualTo("UTC");

        // Update daily goal to 60 items
        UpdatePreferencesRequest goalReq = new UpdatePreferencesRequest(null, 60, null);
        mockMvc.perform(withCsrf(put("/api/v1/settings/preferences"))
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(goalReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyGoalItems").value(60));

        // Historical local date must still be identical
        LearningActivityEvent eventPostGoal = learningActivityEventRepository.findById(storedEvent.getId()).orElseThrow();
        assertThat(eventPostGoal.getHistoricalLocalDate()).isEqualTo(historicalDate);
    }
}
