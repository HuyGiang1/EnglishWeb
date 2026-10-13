package com.englishflow.shadowing;

import com.englishflow.analytics.LearningActivityEvent;
import com.englishflow.analytics.LearningActivityEventRepository;
import com.englishflow.dictation.LearningContentRepository;
import com.englishflow.shadowing.dto.ShadowingCompleteRequest;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class ShadowingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LearningContentRepository learningContentRepository;

    @Autowired
    private ShadowingTakeRepository shadowingTakeRepository;

    @Autowired
    private LearningActivityEventRepository learningActivityEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User userA;
    private User userB;

    private static final UUID B2_SHADOWING_ID = UUID.fromString("b2000000-0000-0000-0001-000000000042");
    private static final UUID A1_SHADOWING_ID = UUID.fromString("a1000000-0000-0000-0001-000000000001");
    private static final UUID DICTATION_ID = UUID.fromString("b2000000-0000-0000-0000-000000000042");

    @BeforeEach
    void setUp() {
        learningActivityEventRepository.deleteAll();
        shadowingTakeRepository.deleteAll();
        userRepository.deleteAll();

        userA = userRepository.save(new User(
            "shadowing.a@example.com",
            passwordEncoder.encode("Password123!"),
            "Learner A",
            "Asia/Ho_Chi_Minh",
            20
        ));

        userB = userRepository.save(new User(
            "shadowing.b@example.com",
            passwordEncoder.encode("Password123!"),
            "Learner B",
            "America/New_York",
            15
        ));
    }

    @AfterEach
    void tearDown() {
        learningActivityEventRepository.deleteAll();
        shadowingTakeRepository.deleteAll();
        userRepository.deleteAll();
    }

    private MockHttpServletRequestBuilder withAuthAndCsrf(MockHttpServletRequestBuilder builder, User user) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            user.getEmail(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        securityContext.setAuthentication(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        return builder
            .session(session)
            .cookie(new Cookie("XSRF-TOKEN", "test-csrf-token"))
            .header("X-XSRF-TOKEN", "test-csrf-token");
    }

    @Test
    @DisplayName("1. Content listing returns published shadowing exercises with CEFR ordering")
    void testListExercises() throws Exception {
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/shadowing"), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(5)))
            .andExpect(jsonPath("$[0].cefrLevel", is("A1")))
            .andExpect(jsonPath("$[0].title", is("Morning Greetings")))
            .andExpect(jsonPath("$[0].takeCount", is(0)))
            .andExpect(jsonPath("$[3].cefrLevel", is("B2")))
            .andExpect(jsonPath("$[3].title", is("Connection Over Perfection")))
            .andExpect(jsonPath("$[3].isAudioAvailable", notNullValue()));
    }

    @Test
    @DisplayName("2. Exercise detail delivers prompt, reference transcript, and audio metadata")
    void testGetExerciseDetail() throws Exception {
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/shadowing/" + B2_SHADOWING_ID), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contentId", is(B2_SHADOWING_ID.toString())))
            .andExpect(jsonPath("$.title", is("Connection Over Perfection")))
            .andExpect(jsonPath("$.cefrLevel", is("B2")))
            .andExpect(jsonPath("$.transcriptText", is("Fluency is not about perfection, but connection and rhythm.")))
            .andExpect(jsonPath("$.audioUrl", containsString("shadowing-b2-042.wav")))
            .andExpect(jsonPath("$.isAudioAvailable", notNullValue()));
    }

    @Test
    @DisplayName("3. Nonexistent exercise ID returns HTTP 404 NOT_FOUND")
    void testGetNonexistentExercise() throws Exception {
        UUID nonExistent = UUID.randomUUID();
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/shadowing/" + nonExistent), userA))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    @Test
    @DisplayName("4. Querying a Dictation contentId via Shadowing endpoint returns HTTP 404")
    void testGetWrongContentTypeReturns404() throws Exception {
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/shadowing/" + DICTATION_ID), userA))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    @Test
    @DisplayName("5. Unauthenticated request returns HTTP 401 UNAUTHORIZED")
    void testUnauthenticatedAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/shadowing"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/shadowing/" + B2_SHADOWING_ID))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("6. Complete valid practice take persists shadowing_takes and emits SHADOWING_TAKE event")
    void testCompleteValidTake() throws Exception {
        UUID attemptId = UUID.randomUUID();
        ShadowingCompleteRequest req = new ShadowingCompleteRequest(attemptId, "GOOD", 12);

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attemptId", is(attemptId.toString())))
            .andExpect(jsonPath("$.contentId", is(B2_SHADOWING_ID.toString())))
            .andExpect(jsonPath("$.selfRating", is("GOOD")))
            .andExpect(jsonPath("$.hasRecordingSaved", is(false)))
            .andExpect(jsonPath("$.completedAt", notNullValue()));

        // Verify DB persistence in shadowing_takes
        List<ShadowingTake> takes = shadowingTakeRepository.findAll();
        assertThat(takes).hasSize(1);
        ShadowingTake take = takes.get(0);
        assertThat(take.getUserId()).isEqualTo(userA.getId());
        assertThat(take.getContentId()).isEqualTo(B2_SHADOWING_ID);
        assertThat(take.getAttemptId()).isEqualTo(attemptId);
        assertThat(take.getSelfRating()).isEqualTo("GOOD");
        assertThat(take.getRecordingDurationSeconds()).isEqualByComparingTo(BigDecimal.valueOf(12));

        // Verify DB persistence in learning_activity_events
        List<LearningActivityEvent> events = learningActivityEventRepository.findAll();
        assertThat(events).hasSize(1);
        LearningActivityEvent event = events.get(0);
        assertThat(event.getUserId()).isEqualTo(userA.getId());
        assertThat(event.getEventType()).isEqualTo("SHADOWING_TAKE");
        assertThat(event.getAttemptId()).isEqualTo(attemptId);
        assertThat(event.getDurationSeconds()).isEqualTo(12);

        // Verify Dashboard daily goal reflects 1 completed activity
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/dashboard/summary"), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyGoalCompletedCount", is(1)));
    }

    @Test
    @DisplayName("7. Validation: Invalid selfRating rejects with HTTP 400 BAD_REQUEST")
    void testInvalidSelfRatingRejected() throws Exception {
        UUID attemptId = UUID.randomUUID();
        String json = "{\"attemptId\":\"" + attemptId + "\",\"selfRating\":\"EXCELLENT\",\"durationSeconds\":10}";

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        assertThat(shadowingTakeRepository.count()).isZero();
        assertThat(learningActivityEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("8. Validation: Negative durationSeconds rejects with HTTP 400 BAD_REQUEST")
    void testNegativeDurationRejected() throws Exception {
        UUID attemptId = UUID.randomUUID();
        ShadowingCompleteRequest req = new ShadowingCompleteRequest(attemptId, "GOOD", -5);

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        assertThat(shadowingTakeRepository.count()).isZero();
    }

    @Test
    @DisplayName("9. Validation: Null attemptId rejects with HTTP 400 BAD_REQUEST")
    void testNullAttemptIdRejected() throws Exception {
        String json = "{\"selfRating\":\"GOOD\",\"durationSeconds\":10}";

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());

        assertThat(shadowingTakeRepository.count()).isZero();
    }

    @Test
    @DisplayName("10. Exact replay returns original completion with Idempotent-Replay header and no extra event")
    void testExactReplayReturnsIdempotentResponse() throws Exception {
        UUID attemptId = UUID.randomUUID();
        ShadowingCompleteRequest req = new ShadowingCompleteRequest(attemptId, "FLUENT", 15);

        // Initial submission
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Idempotent-Replay"));

        assertThat(shadowingTakeRepository.count()).isEqualTo(1);
        assertThat(learningActivityEventRepository.count()).isEqualTo(1);

        // Replay submission
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(header().string("Idempotent-Replay", "true"))
            .andExpect(jsonPath("$.attemptId", is(attemptId.toString())))
            .andExpect(jsonPath("$.selfRating", is("FLUENT")));

        // Zero duplicate persistence
        assertThat(shadowingTakeRepository.count()).isEqualTo(1);
        assertThat(learningActivityEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("11. Conflicting attempt reuse with mismatched rating returns HTTP 409 IDEMPOTENCY_CONFLICT")
    void testConflictingRatingReturns409() throws Exception {
        UUID attemptId = UUID.randomUUID();
        ShadowingCompleteRequest initialReq = new ShadowingCompleteRequest(attemptId, "GOOD", 10);
        ShadowingCompleteRequest conflictReq = new ShadowingCompleteRequest(attemptId, "NEEDS_WORK", 10);

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initialReq)))
            .andExpect(status().isOk());

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(conflictReq)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));

        assertThat(shadowingTakeRepository.count()).isEqualTo(1);
        assertThat(learningActivityEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("12. Conflicting attempt reuse with mismatched contentId returns HTTP 409 IDEMPOTENCY_CONFLICT")
    void testConflictingContentIdReturns409() throws Exception {
        UUID attemptId = UUID.randomUUID();
        ShadowingCompleteRequest initialReq = new ShadowingCompleteRequest(attemptId, "GOOD", 10);

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initialReq)))
            .andExpect(status().isOk());

        // Same attemptId sent to A1 exercise
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + A1_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initialReq)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));
    }

    @Test
    @DisplayName("13. Conflicting attempt reuse with mismatched duration returns HTTP 409 IDEMPOTENCY_CONFLICT")
    void testConflictingDurationReturns409() throws Exception {
        UUID attemptId = UUID.randomUUID();
        ShadowingCompleteRequest initialReq = new ShadowingCompleteRequest(attemptId, "GOOD", 10);
        ShadowingCompleteRequest conflictReq = new ShadowingCompleteRequest(attemptId, "GOOD", 25);

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initialReq)))
            .andExpect(status().isOk());

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(conflictReq)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));
    }

    @Test
    @DisplayName("14. Multi-threaded concurrent duplicate requests create exactly 1 take and 1 activity event")
    void testConcurrentDuplicateSubmissions() throws Exception {
        int threads = 2;
        UUID attemptId = UUID.randomUUID();
        ShadowingCompleteRequest req = new ShadowingCompleteRequest(attemptId, "ACCEPTABLE", 18);
        String body = objectMapper.writeValueAsString(req);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Integer> task = () -> {
            readyLatch.countDown();
            startLatch.await(5, TimeUnit.SECONDS);
            return mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andReturn().getResponse().getStatus();
        };

        Future<Integer> f1 = executor.submit(task);
        Future<Integer> f2 = executor.submit(task);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        int s1 = f1.get();
        int s2 = f2.get();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(s1).isEqualTo(200);
        assertThat(s2).isEqualTo(200);

        // Exactly one take and one activity event
        assertThat(shadowingTakeRepository.count()).isEqualTo(1);
        assertThat(learningActivityEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("15. Multi-tenant isolation: User B cannot see User A's takes or activity count")
    void testCrossUserIsolation() throws Exception {
        UUID attemptId = UUID.randomUUID();
        ShadowingCompleteRequest req = new ShadowingCompleteRequest(attemptId, "GOOD", 14);

        // User A completes take
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());

        // User B summary shows takeCount = 0
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/shadowing"), userB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[3].takeCount", is(0)));

        // User B dashboard shows completed count = 0
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/dashboard/summary"), userB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyGoalCompletedCount", is(0)));
    }

    @Test
    @DisplayName("16. Browsing-only (GET list / GET detail) creates zero learning activity events")
    void testBrowsingCreatesZeroActivityEvents() throws Exception {
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/shadowing"), userA))
            .andExpect(status().isOk());

        mockMvc.perform(withAuthAndCsrf(get("/api/v1/shadowing/" + B2_SHADOWING_ID), userA))
            .andExpect(status().isOk());

        assertThat(learningActivityEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("17. Historical local date remains frozen to user timezone date at event")
    void testHistoricalLocalDateAnchoredToUserTimezone() throws Exception {
        UUID attemptId = UUID.randomUUID();
        ShadowingCompleteRequest req = new ShadowingCompleteRequest(attemptId, "NEEDS_WORK", 8);

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());

        LearningActivityEvent event = learningActivityEventRepository.findAll().get(0);
        LocalDate expectedDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(event.getHistoricalLocalDate()).isEqualTo(expectedDate);
        assertThat(event.getTimezoneAtEvent()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    @DisplayName("18. Multiple takes on different exercises increment take counts and dashboard correctly")
    void testMultipleTakesIncrementDashboard() throws Exception {
        UUID attempt1 = UUID.randomUUID();
        UUID attempt2 = UUID.randomUUID();

        // Take on A1
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + A1_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ShadowingCompleteRequest(attempt1, "GOOD", 10))))
            .andExpect(status().isOk());

        // Take on B2
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ShadowingCompleteRequest(attempt2, "FLUENT", 15))))
            .andExpect(status().isOk());

        assertThat(shadowingTakeRepository.count()).isEqualTo(2);
        assertThat(learningActivityEventRepository.count()).isEqualTo(2);

        // Dashboard daily goal is now 2
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/dashboard/summary"), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyGoalCompletedCount", is(2)));
    }

    @Test
    @DisplayName("19. Multi-threaded concurrent conflicting requests: exactly one succeeds and one receives 409")
    void testConcurrentConflictingSubmissions() throws Exception {
        int threads = 2;
        UUID attemptId = UUID.randomUUID();
        ShadowingCompleteRequest req1 = new ShadowingCompleteRequest(attemptId, "GOOD", 12);
        ShadowingCompleteRequest req2 = new ShadowingCompleteRequest(attemptId, "FLUENT", 12);
        String body1 = objectMapper.writeValueAsString(req1);
        String body2 = objectMapper.writeValueAsString(req2);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Integer> task1 = () -> {
            readyLatch.countDown();
            startLatch.await(5, TimeUnit.SECONDS);
            return mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body1))
                .andReturn().getResponse().getStatus();
        };

        Callable<Integer> task2 = () -> {
            readyLatch.countDown();
            startLatch.await(5, TimeUnit.SECONDS);
            return mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body2))
                .andReturn().getResponse().getStatus();
        };

        Future<Integer> f1 = executor.submit(task1);
        Future<Integer> f2 = executor.submit(task2);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        int s1 = f1.get();
        int s2 = f2.get();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        // One should be 200, one should be 409
        assertThat(List.of(s1, s2)).containsExactlyInAnyOrder(200, 409);

        // Exactly one take and one activity event persisted
        assertThat(shadowingTakeRepository.count()).isEqualTo(1);
        assertThat(learningActivityEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("20. Replay of earlier attempt ID after subsequent takes returns original outcome unchanged")
    void testReplayAfterAdditionalCompletedTakes() throws Exception {
        UUID attempt1 = UUID.randomUUID();
        UUID attempt2 = UUID.randomUUID();

        ShadowingCompleteRequest req1 = new ShadowingCompleteRequest(attempt1, "ACCEPTABLE", 10);
        ShadowingCompleteRequest req2 = new ShadowingCompleteRequest(attempt2, "GOOD", 12);

        // Complete attempt 1
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + A1_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isOk());

        // Complete attempt 2 on another exercise
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
            .andExpect(status().isOk());

        assertThat(shadowingTakeRepository.count()).isEqualTo(2);
        assertThat(learningActivityEventRepository.count()).isEqualTo(2);

        // Replay attempt 1 -> must return 200 with Idempotent-Replay and unchanged ACCEPTABLE rating
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + A1_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isOk())
            .andExpect(header().string("Idempotent-Replay", "true"))
            .andExpect(jsonPath("$.attemptId", is(attempt1.toString())))
            .andExpect(jsonPath("$.selfRating", is("ACCEPTABLE")));

        // Zero additional takes or events
        assertThat(shadowingTakeRepository.count()).isEqualTo(2);
        assertThat(learningActivityEventRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("21. Validation: Zero durationSeconds is rejected with HTTP 400 BAD_REQUEST")
    void testZeroDurationRejected() throws Exception {
        UUID attemptId = UUID.randomUUID();
        String json = "{\"attemptId\":\"" + attemptId + "\",\"selfRating\":\"GOOD\",\"durationSeconds\":0}";

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        assertThat(shadowingTakeRepository.count()).isZero();
    }

    @Test
    @DisplayName("22. Validation: Excessive durationSeconds (> 65.00s) is rejected with HTTP 400 BAD_REQUEST")
    void testExcessiveDurationRejected() throws Exception {
        UUID attemptId = UUID.randomUUID();
        String json = "{\"attemptId\":\"" + attemptId + "\",\"selfRating\":\"GOOD\",\"durationSeconds\":75.50}";

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        assertThat(shadowingTakeRepository.count()).isZero();
    }

    @Test
    @DisplayName("23. Validation: Duration with scale exceeding 2 decimal places is rejected with HTTP 400 BAD_REQUEST")
    void testDurationScaleExceedingTwoRejected() throws Exception {
        UUID attemptId = UUID.randomUUID();
        String json = "{\"attemptId\":\"" + attemptId + "\",\"selfRating\":\"GOOD\",\"durationSeconds\":8.125}";

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        assertThat(shadowingTakeRepository.count()).isZero();
    }

    @Test
    @DisplayName("24. Validation: Non-finite duration (NaN string) is rejected with HTTP 400 BAD_REQUEST")
    void testNonFiniteDurationRejected() throws Exception {
        UUID attemptId = UUID.randomUUID();
        String json = "{\"attemptId\":\"" + attemptId + "\",\"selfRating\":\"GOOD\",\"durationSeconds\":\"NaN\"}";

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/shadowing/" + B2_SHADOWING_ID + "/complete"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        assertThat(shadowingTakeRepository.count()).isZero();
    }

    @Test
    @DisplayName("25. Audio readiness metadata and spoken speech flags are exposed accurately")
    void testAudioReadinessMetadataExposed() throws Exception {
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/shadowing"), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].audioReadiness", notNullValue()))
            .andExpect(jsonPath("$[0].isSpokenSpeech", notNullValue()));

        mockMvc.perform(withAuthAndCsrf(get("/api/v1/shadowing/" + A1_SHADOWING_ID), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.audioReadiness", notNullValue()))
            .andExpect(jsonPath("$.isSpokenSpeech", notNullValue()));
    }
}
