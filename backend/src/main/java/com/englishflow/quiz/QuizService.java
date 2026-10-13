package com.englishflow.quiz;

import com.englishflow.analytics.LearningActivityEvent;
import com.englishflow.analytics.LearningActivityEventRepository;
import com.englishflow.dictation.LearningContent;
import com.englishflow.dictation.LearningContentRepository;
import com.englishflow.quiz.dto.QuizAnswerItemDto;
import com.englishflow.quiz.dto.QuizCompleteRequest;
import com.englishflow.quiz.dto.QuizCompleteResponse;
import com.englishflow.quiz.dto.QuizHistoryItemDto;
import com.englishflow.quiz.dto.QuizQuestionDto;
import com.englishflow.quiz.dto.QuizQuestionResultDto;
import com.englishflow.quiz.dto.QuizSavedAnswerDto;
import com.englishflow.quiz.dto.QuizStartRequest;
import com.englishflow.quiz.dto.QuizStartResponse;
import com.englishflow.quiz.dto.QuizSubmissionResult;
import com.englishflow.quiz.dto.QuizSubmitAnswerRequest;
import com.englishflow.quiz.dto.QuizSubmitAnswerResponse;
import com.englishflow.quiz.dto.QuizSummaryResponse;
import com.englishflow.quiz.exception.QuizConflictException;
import com.englishflow.quiz.exception.QuizException;
import com.englishflow.quiz.exception.QuizNotFoundException;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class QuizService {

    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final LearningContentRepository learningContentRepository;
    private final LearningActivityEventRepository learningActivityEventRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final EntityManager entityManager;

    public QuizService(
        QuizQuestionRepository quizQuestionRepository,
        QuizAttemptRepository quizAttemptRepository,
        QuizAnswerRepository quizAnswerRepository,
        LearningContentRepository learningContentRepository,
        LearningActivityEventRepository learningActivityEventRepository,
        UserRepository userRepository,
        Clock clock,
        EntityManager entityManager
    ) {
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.quizAnswerRepository = quizAnswerRepository;
        this.learningContentRepository = learningContentRepository;
        this.learningActivityEventRepository = learningActivityEventRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    /**
     * Lists all published quiz packs with question counts and the authenticated user's attempt statistics.
     */
    @Transactional(readOnly = true)
    public List<QuizSummaryResponse> listQuizzes(UUID userId) {
        List<LearningContent> quizzes = learningContentRepository
            .findByContentTypeAndIsPublishedTrueOrderByCefrLevelAsc("QUIZ");

        List<String> cefrOrder = List.of("A1", "A2", "B1", "B2", "C1", "C2");
        quizzes.sort(Comparator.comparingInt(c -> {
            int idx = cefrOrder.indexOf(c.getCefrLevel());
            return idx >= 0 ? idx : 99;
        }));

        List<QuizSummaryResponse> result = new ArrayList<>();
        for (LearningContent quiz : quizzes) {
            int questionCount = (int) quizQuestionRepository.countByContentId(quiz.getId());
            int attemptCount = (int) quizAttemptRepository
                .countByUserIdAndContentIdAndStatus(userId, quiz.getId(), QuizAttempt.STATUS_COMPLETED);

            Optional<QuizAttempt> bestAttempt = quizAttemptRepository
                .findFirstByUserIdAndContentIdAndStatusOrderByScorePercentageDesc(userId, quiz.getId(), QuizAttempt.STATUS_COMPLETED);
            BigDecimal bestScore = bestAttempt.map(QuizAttempt::getScorePercentage).orElse(null);

            Optional<QuizAttempt> latestAttempt = quizAttemptRepository
                .findFirstByUserIdAndContentIdAndStatusOrderByCompletedAtDesc(userId, quiz.getId(), QuizAttempt.STATUS_COMPLETED);
            BigDecimal latestScore = latestAttempt.map(QuizAttempt::getScorePercentage).orElse(null);

            result.add(new QuizSummaryResponse(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getCefrLevel(),
                quiz.getTopicTag(),
                quiz.getTranscriptText(), // Quiz description stored in transcriptText
                questionCount,
                attemptCount,
                bestScore,
                latestScore
            ));
        }

        return result;
    }

    /**
     * Retrieves summary metadata for a specific quiz pack.
     */
    @Transactional(readOnly = true)
    public QuizSummaryResponse getQuizSummary(UUID contentId, UUID userId) {
        LearningContent quiz = learningContentRepository.findByIdAndContentTypeAndIsPublishedTrue(contentId, "QUIZ")
            .orElseThrow(() -> new QuizNotFoundException("Quiz not found with id: " + contentId));

        int questionCount = (int) quizQuestionRepository.countByContentId(quiz.getId());
        int attemptCount = (int) quizAttemptRepository
            .countByUserIdAndContentIdAndStatus(userId, quiz.getId(), QuizAttempt.STATUS_COMPLETED);

        Optional<QuizAttempt> bestAttempt = quizAttemptRepository
            .findFirstByUserIdAndContentIdAndStatusOrderByScorePercentageDesc(userId, quiz.getId(), QuizAttempt.STATUS_COMPLETED);
        BigDecimal bestScore = bestAttempt.map(QuizAttempt::getScorePercentage).orElse(null);

        Optional<QuizAttempt> latestAttempt = quizAttemptRepository
            .findFirstByUserIdAndContentIdAndStatusOrderByCompletedAtDesc(userId, quiz.getId(), QuizAttempt.STATUS_COMPLETED);
        BigDecimal latestScore = latestAttempt.map(QuizAttempt::getScorePercentage).orElse(null);

        return new QuizSummaryResponse(
            quiz.getId(),
            quiz.getTitle(),
            quiz.getCefrLevel(),
            quiz.getTopicTag(),
            quiz.getTranscriptText(),
            questionCount,
            attemptCount,
            bestScore,
            latestScore
        );
    }

    /**
     * Initializes or resumes a quiz attempt.
     * Enforces answer concealment: correct answers and explanations are strictly excluded.
     */
    @Transactional
    public QuizStartResponse startQuiz(UUID userId, UUID contentId, QuizStartRequest request) {
        LearningContent quiz = learningContentRepository.findByIdAndContentTypeAndIsPublishedTrue(contentId, "QUIZ")
            .orElseThrow(() -> new QuizNotFoundException("Quiz not found with id: " + contentId));

        UUID clientAttemptId = (request != null && request.attemptId() != null)
            ? request.attemptId()
            : UUID.randomUUID();

        // Transaction-scoped advisory lock on (userId, attemptId)
        String lockKey = "quiz:" + userId + ":" + clientAttemptId;
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(?))")
            .setParameter(1, lockKey)
            .getSingleResult();

        Optional<QuizAttempt> existingOpt = quizAttemptRepository.findByUserIdAndAttemptId(userId, clientAttemptId);
        QuizAttempt attempt;

        if (existingOpt.isPresent()) {
            attempt = existingOpt.get();
            if (!attempt.getContentId().equals(contentId)) {
                throw new QuizConflictException("Conflicting attempt_id reuse for attempt " + clientAttemptId + " with different quiz content");
            }
            if (QuizAttempt.STATUS_COMPLETED.equals(attempt.getStatus())) {
                throw new QuizConflictException("Attempt " + clientAttemptId + " has already been completed");
            }
        } else {
            List<QuizQuestion> questions = quizQuestionRepository.findByContentIdOrderByOrderIndexAsc(contentId);
            if (questions.isEmpty()) {
                throw new QuizException("NO_QUESTIONS", "Quiz pack does not contain any questions");
            }

            attempt = new QuizAttempt(
                UUID.randomUUID(),
                userId,
                contentId,
                clientAttemptId,
                questions.size(),
                0,
                BigDecimal.ZERO,
                QuizAttempt.STATUS_IN_PROGRESS,
                Instant.now(clock),
                null
            );
            quizAttemptRepository.save(attempt);
        }

        // Fetch questions in stable order
        List<QuizQuestion> questions = quizQuestionRepository.findByContentIdOrderByOrderIndexAsc(contentId);
        List<QuizQuestionDto> questionDtos = new ArrayList<>(questions.size());
        for (QuizQuestion q : questions) {
            questionDtos.add(new QuizQuestionDto(
                q.getId(),
                q.getPrompt(),
                q.getQuestionType(),
                new ArrayList<>(q.getOptions()),
                q.getOrderIndex()
            ));
        }

        // Fetch previously saved draft answers if resuming
        List<QuizAnswer> saved = quizAnswerRepository.findByQuizAttemptIdOrderByAnsweredAtAsc(attempt.getId());
        List<QuizSavedAnswerDto> savedAnswerDtos = new ArrayList<>(saved.size());
        for (QuizAnswer sa : saved) {
            savedAnswerDtos.add(new QuizSavedAnswerDto(sa.getQuestionId(), sa.getUserAnswer()));
        }

        return new QuizStartResponse(
            attempt.getId(),
            attempt.getAttemptId(),
            contentId,
            quiz.getTitle(),
            questions.size(),
            questionDtos,
            savedAnswerDtos
        );
    }

    /**
     * Submits an individual question draft answer while navigating.
     * Withholds canonical answers and correctness to prevent test leakage.
     */
    @Transactional
    public QuizSubmitAnswerResponse submitAnswer(UUID userId, UUID quizAttemptIdentifier, QuizSubmitAnswerRequest request) {
        if (request == null || request.questionId() == null || request.selectedAnswer() == null) {
            throw new QuizException("VALIDATION_FAILED", "questionId and selectedAnswer are required");
        }

        QuizAttempt attempt = resolveAttempt(userId, quizAttemptIdentifier);

        if (QuizAttempt.STATUS_COMPLETED.equals(attempt.getStatus())) {
            throw new QuizConflictException("Cannot submit answers to an already completed quiz attempt");
        }

        QuizQuestion question = quizQuestionRepository.findByIdAndContentId(request.questionId(), attempt.getContentId())
            .orElseThrow(() -> new QuizException("INVALID_QUESTION", "Question does not belong to this quiz"));

        // Validate multiple choice option
        if ("MULTIPLE_CHOICE".equalsIgnoreCase(question.getQuestionType())) {
            boolean validOption = question.getOptions().contains(request.selectedAnswer());
            if (!validOption && !request.selectedAnswer().isBlank()) {
                throw new QuizException("INVALID_OPTION", "Selected answer is not one of the valid options for this question");
            }
        }

        Optional<QuizAnswer> existingAns = quizAnswerRepository
            .findByQuizAttemptIdAndQuestionId(attempt.getId(), question.getId());

        if (existingAns.isPresent()) {
            QuizAnswer ans = existingAns.get();
            ans.setUserAnswer(request.selectedAnswer());
            quizAnswerRepository.save(ans);
        } else {
            QuizAnswer newAns = new QuizAnswer(
                UUID.randomUUID(),
                attempt.getId(),
                question.getId(),
                request.selectedAnswer(),
                false,
                null,
                null,
                null,
                null
            );
            quizAnswerRepository.save(newAns);
        }

        return new QuizSubmitAnswerResponse(question.getId(), true);
    }

    /**
     * Finalizes, grades, and records completion of a quiz attempt.
     * Enforces strict transactional idempotency, freezes historical snapshots,
     * logs exactly ONE learning activity event, and reveals full explanations.
     */
    @Transactional
    public QuizSubmissionResult completeQuiz(UUID userId, UUID quizAttemptIdentifier, QuizCompleteRequest request) {
        QuizAttempt attempt = resolveAttempt(userId, quizAttemptIdentifier);

        // Transaction-scoped PostgreSQL advisory lock on (userId, attemptId)
        String lockKey = "quiz:" + userId + ":" + attempt.getAttemptId();
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(?))")
            .setParameter(1, lockKey)
            .getSingleResult();

        // Refresh attempt within locked transaction to observe concurrent commits
        entityManager.refresh(attempt);

        // Check if already completed (Matrix A - Identical Replay vs Matrix B - Conflict)
        if (QuizAttempt.STATUS_COMPLETED.equals(attempt.getStatus())) {
            // Replay verification
            if (request != null && request.answers() != null && !request.answers().isEmpty()) {
                List<QuizAnswer> existingAnswers = quizAnswerRepository.findByQuizAttemptIdOrderByAnsweredAtAsc(attempt.getId());
                Map<UUID, String> existingMap = new HashMap<>();
                for (QuizAnswer ea : existingAnswers) {
                    existingMap.put(ea.getQuestionId(), ea.getUserAnswer());
                }

                for (QuizAnswerItemDto item : request.answers()) {
                    String prev = existingMap.get(item.questionId());
                    String incoming = item.selectedAnswer() != null ? item.selectedAnswer() : "";
                    String recorded = prev != null ? prev : "";
                    if (!incoming.equals(recorded)) {
                        throw new QuizConflictException(
                            "Conflicting attempt submission with different answers for attempt " + attempt.getAttemptId()
                        );
                    }
                }
            }

            QuizCompleteResponse response = buildCompletedResponse(attempt);
            return new QuizSubmissionResult(response, true);
        }

        // Active IN_PROGRESS attempt: Grade all questions
        List<QuizQuestion> questions = quizQuestionRepository.findByContentIdOrderByOrderIndexAsc(attempt.getContentId());

        // Process any answers submitted directly in the complete request
        if (request != null && request.answers() != null) {
            for (QuizAnswerItemDto item : request.answers()) {
                if (item.questionId() == null) continue;
                QuizQuestion q = quizQuestionRepository.findByIdAndContentId(item.questionId(), attempt.getContentId())
                    .orElseThrow(() -> new QuizException("INVALID_QUESTION", "Question " + item.questionId() + " does not belong to this quiz"));

                if ("MULTIPLE_CHOICE".equalsIgnoreCase(q.getQuestionType())) {
                    String selected = item.selectedAnswer() != null ? item.selectedAnswer() : "";
                    if (!selected.isBlank() && !q.getOptions().contains(selected)) {
                        throw new QuizException("INVALID_OPTION", "Option '" + selected + "' is invalid for question " + item.questionId());
                    }
                }

                Optional<QuizAnswer> opt = quizAnswerRepository.findByQuizAttemptIdAndQuestionId(attempt.getId(), q.getId());
                if (opt.isPresent()) {
                    QuizAnswer ans = opt.get();
                    ans.setUserAnswer(item.selectedAnswer() != null ? item.selectedAnswer() : "");
                    quizAnswerRepository.save(ans);
                } else {
                    QuizAnswer ans = new QuizAnswer(
                        UUID.randomUUID(),
                        attempt.getId(),
                        q.getId(),
                        item.selectedAnswer() != null ? item.selectedAnswer() : "",
                        false,
                        null,
                        null,
                        null,
                        null
                    );
                    quizAnswerRepository.save(ans);
                }
            }
        }

        // Fetch all recorded answers for grading
        List<QuizAnswer> answerList = quizAnswerRepository.findByQuizAttemptIdOrderByAnsweredAtAsc(attempt.getId());
        Map<UUID, QuizAnswer> answerByQuestionId = new HashMap<>();
        for (QuizAnswer a : answerList) {
            answerByQuestionId.put(a.getQuestionId(), a);
        }

        int correctCount = 0;
        int incorrectCount = 0;
        int unansweredCount = 0;

        for (QuizQuestion question : questions) {
            QuizAnswer answer = answerByQuestionId.get(question.getId());
            String userAnswer = (answer != null && answer.getUserAnswer() != null) ? answer.getUserAnswer() : "";

            boolean isCorrect;
            if (userAnswer.isBlank()) {
                isCorrect = false;
                unansweredCount++;
            } else if ("CLOZE".equalsIgnoreCase(question.getQuestionType())) {
                isCorrect = matchesClozeAnswer(userAnswer, question.getCorrectAnswer());
                if (isCorrect) {
                    correctCount++;
                } else {
                    incorrectCount++;
                }
            } else { // MULTIPLE_CHOICE
                isCorrect = question.getCorrectAnswer().trim().equalsIgnoreCase(userAnswer.trim());
                if (isCorrect) {
                    correctCount++;
                } else {
                    incorrectCount++;
                }
            }

            if (answer == null) {
                answer = new QuizAnswer(
                    UUID.randomUUID(),
                    attempt.getId(),
                    question.getId(),
                    userAnswer,
                    isCorrect,
                    question.getPrompt(),
                    question.getCorrectAnswer(),
                    question.getExplanation(),
                    new ArrayList<>(question.getOptions())
                );
            } else {
                answer.setUserAnswer(userAnswer);
                answer.setCorrect(isCorrect);
                answer.setPromptSnapshot(question.getPrompt());
                answer.setCanonicalAnswerSnapshot(question.getCorrectAnswer());
                answer.setExplanationSnapshot(question.getExplanation());
                answer.setOptionsSnapshot(new ArrayList<>(question.getOptions()));
            }
            quizAnswerRepository.save(answer);
        }

        int totalQuestions = questions.size();
        BigDecimal scorePercentage = totalQuestions > 0
            ? BigDecimal.valueOf((correctCount * 100.0) / totalQuestions).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        Instant completedAt = Instant.now(clock);

        attempt.setStatus(QuizAttempt.STATUS_COMPLETED);
        attempt.setTotalQuestions(totalQuestions);
        attempt.setCorrectAnswers(correctCount);
        attempt.setScorePercentage(scorePercentage);
        attempt.setCompletedAt(completedAt);
        quizAttemptRepository.save(attempt);

        // Transactionally log exactly ONE learning activity event
        boolean activityLogged = logQuizActivityEvent(userId, attempt, completedAt);

        QuizCompleteResponse response = buildCompletedResponse(attempt, activityLogged);
        return new QuizSubmissionResult(response, false);
    }

    /**
     * Inspects historical completed scorecard without modifying state or logging events.
     */
    @Transactional(readOnly = true)
    public QuizCompleteResponse getCompletedAttempt(UUID userId, UUID quizAttemptIdentifier) {
        QuizAttempt attempt = resolveAttempt(userId, quizAttemptIdentifier);

        if (!QuizAttempt.STATUS_COMPLETED.equals(attempt.getStatus())) {
            throw new QuizException("ATTEMPT_INCOMPLETE", "Quiz attempt has not been completed");
        }

        return buildCompletedResponse(attempt);
    }

    /**
     * Lists all completed quiz attempts for the authenticated user.
     */
    @Transactional(readOnly = true)
    public List<QuizHistoryItemDto> getUserHistory(UUID userId) {
        List<QuizAttempt> attempts = quizAttemptRepository
            .findByUserIdAndStatusOrderByCompletedAtDesc(userId, QuizAttempt.STATUS_COMPLETED);

        List<QuizHistoryItemDto> result = new ArrayList<>(attempts.size());
        for (QuizAttempt att : attempts) {
            LearningContent content = learningContentRepository.findById(att.getContentId()).orElse(null);
            String title = content != null ? content.getTitle() : "Diagnostic Quiz";
            String cefr = content != null ? content.getCefrLevel() : "B1";
            String topic = content != null ? content.getTopicTag() : "General";

            result.add(new QuizHistoryItemDto(
                att.getAttemptId(),
                att.getId(),
                att.getContentId(),
                title,
                cefr,
                topic,
                att.getScorePercentage(),
                att.getCorrectAnswers(),
                att.getTotalQuestions(),
                att.getCompletedAt()
            ));
        }

        return result;
    }

    private QuizAttempt resolveAttempt(UUID userId, UUID quizAttemptIdentifier) {
        // Try resolving by primary key ID first, then by attempt_id
        Optional<QuizAttempt> byId = quizAttemptRepository.findByIdAndUserId(quizAttemptIdentifier, userId);
        if (byId.isPresent()) {
            return byId.get();
        }

        Optional<QuizAttempt> byAttemptId = quizAttemptRepository.findByUserIdAndAttemptId(userId, quizAttemptIdentifier);
        if (byAttemptId.isPresent()) {
            return byAttemptId.get();
        }

        throw new QuizNotFoundException("Quiz attempt not found with identifier: " + quizAttemptIdentifier);
    }

    private boolean logQuizActivityEvent(UUID userId, QuizAttempt attempt, Instant completedAt) {
        boolean alreadyExists = learningActivityEventRepository
            .existsByUserIdAndEventTypeAndAttemptId(
                userId,
                LearningActivityEvent.EVENT_TYPE_QUIZ_QUESTION,
                attempt.getAttemptId()
            );

        if (alreadyExists) {
            return false;
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        ZoneId userZone = resolveUserZone(user.getTimezone());
        LocalDate localDate = LocalDate.now(clock.withZone(userZone));

        int durationSeconds = 0;
        if (attempt.getStartedAt() != null && attempt.getCompletedAt() != null) {
            durationSeconds = Math.max(1, (int) Duration.between(attempt.getStartedAt(), attempt.getCompletedAt()).toSeconds());
        }

        LearningActivityEvent event = new LearningActivityEvent(
            userId,
            LearningActivityEvent.EVENT_TYPE_QUIZ_QUESTION,
            attempt.getAttemptId(),
            completedAt,
            user.getTimezone() != null ? user.getTimezone() : "UTC",
            localDate,
            durationSeconds
        );

        learningActivityEventRepository.save(event);
        return true;
    }

    private QuizCompleteResponse buildCompletedResponse(QuizAttempt attempt) {
        boolean hadActivity = learningActivityEventRepository
            .existsByUserIdAndEventTypeAndAttemptId(
                attempt.getUserId(),
                LearningActivityEvent.EVENT_TYPE_QUIZ_QUESTION,
                attempt.getAttemptId()
            );
        return buildCompletedResponse(attempt, hadActivity);
    }

    private QuizCompleteResponse buildCompletedResponse(QuizAttempt attempt, boolean activityLogged) {
        LearningContent content = learningContentRepository.findById(attempt.getContentId()).orElse(null);
        String title = content != null ? content.getTitle() : "Diagnostic Quiz";

        List<QuizQuestion> questions = quizQuestionRepository.findByContentIdOrderByOrderIndexAsc(attempt.getContentId());
        List<QuizAnswer> answers = quizAnswerRepository.findByQuizAttemptIdOrderByAnsweredAtAsc(attempt.getId());

        Map<UUID, QuizAnswer> answerMap = new HashMap<>();
        for (QuizAnswer a : answers) {
            answerMap.put(a.getQuestionId(), a);
        }

        List<QuizQuestionResultDto> questionResults = new ArrayList<>(questions.size());
        int correctCount = 0;
        int incorrectCount = 0;
        int unansweredCount = 0;

        for (QuizQuestion q : questions) {
            QuizAnswer ans = answerMap.get(q.getId());

            // Use frozen snapshots from answers if present; fallback to question definitions
            String prompt = (ans != null && ans.getPromptSnapshot() != null) ? ans.getPromptSnapshot() : q.getPrompt();
            String canonical = (ans != null && ans.getCanonicalAnswerSnapshot() != null) ? ans.getCanonicalAnswerSnapshot() : q.getCorrectAnswer();
            String explanation = (ans != null && ans.getExplanationSnapshot() != null) ? ans.getExplanationSnapshot() : q.getExplanation();
            List<String> options = (ans != null && ans.getOptionsSnapshot() != null && !ans.getOptionsSnapshot().isEmpty())
                ? ans.getOptionsSnapshot()
                : q.getOptions();

            String userAns = (ans != null && ans.getUserAnswer() != null) ? ans.getUserAnswer() : "";
            boolean isCorrect = ans != null && ans.isCorrect();

            if (userAns.isBlank()) {
                unansweredCount++;
            } else if (isCorrect) {
                correctCount++;
            } else {
                incorrectCount++;
            }

            questionResults.add(new QuizQuestionResultDto(
                q.getId(),
                prompt,
                q.getQuestionType(),
                options,
                userAns,
                canonical,
                isCorrect,
                explanation,
                q.getOrderIndex()
            ));
        }

        return new QuizCompleteResponse(
            attempt.getId(),
            attempt.getAttemptId(),
            attempt.getContentId(),
            title,
            attempt.getScorePercentage(),
            attempt.getCorrectAnswers(),
            attempt.getTotalQuestions(),
            incorrectCount,
            unansweredCount,
            activityLogged,
            attempt.getCompletedAt(),
            questionResults
        );
    }

    public static boolean matchesClozeAnswer(String userAnswer, String canonicalAnswer) {
        if (userAnswer == null || canonicalAnswer == null) {
            return false;
        }
        String normUser = normalizeAnswer(userAnswer);
        if (normUser.isEmpty()) {
            return false;
        }
        String[] acceptable = canonicalAnswer.split("\\|");
        for (String opt : acceptable) {
            if (normUser.equalsIgnoreCase(normalizeAnswer(opt))) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeAnswer(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().toLowerCase();
        s = s.replaceAll("^[\\\"'\\`]+|[\\\"'\\`]+$", "");
        s = s.replaceAll("[.,!?;:]+$", "");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private ZoneId resolveUserZone(String timezoneStr) {
        if (timezoneStr == null || timezoneStr.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timezoneStr);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }
}
