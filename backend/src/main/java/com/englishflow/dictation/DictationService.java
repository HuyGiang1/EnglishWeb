package com.englishflow.dictation;

import com.englishflow.analytics.LearningActivityEvent;
import com.englishflow.analytics.LearningActivityEventRepository;
import com.englishflow.dictation.dto.DictationExerciseDetailResponse;
import com.englishflow.dictation.dto.DictationExerciseSummaryResponse;
import com.englishflow.dictation.dto.DictationSubmissionResult;
import com.englishflow.dictation.dto.DictationSubmitRequest;
import com.englishflow.dictation.dto.DictationSubmitResponse;
import com.englishflow.dictation.engine.DictationDiffEngine;
import com.englishflow.dictation.engine.DictationEvaluationResult;
import com.englishflow.dictation.engine.TranscriptNormalizer;
import com.englishflow.dictation.exception.DictationConflictException;
import com.englishflow.dictation.exception.ExerciseNotFoundException;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.englishflow.dictation.engine.DiffToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.englishflow.content.audio.AudioManifestService;
import com.englishflow.dictation.exception.DictationException;

@Service
public class DictationService {

    private final LearningContentRepository learningContentRepository;
    private final DictationAttemptRepository dictationAttemptRepository;
    private final LearningActivityEventRepository learningActivityEventRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final AudioManifestService audioManifestService;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private static final long DICTATION_LOCK_SALT = 0x444943544154454CL; // "DICTATE"

    public DictationService(
        LearningContentRepository learningContentRepository,
        DictationAttemptRepository dictationAttemptRepository,
        LearningActivityEventRepository learningActivityEventRepository,
        UserRepository userRepository,
        Clock clock,
        ObjectMapper objectMapper,
        ResourceLoader resourceLoader,
        AudioManifestService audioManifestService
    ) {
        this.learningContentRepository = learningContentRepository;
        this.dictationAttemptRepository = dictationAttemptRepository;
        this.learningActivityEventRepository = learningActivityEventRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.audioManifestService = audioManifestService;
    }

    /**
     * Lists all published dictation exercises enriched with the authenticated user's completion status
     * and audio availability on this instance.
     */
    @Transactional(readOnly = true)
    public List<DictationExerciseSummaryResponse> listExercises(UUID userId) {
        List<LearningContent> exercises = learningContentRepository
            .findByContentTypeAndIsPublishedTrueOrderByCefrLevelAsc("DICTATION");

        List<DictationExerciseSummaryResponse> summaries = new ArrayList<>(exercises.size());
        for (LearningContent content : exercises) {
            Optional<DictationAttempt> lastAttemptOpt = dictationAttemptRepository
                .findTopByUserIdAndContentIdOrderByCompletedAtDesc(userId, content.getId());

            boolean isCompleted = lastAttemptOpt.isPresent();
            Double lastAccuracy = lastAttemptOpt.map(a -> a.getAccuracyScore().doubleValue()).orElse(null);
            Double duration = content.getAudioDurationSeconds() != null ? content.getAudioDurationSeconds().doubleValue() : null;
            boolean audioAvailable = isAudioAvailable(content.getAudioStorageKey(), content.getTranscriptText());

            summaries.add(new DictationExerciseSummaryResponse(
                content.getId(),
                content.getTitle(),
                content.getCefrLevel(),
                content.getTopicTag(),
                content.getAudioStorageKey(),
                duration,
                isCompleted,
                lastAccuracy,
                audioAvailable
            ));
        }

        return summaries;
    }

    /**
     * Loads a specific dictation exercise by contentId.
     * Withholds canonical transcript text to prevent leaking answers prior to submission.
     */
    @Transactional(readOnly = true)
    public DictationExerciseDetailResponse getExercise(UUID contentId) {
        LearningContent content = learningContentRepository
            .findByIdAndContentTypeAndIsPublishedTrue(contentId, "DICTATION")
            .orElseThrow(() -> new ExerciseNotFoundException("Dictation exercise not found: " + contentId));

        Double duration = content.getAudioDurationSeconds() != null ? content.getAudioDurationSeconds().doubleValue() : null;
        boolean audioAvailable = isAudioAvailable(content.getAudioStorageKey(), content.getTranscriptText());

        return new DictationExerciseDetailResponse(
            content.getId(),
            content.getTitle(),
            content.getCefrLevel(),
            content.getTopicTag(),
            content.getAudioStorageKey(),
            duration,
            audioAvailable
        );
    }

    /**
     * Submits a transcription attempt:
     * - Enforces idempotency on (userId, attemptId)
     * - Replays original response if attemptId matches identical transcript
     * - Rejects with 409 IDEMPOTENCY_CONFLICT if attemptId is reused with mismatched transcript
     * - Evaluates accuracy via Levenshtein difference engine
     * - Persists DictationAttempt
     * - Transactionally records exactly 1 DICTATION_SENTENCE learning activity event
     */
    @Transactional
    public DictationSubmissionResult submitTranscription(UUID userId, UUID contentId, DictationSubmitRequest request) {
        acquireAttemptLock(userId, request.attemptId());

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        LearningContent content = learningContentRepository
            .findByIdAndContentTypeAndIsPublishedTrue(contentId, "DICTATION")
            .orElseThrow(() -> new ExerciseNotFoundException("Dictation exercise not found: " + contentId));

        // 1. Check idempotency on attempt_id
        Optional<DictationAttempt> existingAttemptOpt = dictationAttemptRepository
            .findByUserIdAndAttemptId(userId, request.attemptId());

        if (existingAttemptOpt.isPresent()) {
            DictationAttempt existing = existingAttemptOpt.get();
            String submittedClean = TranscriptNormalizer.clean(request.submittedTranscript());
            String existingClean = TranscriptNormalizer.clean(existing.getSubmittedTranscript());

            boolean contentMatches = existing.getContentId().equals(content.getId());
            boolean transcriptMatches = submittedClean.equals(existingClean);
            int reqDuration = request.durationSeconds() != null ? Math.max(0, request.durationSeconds()) : 0;
            boolean durationMatches = request.durationSeconds() == null || reqDuration == existing.getDurationSeconds();

            if (!contentMatches || !transcriptMatches || !durationMatches) {
                throw new DictationConflictException(
                    "Attempt ID " + request.attemptId() + " was already processed with different submission parameters."
                );
            }

            // Replay evaluation: Return frozen snapshot if present; fallback to dynamic evaluation for legacy rows
            List<DiffToken> replayTokens;
            String canonicalTranscript;

            if (existing.getCanonicalTranscriptSnapshot() != null && existing.getDiffTokens() != null) {
                try {
                    replayTokens = objectMapper.readValue(
                        existing.getDiffTokens(),
                        new TypeReference<List<DiffToken>>() {}
                    );
                    canonicalTranscript = existing.getCanonicalTranscriptSnapshot();
                } catch (Exception ex) {
                    DictationEvaluationResult eval = DictationDiffEngine.evaluate(content.getTranscriptText(), existing.getSubmittedTranscript());
                    replayTokens = eval.diffTokens();
                    canonicalTranscript = content.getTranscriptText();
                }
            } else {
                // Legacy row compatibility: dynamically evaluate against current content
                DictationEvaluationResult eval = DictationDiffEngine.evaluate(content.getTranscriptText(), existing.getSubmittedTranscript());
                replayTokens = eval.diffTokens();
                canonicalTranscript = content.getTranscriptText();
            }

            DictationSubmitResponse response = new DictationSubmitResponse(
                existing.getAttemptId(),
                content.getId(),
                canonicalTranscript,
                existing.getSubmittedTranscript(),
                existing.getAccuracyScore().doubleValue(),
                existing.getWordCount(),
                existing.getErrorCount(),
                existing.getAccuracyScore().doubleValue() >= DictationDiffEngine.PASS_THRESHOLD,
                replayTokens
            );

            return new DictationSubmissionResult(response, true);
        }

        // 2. Fresh submission: verify audio availability before processing
        if (!isAudioAvailable(content.getAudioStorageKey(), content.getTranscriptText())) {
            throw new DictationException(
                "AUDIO_UNAVAILABLE",
                "Cannot submit dictation attempt because exercise audio is unavailable or unapproved in this environment"
            );
        }

        DictationEvaluationResult eval = DictationDiffEngine.evaluate(content.getTranscriptText(), request.submittedTranscript());
        Instant nowUtc = Instant.now(clock);
        ZoneId userZone = resolveUserZone(user.getTimezone());
        LocalDate historicalLocalDate = LocalDate.now(clock.withZone(userZone));
        int durationSeconds = request.durationSeconds() != null ? Math.max(0, request.durationSeconds()) : 0;

        String diffTokensJson;
        try {
            diffTokensJson = objectMapper.writeValueAsString(eval.diffTokens());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize evaluation diff tokens", ex);
        }

        // 3. Persist dictation_attempts row with frozen evaluation snapshot
        DictationAttempt attempt = new DictationAttempt(
            UUID.randomUUID(),
            userId,
            content.getId(),
            request.attemptId(),
            request.submittedTranscript(),
            BigDecimal.valueOf(eval.accuracyScore()),
            eval.wordCount(),
            eval.errorCount(),
            durationSeconds,
            content.getTranscriptText(),
            diffTokensJson,
            nowUtc
        );
        dictationAttemptRepository.save(attempt);

        // 4. Transactionally log exactly 1 DICTATION_SENTENCE learning activity event
        LearningActivityEvent activityEvent = new LearningActivityEvent(
            userId,
            LearningActivityEvent.EVENT_TYPE_DICTATION_SENTENCE,
            request.attemptId(),
            nowUtc,
            user.getTimezone() != null ? user.getTimezone() : "UTC",
            historicalLocalDate,
            durationSeconds
        );
        learningActivityEventRepository.save(activityEvent);

        DictationSubmitResponse response = new DictationSubmitResponse(
            attempt.getAttemptId(),
            content.getId(),
            content.getTranscriptText(),
            attempt.getSubmittedTranscript(),
            eval.accuracyScore(),
            eval.wordCount(),
            eval.errorCount(),
            eval.isPassed(),
            eval.diffTokens()
        );

        return new DictationSubmissionResult(response, false);
    }

    public boolean isAudioAvailable(String storageKey) {
        return isAudioAvailable(storageKey, null);
    }

    public boolean isAudioAvailable(String storageKey, String canonicalTranscript) {
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }
        return audioManifestService.isAudioAvailable(storageKey, canonicalTranscript);
    }

    private void acquireAttemptLock(UUID userId, UUID attemptId) {
        long lockKey = DICTATION_LOCK_SALT ^ userId.getMostSignificantBits() ^ attemptId.getLeastSignificantBits();
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:lockKey)")
            .setParameter("lockKey", lockKey)
            .getSingleResult();
    }

    private ZoneId resolveUserZone(String timezoneStr) {
        if (timezoneStr == null || timezoneStr.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timezoneStr);
        } catch (Exception ex) {
            return ZoneId.of("UTC");
        }
    }
}
