package com.englishflow.review;

import com.englishflow.analytics.LearningActivityEvent;
import com.englishflow.analytics.LearningActivityEventRepository;
import com.englishflow.review.dto.ReviewSubmissionRequest;
import com.englishflow.review.dto.ReviewSubmissionResponse;
import com.englishflow.review.dto.SubmissionResult;
import com.englishflow.review.scheduler.ReviewRating;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ReviewIntegrationTest {

    private static final String MOCK_CSRF = "review-test-csrf-token";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private SrsItemRepository srsItemRepository;

    @Autowired
    private SrsReviewHistoryRepository srsReviewHistoryRepository;

    @Autowired
    private LearningActivityEventRepository learningActivityEventRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User userA;
    private User userB;
    private MockHttpSession sessionA;
    private MockHttpSession sessionB;

    @BeforeEach
    void setUp() {
        cleanUpAll();

        userA = userRepository.save(new User(
            "user.a@example.com",
            passwordEncoder.encode("Password123!"),
            "User A",
            "Asia/Ho_Chi_Minh",
            20
        ));

        userB = userRepository.save(new User(
            "user.b@example.com",
            passwordEncoder.encode("Password123!"),
            "User B",
            "America/New_York",
            20
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
        srsReviewHistoryRepository.deleteAll();
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

    private Vocabulary createVocab(User user, String term, String pos) {
        return vocabularyRepository.save(new Vocabulary(
            user.getId(),
            term,
            pos,
            "/test/",
            "nghĩa " + term,
            "definition of " + term,
            "Example with " + term,
            null,
            null,
            null,
            "B2"
        ));
    }

    // =========================================================================
    // 1. Provisioning & Queue Tests
    // =========================================================================

    @Test
    @DisplayName("Pre-existing vocabulary is provisioned correctly on due queue fetch")
    void testPreExistingVocabularyProvisioningOnQueueFetch() throws Exception {
        Vocabulary v1 = createVocab(userA, "perseverance", "noun");
        Vocabulary v2 = createVocab(userA, "diligent", "adjective");

        // Initially no SRS items
        assertThat(srsItemRepository.countByUserId(userA.getId())).isZero();

        // Fetch queue
        mockMvc.perform(get("/api/v1/review/queue")
                .session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDue").value(2))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].term").value("perseverance"))
            .andExpect(jsonPath("$.items[1].term").value("diligent"));

        // Both items provisioned
        assertThat(srsItemRepository.countByUserId(userA.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("Queue fetch is idempotent and creates no duplicate SRS items on subsequent calls")
    void testQueueFetchIdempotentProvisioning() throws Exception {
        createVocab(userA, "lucid", "adjective");

        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());
        assertThat(srsItemRepository.countByUserId(userA.getId())).isEqualTo(1);

        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());
        assertThat(srsItemRepository.countByUserId(userA.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Multi-tenant isolation: User A cannot see User B's cards in queue or review them")
    void testMultiTenantIsolation() throws Exception {
        Vocabulary vocabA = createVocab(userA, "ubiquitous", "adjective");
        Vocabulary vocabB = createVocab(userB, "ephemeral", "adjective");

        // User A fetches queue
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDue").value(1))
            .andExpect(jsonPath("$.items[0].term").value("ubiquitous"));

        // User B fetches queue
        mockMvc.perform(get("/api/v1/review/queue").session(sessionB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDue").value(1))
            .andExpect(jsonPath("$.items[0].term").value("ephemeral"));

        // User A attempts to submit grade for User B's card
        SrsItem itemB = srsItemRepository.findByUserIdAndVocabularyId(userB.getId(), vocabB.getId()).orElseThrow();
        ReviewSubmissionRequest req = new ReviewSubmissionRequest(
            itemB.getId(),
            UUID.randomUUID(),
            ReviewRating.GOOD,
            10
        );

        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Soft-deleted words never appear in the due review queue")
    void testSoftDeletedWordsExcludedFromQueue() throws Exception {
        Vocabulary v1 = createVocab(userA, "active-word", "noun");
        Vocabulary v2 = createVocab(userA, "deleted-word", "noun");

        // Soft-delete v2
        v2.setDeletedAt(Instant.now());
        vocabularyRepository.save(v2);

        mockMvc.perform(get("/api/v1/review/queue").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDue").value(1))
            .andExpect(jsonPath("$.items[0].term").value("active-word"));
    }

    @Test
    @DisplayName("Future-due cards do not appear in today's due queue")
    void testFutureDueCardsExcludedFromQueue() throws Exception {
        Vocabulary v1 = createVocab(userA, "due-card", "noun");
        Vocabulary v2 = createVocab(userA, "future-card", "noun");

        // Provision cards
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());

        // Set v2 due in the future
        SrsItem item2 = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v2.getId()).orElseThrow();
        item2.setDueDate(LocalDate.now().plusDays(5));
        item2.setState("REVIEW");
        srsItemRepository.save(item2);

        // Fetch queue again
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalDue").value(1))
            .andExpect(jsonPath("$.items[0].term").value("due-card"));
    }

    // =========================================================================
    // 2. SM-2 Scheduling Ratings & Transitions
    // =========================================================================

    @Test
    @DisplayName("Submit AGAIN rating: resets repetition to 0, sets interval to 1, reduces EF by 0.20, state LEARNING")
    void testAgainRatingScheduling() throws Exception {
        Vocabulary v = createVocab(userA, "anomaly", "noun");
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());
        SrsItem item = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v.getId()).orElseThrow();

        UUID attemptId = UUID.randomUUID();
        ReviewSubmissionRequest req = new ReviewSubmissionRequest(item.getId(), attemptId, ReviewRating.AGAIN, 15);

        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.newIntervalDays").value(1))
            .andExpect(jsonPath("$.newEaseFactor").value(2.30));

        SrsItem updated = srsItemRepository.findById(item.getId()).orElseThrow();
        assertThat(updated.getRepetitionCount()).isZero();
        assertThat(updated.getState()).isEqualTo("LEARNING");
        assertThat(updated.getEaseFactor()).isEqualByComparingTo("2.30");
        assertThat(updated.getIntervalDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("Submit HARD rating: increments repetition, adjusts interval, reduces EF by 0.15")
    void testHardRatingScheduling() throws Exception {
        Vocabulary v = createVocab(userA, "pragmatic", "adjective");
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());
        SrsItem item = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v.getId()).orElseThrow();

        UUID attemptId = UUID.randomUUID();
        ReviewSubmissionRequest req = new ReviewSubmissionRequest(item.getId(), attemptId, ReviewRating.HARD, 8);

        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.newIntervalDays").value(1))
            .andExpect(jsonPath("$.newEaseFactor").value(2.35));

        SrsItem updated = srsItemRepository.findById(item.getId()).orElseThrow();
        assertThat(updated.getRepetitionCount()).isEqualTo(1);
        assertThat(updated.getState()).isEqualTo("REVIEW");
        assertThat(updated.getEaseFactor()).isEqualByComparingTo("2.35");
    }

    @Test
    @DisplayName("Submit GOOD rating: increments repetition, EF unchanged, state REVIEW")
    void testGoodRatingScheduling() throws Exception {
        Vocabulary v = createVocab(userA, "candid", "adjective");
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());
        SrsItem item = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v.getId()).orElseThrow();

        UUID attemptId = UUID.randomUUID();
        ReviewSubmissionRequest req = new ReviewSubmissionRequest(item.getId(), attemptId, ReviewRating.GOOD, 5);

        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.newIntervalDays").value(1))
            .andExpect(jsonPath("$.newEaseFactor").value(2.50));

        SrsItem updated = srsItemRepository.findById(item.getId()).orElseThrow();
        assertThat(updated.getRepetitionCount()).isEqualTo(1);
        assertThat(updated.getState()).isEqualTo("REVIEW");
        assertThat(updated.getEaseFactor()).isEqualByComparingTo("2.50");
    }

    @Test
    @DisplayName("Submit EASY rating: increments repetition, interval jumps to 4, EF increases by 0.15")
    void testEasyRatingScheduling() throws Exception {
        Vocabulary v = createVocab(userA, "fluent", "adjective");
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());
        SrsItem item = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v.getId()).orElseThrow();

        UUID attemptId = UUID.randomUUID();
        ReviewSubmissionRequest req = new ReviewSubmissionRequest(item.getId(), attemptId, ReviewRating.EASY, 3);

        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.newIntervalDays").value(4))
            .andExpect(jsonPath("$.newEaseFactor").value(2.65));

        SrsItem updated = srsItemRepository.findById(item.getId()).orElseThrow();
        assertThat(updated.getRepetitionCount()).isEqualTo(1);
        assertThat(updated.getIntervalDays()).isEqualTo(4);
        assertThat(updated.getEaseFactor()).isEqualByComparingTo("2.65");
    }

    @Test
    @DisplayName("Ease factor cannot drop below 1.30 floor under repeated AGAIN ratings")
    void testEaseFactorFloorBoundary() throws Exception {
        Vocabulary v = createVocab(userA, "tenacious", "adjective");
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());
        SrsItem item = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v.getId()).orElseThrow();

        // Submit 10 consecutive AGAIN reviews with distinct attempt IDs
        for (int i = 0; i < 10; i++) {
            ReviewSubmissionRequest req = new ReviewSubmissionRequest(
                item.getId(),
                UUID.randomUUID(),
                ReviewRating.AGAIN,
                5
            );
            mockMvc.perform(post("/api/v1/review/grade")
                    .session(sessionA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req))
                    .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                    .header("X-XSRF-TOKEN", MOCK_CSRF))
                .andExpect(status().isOk());
        }

        SrsItem updated = srsItemRepository.findById(item.getId()).orElseThrow();
        assertThat(updated.getEaseFactor()).isEqualByComparingTo("1.30");
    }

    // =========================================================================
    // 3. Idempotency & Replay Semantics
    // =========================================================================

    @Test
    @DisplayName("Identical retry replays original response with Idempotent-Replay header and zero mutation")
    void testExactRetryReplaysWithoutMutation() throws Exception {
        Vocabulary v = createVocab(userA, "resilience", "noun");
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());
        SrsItem item = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v.getId()).orElseThrow();

        UUID attemptId = UUID.randomUUID();
        ReviewSubmissionRequest req = new ReviewSubmissionRequest(item.getId(), attemptId, ReviewRating.GOOD, 12);

        // First attempt
        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Idempotent-Replay"))
            .andExpect(jsonPath("$.newIntervalDays").value(1));

        assertThat(srsReviewHistoryRepository.countByUserId(userA.getId())).isEqualTo(1);
        assertThat(learningActivityEventRepository.countByUserId(userA.getId())).isEqualTo(1);

        // Second attempt (identical retry)
        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk())
            .andExpect(header().string("Idempotent-Replay", "true"))
            .andExpect(jsonPath("$.newIntervalDays").value(1));

        // Verifications: Exactly 1 history record, 1 activity event, item unchanged
        assertThat(srsReviewHistoryRepository.countByUserId(userA.getId())).isEqualTo(1);
        assertThat(learningActivityEventRepository.countByUserId(userA.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Reused attempt ID with different grade returns HTTP 409 IDEMPOTENCY_CONFLICT")
    void testMismatchedRetryReturns409Conflict() throws Exception {
        Vocabulary v = createVocab(userA, "paradox", "noun");
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());
        SrsItem item = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v.getId()).orElseThrow();

        UUID attemptId = UUID.randomUUID();
        ReviewSubmissionRequest originalReq = new ReviewSubmissionRequest(item.getId(), attemptId, ReviewRating.GOOD, 10);
        ReviewSubmissionRequest conflictingReq = new ReviewSubmissionRequest(item.getId(), attemptId, ReviewRating.EASY, 10);

        // First attempt succeeds
        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(originalReq))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk());

        // Conflicting attempt fails with 409
        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(conflictingReq))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    @DisplayName("Valid subsequent review with fresh attempt_id successfully advances the card again")
    void testValidSubsequentReviewAdvancesSchedule() throws Exception {
        Vocabulary v = createVocab(userA, "metaphor", "noun");
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());
        SrsItem item = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v.getId()).orElseThrow();

        // First review (GOOD: interval 1)
        UUID attempt1 = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReviewSubmissionRequest(item.getId(), attempt1, ReviewRating.GOOD, 5)))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.newIntervalDays").value(1));

        // Second review (GOOD: interval 6)
        UUID attempt2 = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReviewSubmissionRequest(item.getId(), attempt2, ReviewRating.GOOD, 7)))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.newIntervalDays").value(6));

        assertThat(srsReviewHistoryRepository.countByUserId(userA.getId())).isEqualTo(2);
        assertThat(learningActivityEventRepository.countByUserId(userA.getId())).isEqualTo(2);
    }

    // =========================================================================
    // 4. Concurrency Safety Tests (Real PostgreSQL Container Multi-Threading)
    // =========================================================================

    @Test
    @DisplayName("Two concurrent identical submissions result in exactly ONE schedule transition and replay")
    void testConcurrentIdenticalSubmissions() throws Exception {
        Vocabulary v = createVocab(userA, "concurrency", "noun");
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());
        SrsItem item = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v.getId()).orElseThrow();

        UUID attemptId = UUID.randomUUID();
        ReviewSubmissionRequest req = new ReviewSubmissionRequest(item.getId(), attemptId, ReviewRating.GOOD, 10);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        Callable<SubmissionResult> task = () -> {
            latch.await();
            return reviewService.submitGrade(userA.getId(), req);
        };

        Future<SubmissionResult> future1 = executor.submit(task);
        Future<SubmissionResult> future2 = executor.submit(task);

        // Fire both threads simultaneously
        latch.countDown();

        SubmissionResult res1 = future1.get(10, TimeUnit.SECONDS);
        SubmissionResult res2 = future2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one should be initial (isReplay=false) and one should be replay (isReplay=true)
        int replayCount = (res1.isReplay() ? 1 : 0) + (res2.isReplay() ? 1 : 0);
        assertThat(replayCount).isEqualTo(1);

        // Exactly ONE history record and ONE activity event persisted
        assertThat(srsReviewHistoryRepository.countByUserId(userA.getId())).isEqualTo(1);
        assertThat(learningActivityEventRepository.countByUserId(userA.getId())).isEqualTo(1);

        // SRS item updated exactly once
        SrsItem updated = srsItemRepository.findById(item.getId()).orElseThrow();
        assertThat(updated.getRepetitionCount()).isEqualTo(1);
        assertThat(updated.getIntervalDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("Two concurrent conflicting submissions result in one success and one conflict rejection")
    void testConcurrentConflictingSubmissions() throws Exception {
        Vocabulary v = createVocab(userA, "collision", "noun");
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());
        SrsItem item = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v.getId()).orElseThrow();

        UUID attemptId = UUID.randomUUID();
        ReviewSubmissionRequest req1 = new ReviewSubmissionRequest(item.getId(), attemptId, ReviewRating.GOOD, 10);
        ReviewSubmissionRequest req2 = new ReviewSubmissionRequest(item.getId(), attemptId, ReviewRating.EASY, 10);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        Callable<SubmissionResult> task1 = () -> {
            latch.await();
            return reviewService.submitGrade(userA.getId(), req1);
        };

        Callable<SubmissionResult> task2 = () -> {
            latch.await();
            return reviewService.submitGrade(userA.getId(), req2);
        };

        Future<SubmissionResult> future1 = executor.submit(task1);
        Future<SubmissionResult> future2 = executor.submit(task2);

        latch.countDown();

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        try {
            future1.get(10, TimeUnit.SECONDS);
            successes.incrementAndGet();
        } catch (ExecutionException ex) {
            conflicts.incrementAndGet();
        }

        try {
            future2.get(10, TimeUnit.SECONDS);
            successes.incrementAndGet();
        } catch (ExecutionException ex) {
            conflicts.incrementAndGet();
        }
        executor.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);

        // State remained completely consistent: exactly 1 history row, 1 activity event
        assertThat(srsReviewHistoryRepository.countByUserId(userA.getId())).isEqualTo(1);
        assertThat(learningActivityEventRepository.countByUserId(userA.getId())).isEqualTo(1);
    }

    // =========================================================================
    // 5. Heatmap Activity Event & Timezone Integrity
    // =========================================================================

    @Test
    @DisplayName("Historical local date remains immutable even if user later changes timezone")
    void testHistoricalLocalDateImmutableAcrossTimezoneChanges() throws Exception {
        Vocabulary v = createVocab(userA, "immutable", "adjective");
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());
        SrsItem item = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v.getId()).orElseThrow();

        UUID attemptId = UUID.randomUUID();
        ReviewSubmissionRequest req = new ReviewSubmissionRequest(item.getId(), attemptId, ReviewRating.GOOD, 15);

        // Submit review while user timezone is Asia/Ho_Chi_Minh
        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk());

        LearningActivityEvent event = learningActivityEventRepository
            .findByUserIdAndEventTypeAndAttemptId(userA.getId(), "FLASHCARD_REVIEW", attemptId)
            .orElseThrow();

        LocalDate originalLocalDate = event.getHistoricalLocalDate();
        assertThat(originalLocalDate).isNotNull();
        assertThat(event.getTimezoneAtEvent()).isEqualTo("Asia/Ho_Chi_Minh");

        // User later changes their profile timezone to Pacific/Honolulu (GMT-10)
        userA.setTimezone("Pacific/Honolulu");
        userRepository.save(userA);

        // Retrieve the event again from PostgreSQL
        LearningActivityEvent reloaded = learningActivityEventRepository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getHistoricalLocalDate()).isEqualTo(originalLocalDate);
        assertThat(reloaded.getTimezoneAtEvent()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    // =========================================================================
    // 6. Additional Concurrency & Edge Cases (Scenarios C, E, F & Lifecycle)
    // =========================================================================

    @Test
    @DisplayName("Scenario C: Two concurrent submissions targeting DIFFERENT cards with the SAME attemptId")
    void testConcurrentDifferentCardsSameAttemptId() throws Exception {
        Vocabulary v1 = createVocab(userA, "card-one", "noun");
        Vocabulary v2 = createVocab(userA, "card-two", "noun");
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());

        SrsItem item1 = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v1.getId()).orElseThrow();
        SrsItem item2 = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v2.getId()).orElseThrow();

        UUID sharedAttemptId = UUID.randomUUID();
        ReviewSubmissionRequest req1 = new ReviewSubmissionRequest(item1.getId(), sharedAttemptId, ReviewRating.GOOD, 5);
        ReviewSubmissionRequest req2 = new ReviewSubmissionRequest(item2.getId(), sharedAttemptId, ReviewRating.GOOD, 5);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Callable<SubmissionResult> task1 = () -> {
            latch.await();
            return reviewService.submitGrade(userA.getId(), req1);
        };
        Callable<SubmissionResult> task2 = () -> {
            latch.await();
            return reviewService.submitGrade(userA.getId(), req2);
        };

        Future<SubmissionResult> future1 = executor.submit(task1);
        Future<SubmissionResult> future2 = executor.submit(task2);

        latch.countDown();

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        try {
            future1.get(10, TimeUnit.SECONDS);
            successes.incrementAndGet();
        } catch (ExecutionException ex) {
            conflicts.incrementAndGet();
        }

        try {
            future2.get(10, TimeUnit.SECONDS);
            successes.incrementAndGet();
        } catch (ExecutionException ex) {
            conflicts.incrementAndGet();
        }
        executor.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);

        // Exactly ONE history record and ONE activity event in the database
        assertThat(srsReviewHistoryRepository.countByUserId(userA.getId())).isEqualTo(1);
        assertThat(learningActivityEventRepository.countByUserId(userA.getId())).isEqualTo(1);

        // Crucial invariant: Exactly one card was updated, and the losing card did NOT advance
        SrsItem updated1 = srsItemRepository.findById(item1.getId()).orElseThrow();
        SrsItem updated2 = srsItemRepository.findById(item2.getId()).orElseThrow();
        int totalRepetitions = updated1.getRepetitionCount() + updated2.getRepetitionCount();
        assertThat(totalRepetitions).isEqualTo(1);
    }

    @Test
    @DisplayName("Scenario E: Historical replay reproduces original outcome A without leaking later schedule state")
    void testHistoricalReplayReproducesOriginalOutcome() throws Exception {
        Vocabulary v = createVocab(userA, "historical-term", "noun");
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());
        SrsItem item = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v.getId()).orElseThrow();

        // 1. First review with attempt A (GOOD -> interval 1)
        UUID attemptA = UUID.randomUUID();
        MvcResult resA = mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReviewSubmissionRequest(item.getId(), attemptA, ReviewRating.GOOD, 10)))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.newIntervalDays").value(1))
            .andReturn();

        String responseA = resA.getResponse().getContentAsString();

        // 2. Second review with attempt B (GOOD -> interval 6)
        UUID attemptB = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReviewSubmissionRequest(item.getId(), attemptB, ReviewRating.GOOD, 8)))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.newIntervalDays").value(6));

        // Card is now currently at interval 6
        SrsItem currentItem = srsItemRepository.findById(item.getId()).orElseThrow();
        assertThat(currentItem.getIntervalDays()).isEqualTo(6);

        // 3. Retry attempt A
        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReviewSubmissionRequest(item.getId(), attemptA, ReviewRating.GOOD, 10)))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk())
            .andExpect(header().string("Idempotent-Replay", "true"))
            .andExpect(jsonPath("$.newIntervalDays").value(1)) // Must reproduce original outcome A!
            .andExpect(jsonPath("$.attemptId").value(attemptA.toString()));

        // Card remains at interval 6, exactly 2 histories and 2 activity events exist
        assertThat(srsItemRepository.findById(item.getId()).orElseThrow().getIntervalDays()).isEqualTo(6);
        assertThat(srsReviewHistoryRepository.countByUserId(userA.getId())).isEqualTo(2);
        assertThat(learningActivityEventRepository.countByUserId(userA.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("Scenario F: Materially changed durationSeconds with same attemptId returns HTTP 409 Conflict")
    void testMismatchedDurationReturns409Conflict() throws Exception {
        Vocabulary v = createVocab(userA, "duration-test", "noun");
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());
        SrsItem item = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v.getId()).orElseThrow();

        UUID attemptId = UUID.randomUUID();
        ReviewSubmissionRequest req1 = new ReviewSubmissionRequest(item.getId(), attemptId, ReviewRating.GOOD, 10);
        ReviewSubmissionRequest req2 = new ReviewSubmissionRequest(item.getId(), attemptId, ReviewRating.GOOD, 35); // duration changed

        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    @DisplayName("Soft-deleted word suspends card and cannot be graded even with old card ID")
    void testDeletedWordSuspendsCardAndCannotBeGraded() throws Exception {
        Vocabulary v = createVocab(userA, "ephemeral-term", "noun");
        mockMvc.perform(get("/api/v1/review/queue").session(sessionA)).andExpect(status().isOk());
        SrsItem item = srsItemRepository.findByUserIdAndVocabularyId(userA.getId(), v.getId()).orElseThrow();

        // Soft-delete vocabulary via VocabularyController / VocabularyService
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/vocabulary/" + v.getId())
                .session(sessionA)
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isNoContent());

        // Verify SRS item is now SUSPENDED in database
        SrsItem suspendedItem = srsItemRepository.findById(item.getId()).orElseThrow();
        assertThat(suspendedItem.getState()).isEqualTo("SUSPENDED");

        // Attempting to grade the suspended card yields 404 NOT_FOUND
        ReviewSubmissionRequest req = new ReviewSubmissionRequest(item.getId(), UUID.randomUUID(), ReviewRating.GOOD, 10);
        mockMvc.perform(post("/api/v1/review/grade")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .cookie(new Cookie("XSRF-TOKEN", MOCK_CSRF))
                .header("X-XSRF-TOKEN", MOCK_CSRF))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
