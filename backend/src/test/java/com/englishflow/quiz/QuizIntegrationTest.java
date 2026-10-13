package com.englishflow.quiz;

import com.englishflow.analytics.AnalyticsService;
import com.englishflow.analytics.LearningActivityEvent;
import com.englishflow.analytics.LearningActivityEventRepository;
import com.englishflow.analytics.dto.DashboardSummaryResponse;
import com.englishflow.dictation.LearningContentRepository;
import com.englishflow.quiz.dto.QuizAnswerItemDto;
import com.englishflow.quiz.dto.QuizCompleteRequest;
import com.englishflow.quiz.dto.QuizStartRequest;
import com.englishflow.quiz.dto.QuizSubmitAnswerRequest;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class QuizIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LearningContentRepository learningContentRepository;

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private QuizAnswerRepository quizAnswerRepository;

    @Autowired
    private LearningActivityEventRepository learningActivityEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User userA;
    private User userB;

    private static final UUID QUIZ_1_ID = UUID.fromString("c1000000-0000-0000-0000-000000000001");
    private static final UUID QUIZ_2_ID = UUID.fromString("c2000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        learningActivityEventRepository.deleteAll();
        quizAnswerRepository.deleteAll();
        quizAttemptRepository.deleteAll();
        userRepository.deleteAll();

        userA = userRepository.save(new User(
            "quiz.a@example.com",
            passwordEncoder.encode("Password123!"),
            "Learner A",
            "Asia/Ho_Chi_Minh",
            20
        ));

        userB = userRepository.save(new User(
            "quiz.b@example.com",
            passwordEncoder.encode("Password123!"),
            "Learner B",
            "America/New_York",
            15
        ));
    }

    @AfterEach
    void tearDown() {
        learningActivityEventRepository.deleteAll();
        quizAnswerRepository.deleteAll();
        quizAttemptRepository.deleteAll();
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
    @DisplayName("1. Authenticated quiz listing returns available quiz packs with metadata")
    void testAuthenticatedQuizListing() throws Exception {
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/quizzes"), userA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].cefrLevel", is("B1")))
            .andExpect(jsonPath("$[0].title", containsString("Essential Workplace")))
            .andExpect(jsonPath("$[0].questionCount", is(6)))
            .andExpect(jsonPath("$[0].attemptCount", is(0)))
            .andExpect(jsonPath("$[1].cefrLevel", is("B2")))
            .andExpect(jsonPath("$[1].title", containsString("Advanced Lexical")))
            .andExpect(jsonPath("$[1].questionCount", is(6)));
    }

    @Test
    @DisplayName("2. Anonymous access to quizzes is rejected with 401 Unauthorized")
    void testAnonymousAccessRejected() throws Exception {
        mockMvc.perform(get("/api/v1/quizzes"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("3. Cross-user attempt isolation: User B cannot submit or view User A's attempt")
    void testCrossUserAttemptIsolation() throws Exception {
        UUID attemptId = UUID.randomUUID();
        // User A starts quiz
        MvcResult startRes = mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode startJson = objectMapper.readTree(startRes.getResponse().getContentAsString());
        UUID quizAttemptId = UUID.fromString(startJson.get("quizAttemptId").asText());

        // User B attempts to access User A's attempt -> 404
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/quizzes/attempts/" + quizAttemptId), userB))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("QUIZ_NOT_FOUND")));

        // User B attempts to submit answer to User A's attempt -> 404
        QuizSubmitAnswerRequest subReq = new QuizSubmitAnswerRequest(
            UUID.fromString("c1000000-0000-0000-0001-000000000001"),
            attemptId,
            "adaptable"
        );
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + quizAttemptId + "/answers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(subReq)),
                userB
            ))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("4. Quiz start initializes IN_PROGRESS attempt and returns questions")
    void testQuizStart() throws Exception {
        UUID attemptId = UUID.randomUUID();
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quizAttemptId", notNullValue()))
            .andExpect(jsonPath("$.attemptId", is(attemptId.toString())))
            .andExpect(jsonPath("$.contentId", is(QUIZ_1_ID.toString())))
            .andExpect(jsonPath("$.totalQuestions", is(6)))
            .andExpect(jsonPath("$.questions", hasSize(6)));

        // Verify DB attempt is IN_PROGRESS
        QuizAttempt attempt = quizAttemptRepository.findByUserIdAndAttemptId(userA.getId(), attemptId).orElseThrow();
        assertEquals("IN_PROGRESS", attempt.getStatus());
        assertEquals(0, learningActivityEventRepository.count());
    }

    @Test
    @DisplayName("5. Stable question order: successive starts for same attempt return identical order")
    void testStableQuestionOrder() throws Exception {
        UUID attemptId = UUID.randomUUID();
        MvcResult res1 = mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk())
            .andReturn();

        MvcResult res2 = mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode json1 = objectMapper.readTree(res1.getResponse().getContentAsString());
        JsonNode json2 = objectMapper.readTree(res2.getResponse().getContentAsString());

        for (int i = 0; i < 6; i++) {
            assertEquals(
                json1.get("questions").get(i).get("questionId").asText(),
                json2.get("questions").get(i).get("questionId").asText()
            );
        }
    }

    @Test
    @DisplayName("6. Critical: Zero answer key leakage before submission (prompts & options only)")
    void testNoAnswerKeyBeforeSubmission() throws Exception {
        UUID attemptId = UUID.randomUUID();
        MvcResult startRes = mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk())
            .andReturn();

        String rawContent = startRes.getResponse().getContentAsString();
        // Verify canonical answer "adaptable" is NOT exposed in the questions payload
        assertFalse(rawContent.contains("\"correctAnswer\""));
        assertFalse(rawContent.contains("\"isCorrect\""));
        assertFalse(rawContent.contains("\"explanation\""));
        assertFalse(rawContent.contains("\"canonicalAnswer\""));

        // Also check submit answers endpoint: zero answer leakage
        JsonNode json = objectMapper.readTree(rawContent);
        UUID quizAttemptId = UUID.fromString(json.get("quizAttemptId").asText());
        UUID q1Id = UUID.fromString(json.get("questions").get(0).get("questionId").asText());

        MvcResult ansRes = mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + quizAttemptId + "/answers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizSubmitAnswerRequest(q1Id, attemptId, "adaptable"))),
                userA
            ))
            .andExpect(status().isOk())
            .andReturn();

        String ansContent = ansRes.getResponse().getContentAsString();
        assertFalse(ansContent.contains("\"correctAnswer\""));
        assertFalse(ansContent.contains("\"isCorrect\""));
        assertFalse(ansContent.contains("\"explanation\""));
    }

    @Test
    @DisplayName("7. Valid multiple choice submission saves draft answer")
    void testValidMultipleChoiceSubmission() throws Exception {
        UUID attemptId = UUID.randomUUID();
        MvcResult startRes = mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode startJson = objectMapper.readTree(startRes.getResponse().getContentAsString());
        UUID quizAttemptId = UUID.fromString(startJson.get("quizAttemptId").asText());
        UUID q1Id = UUID.fromString(startJson.get("questions").get(0).get("questionId").asText());

        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + quizAttemptId + "/answers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizSubmitAnswerRequest(q1Id, attemptId, "adaptable"))),
                userA
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questionId", is(q1Id.toString())))
            .andExpect(jsonPath("$.saved", is(true)));

        // Verify stored in quiz_answers
        QuizAnswer answer = quizAnswerRepository.findByQuizAttemptIdAndQuestionId(quizAttemptId, q1Id).orElseThrow();
        assertEquals("adaptable", answer.getUserAnswer());
    }

    @Test
    @DisplayName("8. Invalid option for multiple choice is rejected with 400 Bad Request")
    void testInvalidOptionRejected() throws Exception {
        UUID attemptId = UUID.randomUUID();
        MvcResult startRes = mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode startJson = objectMapper.readTree(startRes.getResponse().getContentAsString());
        UUID quizAttemptId = UUID.fromString(startJson.get("quizAttemptId").asText());
        UUID q1Id = UUID.fromString(startJson.get("questions").get(0).get("questionId").asText());

        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + quizAttemptId + "/answers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizSubmitAnswerRequest(q1Id, attemptId, "nonexistent-option-xyz"))),
                userA
            ))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_OPTION")));
    }

    @Test
    @DisplayName("9. Forged question ID from another quiz is rejected with 400 Bad Request")
    void testForgedQuestionIdRejected() throws Exception {
        UUID attemptId = UUID.randomUUID();
        MvcResult startRes = mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode startJson = objectMapper.readTree(startRes.getResponse().getContentAsString());
        UUID quizAttemptId = UUID.fromString(startJson.get("quizAttemptId").asText());

        // Question ID from Quiz 2
        UUID foreignQId = UUID.fromString("c2000000-0000-0000-0002-000000000001");

        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + quizAttemptId + "/answers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizSubmitAnswerRequest(foreignQId, attemptId, "corroborate"))),
                userA
            ))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_QUESTION")));
    }

    @Test
    @DisplayName("10. Updating answer replaces previous draft answer cleanly")
    void testDraftAnswerUpdate() throws Exception {
        UUID attemptId = UUID.randomUUID();
        MvcResult startRes = mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode startJson = objectMapper.readTree(startRes.getResponse().getContentAsString());
        UUID quizAttemptId = UUID.fromString(startJson.get("quizAttemptId").asText());
        UUID q1Id = UUID.fromString(startJson.get("questions").get(0).get("questionId").asText());

        // First answer
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + quizAttemptId + "/answers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizSubmitAnswerRequest(q1Id, attemptId, "fragile"))),
                userA
            ))
            .andExpect(status().isOk());

        // Changed answer
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + quizAttemptId + "/answers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizSubmitAnswerRequest(q1Id, attemptId, "adaptable"))),
                userA
            ))
            .andExpect(status().isOk());

        QuizAnswer answer = quizAnswerRepository.findByQuizAttemptIdAndQuestionId(quizAttemptId, q1Id).orElseThrow();
        assertEquals("adaptable", answer.getUserAnswer());
    }

    @Test
    @DisplayName("11. Correct scoring grades properly and reveals explanations after completion")
    void testCorrectScoring() throws Exception {
        UUID attemptId = UUID.randomUUID();
        MvcResult startRes = mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode startJson = objectMapper.readTree(startRes.getResponse().getContentAsString());
        UUID quizAttemptId = UUID.fromString(startJson.get("quizAttemptId").asText());

        List<QuizAnswerItemDto> answers = List.of(
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000001"), "adaptable"),
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000002"), "postpone"),
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000003"), "for"),
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000004"), "had received"),
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000005"), "I see your point, but I have a different perspective"),
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000006"), "by")
        );

        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + quizAttemptId + "/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, answers))),
                userA
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scorePercentage", is(100.00)))
            .andExpect(jsonPath("$.correctCount", is(6)))
            .andExpect(jsonPath("$.incorrectCount", is(0)))
            .andExpect(jsonPath("$.totalQuestions", is(6)))
            .andExpect(jsonPath("$.verifiedActivityCreated", is(true)))
            .andExpect(jsonPath("$.questions[0].canonicalAnswer", is("adaptable")))
            .andExpect(jsonPath("$.questions[0].isCorrect", is(true)))
            .andExpect(jsonPath("$.questions[0].explanation", containsString("Resilient")));

        assertEquals(1, learningActivityEventRepository.count());
    }

    @Test
    @DisplayName("12. All incorrect answers report 0.00% score and highlight mistakes")
    void testAllIncorrectScoring() throws Exception {
        UUID attemptId = UUID.randomUUID();
        MvcResult startRes = mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode startJson = objectMapper.readTree(startRes.getResponse().getContentAsString());
        UUID quizAttemptId = UUID.fromString(startJson.get("quizAttemptId").asText());

        List<QuizAnswerItemDto> answers = List.of(
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000001"), "fragile"),
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000002"), "cancel"),
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000003"), "since"),
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000004"), "received"),
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000005"), "You are completely mistaken"),
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000006"), "until")
        );

        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + quizAttemptId + "/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, answers))),
                userA
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scorePercentage", is(0.00)))
            .andExpect(jsonPath("$.correctCount", is(0)))
            .andExpect(jsonPath("$.incorrectCount", is(6)))
            .andExpect(jsonPath("$.questions[0].isCorrect", is(false)))
            .andExpect(jsonPath("$.verifiedActivityCreated", is(true)));

        // Still earns verified activity for completed effort
        assertEquals(1, learningActivityEventRepository.count());
    }

    @Test
    @DisplayName("13. Perfect score calculation is exact with 2 decimal places")
    void testPerfectScore() throws Exception {
        UUID attemptId = UUID.randomUUID();
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_2_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk());

        List<QuizAnswerItemDto> answers = List.of(
            new QuizAnswerItemDto(UUID.fromString("c2000000-0000-0000-0002-000000000001"), "corroborate"),
            new QuizAnswerItemDto(UUID.fromString("c2000000-0000-0000-0002-000000000002"), "lucid"),
            new QuizAnswerItemDto(UUID.fromString("c2000000-0000-0000-0002-000000000003"), "avert"),
            new QuizAnswerItemDto(UUID.fromString("c2000000-0000-0000-0002-000000000004"), "however"),
            new QuizAnswerItemDto(UUID.fromString("c2000000-0000-0000-0002-000000000005"), "argues"),
            new QuizAnswerItemDto(UUID.fromString("c2000000-0000-0000-0002-000000000006"), "into")
        );

        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + attemptId + "/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, answers))),
                userA
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scorePercentage", is(100.00)))
            .andExpect(jsonPath("$.correctCount", is(6)));
    }

    @Test
    @DisplayName("14. Unanswered questions count as 0 points and appear as unanswered in scorecard")
    void testUnansweredQuestionBehavior() throws Exception {
        UUID attemptId = UUID.randomUUID();
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk());

        // Only answer question 1
        List<QuizAnswerItemDto> partialAnswers = List.of(
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000001"), "adaptable")
        );

        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + attemptId + "/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, partialAnswers))),
                userA
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.correctCount", is(1)))
            .andExpect(jsonPath("$.unansweredCount", is(5)))
            .andExpect(jsonPath("$.scorePercentage", is(16.67)));
    }

    @Test
    @DisplayName("15. Fill-in-the-blank normalization handles case, whitespace, and punctuation")
    void testFillInTheBlankNormalization() throws Exception {
        UUID attemptId = UUID.randomUUID();
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk());

        // Cloze with extra spaces and punctuation: "  HAD RECEIVED.  " and "  by! "
        List<QuizAnswerItemDto> answers = List.of(
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000004"), "  HAD  RECEIVED.  "),
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000006"), " BY. ")
        );

        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + attemptId + "/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, answers))),
                userA
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.correctCount", is(2)));
    }

    @Test
    @DisplayName("16. Non-existent quiz pack returns 404 Not Found")
    void testQuizNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/quizzes/" + nonExistentId), userA))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("QUIZ_NOT_FOUND")));
    }

    @Test
    @DisplayName("17. Matrix A: Identical submission replay returns original result with Idempotent-Replay header")
    void testSubmissionReplayMatrixA() throws Exception {
        UUID attemptId = UUID.randomUUID();
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk());

        List<QuizAnswerItemDto> answers = List.of(
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000001"), "adaptable")
        );

        // First completion
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + attemptId + "/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, answers))),
                userA
            ))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Idempotent-Replay"));

        // Exact replay
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + attemptId + "/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, answers))),
                userA
            ))
            .andExpect(status().isOk())
            .andExpect(header().string("Idempotent-Replay", "true"))
            .andExpect(jsonPath("$.correctCount", is(1)));

        // Still exactly 1 event in learning_activity_events
        assertEquals(1, learningActivityEventRepository.count());
    }

    @Test
    @DisplayName("18. Matrix B: Conflicting submission replay with different answers returns 409 Conflict")
    void testConflictingReplayMatrixB() throws Exception {
        UUID attemptId = UUID.randomUUID();
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk());

        List<QuizAnswerItemDto> answers1 = List.of(
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000001"), "adaptable")
        );

        // First completion
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + attemptId + "/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, answers1))),
                userA
            ))
            .andExpect(status().isOk());

        // Conflicting answers replay
        List<QuizAnswerItemDto> conflictingAnswers = List.of(
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000001"), "fragile")
        );

        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + attemptId + "/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, conflictingAnswers))),
                userA
            ))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));
    }

    @Test
    @DisplayName("19. Matrix D: Concurrent identical submissions persist exactly one result and 1 event")
    void testConcurrentIdenticalSubmissions() throws Exception {
        UUID attemptId = UUID.randomUUID();
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk());

        List<QuizAnswerItemDto> answers = List.of(
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000001"), "adaptable"),
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000002"), "postpone")
        );

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> {
                MvcResult res = mockMvc.perform(withAuthAndCsrf(
                        post("/api/v1/quizzes/attempts/" + attemptId + "/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, answers))),
                        userA
                    ))
                    .andReturn();
                return res.getResponse().getStatus();
            });
        }

        List<Future<Integer>> results = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Integer> f : results) {
            assertEquals(200, f.get());
        }

        assertEquals(1, learningActivityEventRepository.count());
    }

    @Test
    @DisplayName("20. Matrix E: Concurrent conflicting submissions accept one and reject conflicting with 409")
    void testConcurrentConflictingSubmissions() throws Exception {
        UUID attemptId = UUID.randomUUID();
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk());

        List<QuizAnswerItemDto> answers1 = List.of(
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000001"), "adaptable")
        );
        List<QuizAnswerItemDto> answers2 = List.of(
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000001"), "fragile")
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> tasks = List.of(
            () -> mockMvc.perform(withAuthAndCsrf(
                    post("/api/v1/quizzes/attempts/" + attemptId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, answers1))),
                    userA
                )).andReturn().getResponse().getStatus(),
            () -> mockMvc.perform(withAuthAndCsrf(
                    post("/api/v1/quizzes/attempts/" + attemptId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, answers2))),
                    userA
                )).andReturn().getResponse().getStatus()
        );

        List<Future<Integer>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get());
        }

        assertTrue(statuses.contains(200));
        assertTrue(statuses.contains(409));
        assertEquals(1, learningActivityEventRepository.count());
    }

    @Test
    @DisplayName("21. Atomic rollback on persistence failure preserves clean state")
    void testAtomicRollback() throws Exception {
        UUID attemptId = UUID.randomUUID();
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk());

        // Submission with invalid option fails atomically
        List<QuizAnswerItemDto> answers = List.of(
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000001"), "illegal-choice-123")
        );

        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + attemptId + "/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, answers))),
                userA
            ))
            .andExpect(status().isBadRequest());

        // Zero events and attempt is still IN_PROGRESS
        assertEquals(0, learningActivityEventRepository.count());
        QuizAttempt attempt = quizAttemptRepository.findByUserIdAndAttemptId(userA.getId(), attemptId).orElseThrow();
        assertEquals("IN_PROGRESS", attempt.getStatus());
    }

    @Test
    @DisplayName("22. Historical result stability: completed scorecard remains immutable if questions change later")
    void testHistoricalScoreImmutability() throws Exception {
        UUID attemptId = UUID.randomUUID();
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk());

        List<QuizAnswerItemDto> answers = List.of(
            new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000001"), "adaptable")
        );

        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + attemptId + "/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, answers))),
                userA
            ))
            .andExpect(status().isOk());

        // Modify underlying question in DB
        QuizQuestion q1 = quizQuestionRepository.findById(UUID.fromString("c1000000-0000-0000-0001-000000000001")).orElseThrow();
        String originalPrompt = q1.getPrompt();
        String originalAnswer = q1.getCorrectAnswer();
        try {
            q1.setPrompt("MODIFIED PROMPT TEXT");
            q1.setCorrectAnswer("rigid");
            quizQuestionRepository.save(q1);

            // Fetch completed scorecard
            mockMvc.perform(withAuthAndCsrf(get("/api/v1/quizzes/attempts/" + attemptId), userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scorePercentage", is(16.67)))
                .andExpect(jsonPath("$.correctCount", is(1)))
                .andExpect(jsonPath("$.questions[0].prompt", not("MODIFIED PROMPT TEXT")))
                .andExpect(jsonPath("$.questions[0].canonicalAnswer", is("adaptable")))
                .andExpect(jsonPath("$.questions[0].isCorrect", is(true)));
        } finally {
            q1.setPrompt(originalPrompt);
            q1.setCorrectAnswer(originalAnswer);
            quizQuestionRepository.save(q1);
        }
    }

    @Test
    @DisplayName("23. Question options stability across attempts")
    void testQuestionOptionsStability() throws Exception {
        QuizQuestion q = quizQuestionRepository.findById(UUID.fromString("c1000000-0000-0000-0001-000000000001")).orElseThrow();
        assertEquals(4, q.getOptions().size());
        assertTrue(q.getOptions().contains("adaptable"));
        assertTrue(q.getOptions().contains("fragile"));
    }

    @Test
    @DisplayName("24. Activity logging semantics: exactly 1 event on complete, 0 on start/draft/replay")
    void testActivityLoggingRules() throws Exception {
        UUID attemptId = UUID.randomUUID();

        // 1. Start -> 0 events
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk());
        assertEquals(0, learningActivityEventRepository.count());

        // 2. Draft answer -> 0 events
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + attemptId + "/answers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizSubmitAnswerRequest(
                        UUID.fromString("c1000000-0000-0000-0001-000000000001"), attemptId, "adaptable"
                    ))),
                userA
            ))
            .andExpect(status().isOk());
        assertEquals(0, learningActivityEventRepository.count());

        // 3. Complete -> exactly 1 event
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + attemptId + "/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, List.of()))),
                userA
            ))
            .andExpect(status().isOk());
        assertEquals(1, learningActivityEventRepository.count());

        // 4. Replay -> still exactly 1 event
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + attemptId + "/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, List.of()))),
                userA
            ))
            .andExpect(status().isOk());
        assertEquals(1, learningActivityEventRepository.count());

        // 5. Read result -> still exactly 1 event
        mockMvc.perform(withAuthAndCsrf(get("/api/v1/quizzes/attempts/" + attemptId), userA))
            .andExpect(status().isOk());
        assertEquals(1, learningActivityEventRepository.count());
    }

    @Test
    @DisplayName("25. Dashboard verified activity update after completed quiz")
    void testDashboardUpdateAfterQuiz() throws Exception {
        UUID attemptId = UUID.randomUUID();
        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/" + QUIZ_1_ID + "/start")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizStartRequest(attemptId))),
                userA
            ))
            .andExpect(status().isOk());

        mockMvc.perform(withAuthAndCsrf(
                post("/api/v1/quizzes/attempts/" + attemptId + "/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new QuizCompleteRequest(attemptId, List.of(
                        new QuizAnswerItemDto(UUID.fromString("c1000000-0000-0000-0001-000000000001"), "adaptable")
                    )))),
                userA
            ))
            .andExpect(status().isOk());

        DashboardSummaryResponse dashboard = analyticsService.getDashboardSummary(userA.getId());
        assertEquals(1, dashboard.dailyGoalCompletedCount());
        assertEquals(1, dashboard.currentStreakDays());
        assertEquals("ACTIVE_STUDIED_TODAY", dashboard.streakStatus());
        assertFalse(dashboard.recentSessions().isEmpty());
    }
}
