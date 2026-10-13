package com.englishflow.dictation;

import com.englishflow.analytics.LearningActivityEvent;
import com.englishflow.analytics.LearningActivityEventRepository;
import com.englishflow.dictation.dto.DictationSubmitRequest;
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
class DictationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LearningContentRepository learningContentRepository;

    @Autowired
    private DictationAttemptRepository dictationAttemptRepository;

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

    private static final UUID B2_EXERCISE_ID = UUID.fromString("b2000000-0000-0000-0000-000000000042");
    private static final UUID A1_EXERCISE_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        learningActivityEventRepository.deleteAll();
        dictationAttemptRepository.deleteAll();
        userRepository.deleteAll();

        userA = userRepository.save(new User(
            "dictation.a@example.com",
            passwordEncoder.encode("Password123!"),
            "Learner A",
            "Asia/Ho_Chi_Minh",
            20
        ));

        userB = userRepository.save(new User(
            "dictation.b@example.com",
            passwordEncoder.encode("Password123!"),
            "Learner B",
            "America/New_York",
            15
        ));
    }

    @AfterEach
    void tearDown() {
        learningActivityEventRepository.deleteAll();
        dictationAttemptRepository.deleteAll();
        userRepository.deleteAll();
    }

    private MockHttpServletRequestBuilder withAuthAndCsrf(MockHttpServletRequestBuilder builder, User user) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
            user.getEmail(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        return builder
            .session(session)
            .cookie(new Cookie("XSRF-TOKEN", "test-csrf-token"))
            .header("X-XSRF-TOKEN", "test-csrf-token");
    }

    @Test
    @DisplayName("01. List exercises returns seeded content with metadata and completion status")
    void testListExercises() throws Exception {
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/dictation"), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(5)))
            .andExpect(jsonPath("$[0].title", notNullValue()))
            .andExpect(jsonPath("$[0].audioUrl", notNullValue()))
            .andExpect(jsonPath("$[0].isCompleted", is(false)));
    }

    @Test
    @DisplayName("02. Get exercise metadata withholds canonical transcript text")
    void testGetExerciseWithholdsTranscript() throws Exception {
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/dictation/" + B2_EXERCISE_ID), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contentId", is(B2_EXERCISE_ID.toString())))
            .andExpect(jsonPath("$.title", is("Economic Resilience")))
            .andExpect(jsonPath("$.cefrLevel", is("B2")))
            .andExpect(jsonPath("$.audioUrl", containsString("dictation-b2-042.wav")))
            .andExpect(jsonPath("$.audioDurationSeconds", notNullValue()))
            .andExpect(jsonPath("$.canonicalTranscript").doesNotExist())
            .andExpect(jsonPath("$.transcriptText").doesNotExist());
    }

    @Test
    @DisplayName("03. Get non-existent exercise returns 404 NOT_FOUND")
    void testGetNonExistentExercise() throws Exception {
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/dictation/" + randomId), userA))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    @Test
    @DisplayName("04. Submit transcription requires authentication")
    void testSubmitRequiresAuthentication() throws Exception {
        DictationSubmitRequest req = new DictationSubmitRequest(
            UUID.randomUUID(),
            "The country showed great economic resilience during the crisis.",
            25
        );

        mockMvc.perform(post("/api/v1/dictation/" + B2_EXERCISE_ID + "/submit")
                .cookie(new Cookie("XSRF-TOKEN", "test-csrf-token"))
                .header("X-XSRF-TOKEN", "test-csrf-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("05. Exact transcription submission scores 100%, passes, reveals reference, and creates activity event")
    void testExactSubmission() throws Exception {
        UUID attemptId = UUID.randomUUID();
        String transcript = "The country showed great economic resilience during the crisis.";
        DictationSubmitRequest req = new DictationSubmitRequest(attemptId, transcript, 22);

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attemptId", is(attemptId.toString())))
            .andExpect(jsonPath("$.accuracyScore", is(100.0)))
            .andExpect(jsonPath("$.isPassed", is(true)))
            .andExpect(jsonPath("$.errorCount", is(0)))
            .andExpect(jsonPath("$.canonicalTranscript", is(transcript)))
            .andExpect(jsonPath("$.diffTokens", notNullValue()));

        // Verify DB persistence
        assertThat(dictationAttemptRepository.findByUserIdAndAttemptId(userA.getId(), attemptId)).isPresent();

        // Verify Learning Activity Event
        List<LearningActivityEvent> events = learningActivityEventRepository.findAll();
        assertThat(events).hasSize(1);
        LearningActivityEvent event = events.get(0);
        assertThat(event.getUserId()).isEqualTo(userA.getId());
        assertThat(event.getEventType()).isEqualTo("DICTATION_SENTENCE");
        assertThat(event.getAttemptId()).isEqualTo(attemptId);
        assertThat(event.getDurationSeconds()).isEqualTo(22);
    }

    @Test
    @DisplayName("06. Normalized submission (case & missing period) scores 100%")
    void testNormalizedSubmission() throws Exception {
        UUID attemptId = UUID.randomUUID();
        String transcript = "the country showed great economic resilience during the crisis";
        DictationSubmitRequest req = new DictationSubmitRequest(attemptId, transcript, 18);

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accuracyScore", is(100.0)))
            .andExpect(jsonPath("$.isPassed", is(true)));
    }

    @Test
    @DisplayName("07. Minor typo achieves high accuracy (>95%) and isPassed true")
    void testMinorTypoSubmission() throws Exception {
        UUID attemptId = UUID.randomUUID();
        // Typo: "resillience" (double l)
        String transcript = "The country showed great economic resillience during the crisis.";
        DictationSubmitRequest req = new DictationSubmitRequest(attemptId, transcript, 20);

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accuracyScore").value(org.hamcrest.Matchers.greaterThanOrEqualTo(95.0)))
            .andExpect(jsonPath("$.isPassed", is(true)));
    }

    @Test
    @DisplayName("08. Completed low-accuracy submission (<80%) still records a valid DICTATION_SENTENCE learning event")
    void testLowAccuracySubmissionStillCountsAsLearningActivity() throws Exception {
        UUID attemptId = UUID.randomUUID();
        String transcript = "I think someone said something about country economy.";
        DictationSubmitRequest req = new DictationSubmitRequest(attemptId, transcript, 35);

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accuracyScore").value(org.hamcrest.Matchers.lessThan(80.0)))
            .andExpect(jsonPath("$.isPassed", is(false)));

        // Critical product rule: Completed work records learning activity regardless of 80% pass threshold
        List<LearningActivityEvent> events = learningActivityEventRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("DICTATION_SENTENCE");
    }

    @Test
    @DisplayName("09. Idempotent identical replay returns HTTP 200 with Idempotent-Replay: true header")
    void testIdenticalReplayIsIdempotent() throws Exception {
        UUID attemptId = UUID.randomUUID();
        String transcript = "The country showed great economic resilience during the crisis.";
        DictationSubmitRequest req = new DictationSubmitRequest(attemptId, transcript, 25);

        // First attempt
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Idempotent-Replay"));

        // Second attempt (identical replay)
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(header().string("Idempotent-Replay", "true"))
            .andExpect(jsonPath("$.accuracyScore", is(100.0)));

        // Exactly 1 attempt and 1 activity event in DB
        assertThat(dictationAttemptRepository.count()).isEqualTo(1);
        assertThat(learningActivityEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("10. Conflicting reuse of attempt_id with different transcript returns 409 IDEMPOTENCY_CONFLICT")
    void testConflictingAttemptIdReturns409() throws Exception {
        UUID attemptId = UUID.randomUUID();
        DictationSubmitRequest req1 = new DictationSubmitRequest(attemptId, "First transcript text.", 10);
        DictationSubmitRequest req2 = new DictationSubmitRequest(attemptId, "Different transcript text.", 15);

        // First attempt
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isOk());

        // Conflicting replay
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));
    }

    @Test
    @DisplayName("11. Concurrent duplicate submissions are safe and produce exactly one record")
    void testConcurrentSubmissionsSafety() throws Exception {
        UUID attemptId = UUID.randomUUID();
        String transcript = "I drink a cup of coffee every morning before work.";
        DictationSubmitRequest req = new DictationSubmitRequest(attemptId, transcript, 12);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Integer> task = () -> {
            readyLatch.countDown();
            startLatch.await(5, TimeUnit.SECONDS);

            return mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + A1_EXERCISE_ID + "/submit"), userA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse().getStatus();
        };

        Future<Integer> f1 = executor.submit(task);
        Future<Integer> f2 = executor.submit(task);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        int s1 = f1.get();
        int s2 = f2.get();
        executor.shutdown();

        assertThat(s1).isEqualTo(200);
        assertThat(s2).isEqualTo(200);

        // Exactly 1 attempt record and 1 activity event created
        assertThat(dictationAttemptRepository.count()).isEqualTo(1);
        assertThat(learningActivityEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("12. Cross-user isolation: User B cannot see User A's attempts or modify User A's progress")
    void testCrossUserIsolation() throws Exception {
        UUID attemptId = UUID.randomUUID();
        DictationSubmitRequest req = new DictationSubmitRequest(attemptId, "I drink a cup of coffee every morning before work.", 15);

        // User A completes exercise A1
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + A1_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());

        // User A sees A1 as completed
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/dictation"), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].isCompleted", is(true)))
            .andExpect(jsonPath("$[0].lastAccuracyScore", notNullValue()));

        // User B sees A1 as NOT completed
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/dictation"), userB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].isCompleted", is(false)))
            .andExpect(jsonPath("$[0].lastAccuracyScore").doesNotExist());
    }

    @Test
    @DisplayName("13. Static audio asset endpoint serves WAV file with audio/wav mime type")
    void testStaticAudioAssetServing() throws Exception {
        mockMvc.perform(get("/api/v1/media/audio/curated/dictation-b2-042.wav"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.anyOf(containsString("audio/wav"), containsString("audio/x-wav"))))
            .andExpect(header().exists("Content-Length"));
    }

    @Test
    @DisplayName("14. Merely browsing or viewing dictation exercises generates ZERO learning activity events")
    void testBrowsingGeneratesZeroActivity() throws Exception {
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/dictation"), userA)).andExpect(status().isOk());
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/dictation/" + B2_EXERCISE_ID), userA)).andExpect(status().isOk());

        assertThat(learningActivityEventRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("15. Historical local date is calculated using user's timezone")
    void testHistoricalLocalDateAnchoredToUserTimezone() throws Exception {
        UUID attemptId = UUID.randomUUID();
        DictationSubmitRequest req = new DictationSubmitRequest(attemptId, "The country showed great economic resilience during the crisis.", 20);

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());

        List<LearningActivityEvent> events = learningActivityEventRepository.findAll();
        assertThat(events).hasSize(1);
        LearningActivityEvent event = events.get(0);
        assertThat(event.getTimezoneAtEvent()).isEqualTo("Asia/Ho_Chi_Minh");

        LocalDate expectedLocalDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(event.getHistoricalLocalDate()).isEqualTo(expectedLocalDate);
    }

    @Test
    @DisplayName("16. Matrix C: Conflicting reuse of attempt_id on a different contentId returns 409 IDEMPOTENCY_CONFLICT")
    void testConflictingAttemptIdAcrossDifferentExercisesReturns409() throws Exception {
        UUID attemptId = UUID.randomUUID();
        String transcript = "I drink a cup of coffee every morning before work.";
        DictationSubmitRequest req = new DictationSubmitRequest(attemptId, transcript, 15);

        // Submit for A1
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + A1_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());

        // Attempt to reuse same attemptId for B2
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));
    }

    @Test
    @DisplayName("17. Matrix D: Conflicting reuse of attempt_id with changed durationSeconds returns 409 IDEMPOTENCY_CONFLICT")
    void testConflictingAttemptIdWithChangedDurationReturns409() throws Exception {
        UUID attemptId = UUID.randomUUID();
        String transcript = "I drink a cup of coffee every morning before work.";
        DictationSubmitRequest req1 = new DictationSubmitRequest(attemptId, transcript, 15);
        DictationSubmitRequest req2 = new DictationSubmitRequest(attemptId, transcript, 99);

        // Submit with duration 15
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + A1_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isOk());

        // Replay with different duration (99s)
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + A1_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));
    }

    @Test
    @DisplayName("18. Matrix E: Historical attempt replay after subsequent attempts completed returns original persisted outcome")
    void testHistoricalAttemptReplayAfterOtherAttemptsCompleted() throws Exception {
        UUID attempt1Id = UUID.randomUUID();
        UUID attempt2Id = UUID.randomUUID();

        DictationSubmitRequest req1 = new DictationSubmitRequest(attempt1Id, "I drink a cup of coffee every morning before work.", 12);
        DictationSubmitRequest req2 = new DictationSubmitRequest(attempt2Id, "The country showed great economic resilience during the crisis.", 22);

        // Complete attempt 1 on A1
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + A1_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isOk());

        // Complete attempt 2 on B2
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
            .andExpect(status().isOk());

        assertThat(dictationAttemptRepository.count()).isEqualTo(2);
        assertThat(learningActivityEventRepository.count()).isEqualTo(2);

        // Replay attempt 1
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + A1_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isOk())
            .andExpect(header().string("Idempotent-Replay", "true"))
            .andExpect(jsonPath("$.attemptId", is(attempt1Id.toString())))
            .andExpect(jsonPath("$.contentId", is(A1_EXERCISE_ID.toString())));

        // Verify counts remain unchanged
        assertThat(dictationAttemptRepository.count()).isEqualTo(2);
        assertThat(learningActivityEventRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("19. Matrix G: Concurrent conflicting submissions result in exactly one 200 and one 409")
    void testConcurrentConflictingSubmissions() throws Exception {
        UUID attemptId = UUID.randomUUID();
        DictationSubmitRequest req1 = new DictationSubmitRequest(attemptId, "I drink a cup of coffee every morning before work.", 12);
        DictationSubmitRequest req2 = new DictationSubmitRequest(attemptId, "Completely conflicting transcript submitted concurrently.", 12);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Integer> task1 = () -> {
            readyLatch.countDown();
            startLatch.await(5, TimeUnit.SECONDS);
            return mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + A1_EXERCISE_ID + "/submit"), userA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req1)))
                .andReturn().getResponse().getStatus();
        };

        Callable<Integer> task2 = () -> {
            readyLatch.countDown();
            startLatch.await(5, TimeUnit.SECONDS);
            return mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + A1_EXERCISE_ID + "/submit"), userA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req2)))
                .andReturn().getResponse().getStatus();
        };

        Future<Integer> f1 = executor.submit(task1);
        Future<Integer> f2 = executor.submit(task2);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        int s1 = f1.get();
        int s2 = f2.get();
        executor.shutdown();

        // Exactly one should succeed (200) and one should conflict (409)
        assertThat(List.of(s1, s2)).containsExactlyInAnyOrder(200, 409);

        // Exactly 1 attempt row and 1 activity event created
        assertThat(dictationAttemptRepository.count()).isEqualTo(1);
        assertThat(learningActivityEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("20. Matrix H: Rejected requests never record learning activity events")
    void testRejectedRequestCreatesNoLearningActivity() throws Exception {
        UUID attemptId = UUID.randomUUID();
        UUID nonExistentId = UUID.randomUUID();
        DictationSubmitRequest req = new DictationSubmitRequest(attemptId, "Some text", 10);

        // 404 Not Found exercise
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + nonExistentId + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound());

        // Zero activity events
        assertThat(learningActivityEventRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("21. Static audio endpoint supports HTTP Range request seeking")
    void testStaticAudioRangeRequest() throws Exception {
        mockMvc.perform(get("/api/v1/media/audio/curated/dictation-b2-042.wav")
                .header("Range", "bytes=0-1023"))
            .andExpect(status().is(org.hamcrest.Matchers.anyOf(is(200), is(206))))
            .andExpect(header().exists("Content-Type"));
    }

    @Test
    @DisplayName("22. Missing audio asset returns HTTP 404 Not Found")
    void testMissingAudioAssetReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/media/audio/curated/nonexistent-audio-999.wav"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("23. Snapshot A: New submission persists canonical_transcript_snapshot and diff_tokens in dictation_attempts")
    void testSnapshotPersistedOnSubmission() throws Exception {
        UUID attemptId = UUID.randomUUID();
        String submitted = "The country showed great economic resilience during the crisis.";
        DictationSubmitRequest req = new DictationSubmitRequest(attemptId, submitted, 20);

        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());

        DictationAttempt attempt = dictationAttemptRepository.findByUserIdAndAttemptId(userA.getId(), attemptId).orElseThrow();
        assertThat(attempt.getCanonicalTranscriptSnapshot()).isNotBlank();
        assertThat(attempt.getCanonicalTranscriptSnapshot()).isEqualTo("The country showed great economic resilience during the crisis.");
        assertThat(attempt.getDiffTokens()).isNotBlank();
        assertThat(attempt.getDiffTokens()).startsWith("[{").contains("EQUAL");
    }

    @Test
    @DisplayName("24. Snapshot C: Editing reference transcript after completion does not alter historical replay output")
    void testSnapshotReplayUnchangedWhenReferenceTranscriptIsEdited() throws Exception {
        UUID attemptId = UUID.randomUUID();
        String originalRef = "The country showed great economic resilience during the crisis.";
        DictationSubmitRequest req = new DictationSubmitRequest(attemptId, originalRef, 20);

        // Submit initial attempt
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.canonicalTranscript", is(originalRef)))
            .andExpect(jsonPath("$.accuracyScore", is(100.0)));

        // Edit reference transcript in learning_contents to simulate content author update
        LearningContent content = learningContentRepository.findById(B2_EXERCISE_ID).orElseThrow();
        String originalDbText = content.getTranscriptText();
        content.setTranscriptText("Altered reference sentence modified by content administrator.");
        learningContentRepository.save(content);

        try {
            // Replay original attempt
            mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + B2_EXERCISE_ID + "/submit"), userA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                // Frozen canonical snapshot is returned, NOT the altered text
                .andExpect(jsonPath("$.canonicalTranscript", is(originalRef)))
                .andExpect(jsonPath("$.accuracyScore", is(100.0)))
                .andExpect(jsonPath("$.diffTokens", hasSize(9)));
        } finally {
            // Restore original text
            content.setTranscriptText(originalDbText);
            learningContentRepository.save(content);
        }
    }

    @Test
    @DisplayName("25. Snapshot G: Legacy attempt with NULL snapshot columns replays gracefully via fallback")
    void testLegacyAttemptWithNullSnapshotReplaysGracefully() throws Exception {
        UUID attemptId = UUID.randomUUID();
        // Insert legacy attempt directly where snapshot columns are null
        DictationAttempt legacy = new DictationAttempt(
            UUID.randomUUID(),
            userA.getId(),
            A1_EXERCISE_ID,
            attemptId,
            "I drink a cup of coffee every morning before work.",
            BigDecimal.valueOf(100.0),
            10,
            0,
            15,
            null, // canonicalTranscriptSnapshot = null
            null, // diffTokens = null
            java.time.Instant.now()
        );
        dictationAttemptRepository.save(legacy);

        DictationSubmitRequest req = new DictationSubmitRequest(attemptId, "I drink a cup of coffee every morning before work.", 15);

        // Replay of legacy row returns 200 with fallback evaluation
        mockMvc.perform(withAuthAndCsrf(post("/api/v1/dictation/" + A1_EXERCISE_ID + "/submit"), userA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(header().string("Idempotent-Replay", "true"))
            .andExpect(jsonPath("$.accuracyScore", is(100.0)))
            .andExpect(jsonPath("$.canonicalTranscript", notNullValue()));
    }

    @Test
    @DisplayName("26. Audio availability flag is delivered in exercise summary and detail endpoints")
    void testAudioAvailabilityFlagInSummaryAndDetail() throws Exception {
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/dictation"), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].isAudioAvailable", notNullValue()));

        mockMvc.perform(withAuthAndCsrf(get("/api/v1/dictation/" + B2_EXERCISE_ID), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isAudioAvailable", notNullValue()));
    }
}
