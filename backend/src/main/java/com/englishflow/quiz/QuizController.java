package com.englishflow.quiz;

import com.englishflow.quiz.dto.QuizCompleteRequest;
import com.englishflow.quiz.dto.QuizCompleteResponse;
import com.englishflow.quiz.dto.QuizHistoryItemDto;
import com.englishflow.quiz.dto.QuizStartRequest;
import com.englishflow.quiz.dto.QuizStartResponse;
import com.englishflow.quiz.dto.QuizSubmissionResult;
import com.englishflow.quiz.dto.QuizSubmitAnswerRequest;
import com.englishflow.quiz.dto.QuizSubmitAnswerResponse;
import com.englishflow.quiz.dto.QuizSummaryResponse;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quizzes")
public class QuizController {

    private final QuizService quizService;
    private final UserRepository userRepository;

    public QuizController(QuizService quizService, UserRepository userRepository) {
        this.quizService = quizService;
        this.userRepository = userRepository;
    }

    private UUID getAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BadCredentialsException("Unauthenticated");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
            .map(User::getId)
            .orElseThrow(() -> new BadCredentialsException("User not found: " + email));
    }

    /**
     * Lists available diagnostic quizzes with question counts and user performance.
     */
    @GetMapping
    public ResponseEntity<List<QuizSummaryResponse>> listQuizzes(Authentication authentication) {
        UUID userId = getAuthenticatedUserId(authentication);
        List<QuizSummaryResponse> quizzes = quizService.listQuizzes(userId);
        return ResponseEntity.ok(quizzes);
    }

    /**
     * Retrieves metadata for a specific quiz pack.
     */
    @GetMapping("/{contentId}")
    public ResponseEntity<QuizSummaryResponse> getQuizSummary(
        @PathVariable UUID contentId,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        QuizSummaryResponse summary = quizService.getQuizSummary(contentId, userId);
        return ResponseEntity.ok(summary);
    }

    /**
     * Initializes or resumes a quiz attempt. Correct answers and explanations are strictly withheld.
     */
    @PostMapping("/{contentId}/start")
    public ResponseEntity<QuizStartResponse> startQuiz(
        @PathVariable UUID contentId,
        @RequestBody(required = false) QuizStartRequest request,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        QuizStartResponse response = quizService.startQuiz(userId, contentId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Submits an individual question draft answer while navigating.
     * Withholds answer evaluation until completion.
     */
    @PostMapping("/attempts/{quizAttemptId}/answers")
    public ResponseEntity<QuizSubmitAnswerResponse> submitAnswer(
        @PathVariable UUID quizAttemptId,
        @RequestBody QuizSubmitAnswerRequest request,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        QuizSubmitAnswerResponse response = quizService.submitAnswer(userId, quizAttemptId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Finalizes and grades a quiz attempt.
     * Enforces transactional idempotency and returns full scorecard with explanations.
     */
    @PostMapping("/attempts/{quizAttemptId}/complete")
    public ResponseEntity<QuizCompleteResponse> completeQuiz(
        @PathVariable UUID quizAttemptId,
        @RequestBody(required = false) QuizCompleteRequest request,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        QuizSubmissionResult result = quizService.completeQuiz(userId, quizAttemptId, request);

        HttpHeaders headers = new HttpHeaders();
        if (result.isReplay()) {
            headers.add("Idempotent-Replay", "true");
        }

        return ResponseEntity.ok().headers(headers).body(result.response());
    }

    /**
     * Retrieves the completed scorecard snapshot for review.
     * Strictly read-only; zero side effects.
     */
    @GetMapping("/attempts/{quizAttemptId}")
    public ResponseEntity<QuizCompleteResponse> getCompletedAttempt(
        @PathVariable UUID quizAttemptId,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        QuizCompleteResponse response = quizService.getCompletedAttempt(userId, quizAttemptId);
        return ResponseEntity.ok(response);
    }

    /**
     * Lists historical completed quiz attempts for the authenticated user.
     */
    @GetMapping("/history")
    public ResponseEntity<List<QuizHistoryItemDto>> getUserHistory(Authentication authentication) {
        UUID userId = getAuthenticatedUserId(authentication);
        List<QuizHistoryItemDto> history = quizService.getUserHistory(userId);
        return ResponseEntity.ok(history);
    }
}
