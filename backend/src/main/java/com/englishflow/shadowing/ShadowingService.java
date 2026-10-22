package com.englishflow.shadowing;

import com.englishflow.analytics.LearningActivityEvent;
import com.englishflow.analytics.LearningActivityEventRepository;
import com.englishflow.dictation.LearningContent;
import com.englishflow.dictation.LearningContentRepository;
import com.englishflow.shadowing.dto.ShadowingCompleteRequest;
import com.englishflow.shadowing.dto.ShadowingCompleteResponse;
import com.englishflow.shadowing.dto.ShadowingExerciseDetailResponse;
import com.englishflow.shadowing.dto.ShadowingExerciseSummaryResponse;
import com.englishflow.shadowing.dto.ShadowingSubmissionResult;
import com.englishflow.shadowing.exception.ShadowingConflictException;
import com.englishflow.shadowing.exception.ShadowingException;
import com.englishflow.shadowing.exception.ShadowingExerciseNotFoundException;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import com.englishflow.content.audio.AudioManifestService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ShadowingService {

    public static final BigDecimal MAX_RECORDING_DURATION_SECONDS = new BigDecimal("65.00");

    private final ShadowingTakeRepository shadowingTakeRepository;
    private final LearningContentRepository learningContentRepository;
    private final LearningActivityEventRepository learningActivityEventRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final EntityManager entityManager;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final AudioManifestService audioManifestService;

    public ShadowingService(
        ShadowingTakeRepository shadowingTakeRepository,
        LearningContentRepository learningContentRepository,
        LearningActivityEventRepository learningActivityEventRepository,
        UserRepository userRepository,
        Clock clock,
        EntityManager entityManager,
        ResourceLoader resourceLoader,
        ObjectMapper objectMapper,
        AudioManifestService audioManifestService
    ) {
        this.shadowingTakeRepository = shadowingTakeRepository;
        this.learningContentRepository = learningContentRepository;
        this.learningActivityEventRepository = learningActivityEventRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.entityManager = entityManager;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.audioManifestService = audioManifestService;
    }

    /**
     * Determines whether the reference audio file actually exists and is available.
     */
    public boolean isAudioAvailable(String storageKey) {
        return isAudioAvailable(storageKey, null);
    }

    public boolean isAudioAvailable(String storageKey, String canonicalTranscript) {
        return audioManifestService.isAudioAvailable(storageKey, canonicalTranscript);
    }

    /**
     * Discovers audio readiness classification (MISSING, SYNTHETIC_FIXTURE, LOCAL_SPOKEN, LICENSED_DISTRIBUTABLE)
     * using the provenance manifest and local file verification.
     */
    public String getAudioReadiness(String storageKey) {
        return audioManifestService.getAudioReadiness(storageKey).name();
    }

    /**
     * Returns true only if the audio asset actually contains intelligible spoken English speech.
     */
    public boolean isSpokenSpeech(String storageKey) {
        return audioManifestService.isSpokenSpeech(storageKey);
    }

    /**
     * List all published shadowing exercises ordered by CEFR difficulty.
     */
    @Transactional(readOnly = true)
    public List<ShadowingExerciseSummaryResponse> listExercises(UUID userId) {
        List<LearningContent> contents = learningContentRepository.findByContentTypeAndIsPublishedTrueOrderByCefrLevelAsc("SHADOWING");

        // Sort predictably by CEFR level
        List<String> cefrOrder = List.of("A1", "A2", "B1", "B2", "C1", "C2");
        contents.sort(Comparator.comparingInt(c -> {
            int idx = cefrOrder.indexOf(c.getCefrLevel());
            return idx >= 0 ? idx : 99;
        }));

        List<ShadowingExerciseSummaryResponse> result = new ArrayList<>();
        for (LearningContent content : contents) {
            long takeCount = shadowingTakeRepository.countByUserIdAndContentId(userId, content.getId());
            Optional<ShadowingTake> latestTake = shadowingTakeRepository.findFirstByUserIdAndContentIdOrderByCompletedAtDesc(userId, content.getId());
            String lastSelfRating = latestTake.map(ShadowingTake::getSelfRating).orElse(null);
            String activeAudioUrl = audioManifestService.resolveActiveAudioUrl(content.getId(), content.getAudioStorageKey());
            String readiness = getAudioReadiness(activeAudioUrl);
            boolean audioAvailable = audioManifestService.isAudioAvailable(content.getId(), content.getAudioStorageKey(), content.getTranscriptText());
            boolean spoken = isSpokenSpeech(activeAudioUrl);

            result.add(new ShadowingExerciseSummaryResponse(
                content.getId(),
                content.getTitle(),
                content.getTopicTag(),
                content.getCefrLevel(),
                content.getAudioDurationSeconds(),
                audioAvailable,
                readiness,
                spoken,
                (int) takeCount,
                lastSelfRating
            ));
        }

        return result;
    }

    /**
     * Load exercise prompt, audio reference, and canonical reference transcript for reading.
     */
    @Transactional(readOnly = true)
    public ShadowingExerciseDetailResponse getExercise(UUID contentId) {
        LearningContent content = learningContentRepository.findByIdAndContentTypeAndIsPublishedTrue(contentId, "SHADOWING")
            .orElseThrow(() -> new ShadowingExerciseNotFoundException("Shadowing exercise not found with id: " + contentId));

        String activeAudioUrl = audioManifestService.resolveActiveAudioUrl(content.getId(), content.getAudioStorageKey());
        String readiness = getAudioReadiness(activeAudioUrl);
        boolean audioAvailable = audioManifestService.isAudioAvailable(content.getId(), content.getAudioStorageKey(), content.getTranscriptText());
        boolean spoken = isSpokenSpeech(activeAudioUrl);

        return new ShadowingExerciseDetailResponse(
            content.getId(),
            content.getTitle(),
            content.getTopicTag(),
            content.getCefrLevel(),
            content.getTranscriptText(),
            activeAudioUrl,
            content.getAudioDurationSeconds(),
            audioAvailable,
            readiness,
            spoken
        );
    }

    /**
     * Records completed practice take with self-assessment rating.
     * Enforces transactional idempotency via PostgreSQL advisory locks on (user_id, attempt_id)
     * and strict equality comparison on contentId, selfRating, and durationSeconds.
     */
    @Transactional
    public ShadowingSubmissionResult completeTake(UUID userId, UUID contentId, ShadowingCompleteRequest request) {
        // 1. Server-side duration validation
        if (request.getDurationSeconds() == null) {
            throw new ShadowingException("VALIDATION_FAILED", "durationSeconds is required and must be positive");
        }
        BigDecimal rawDuration = request.getDurationSeconds();
        if (rawDuration.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ShadowingException("VALIDATION_FAILED", "durationSeconds must be positive");
        }
        if (rawDuration.compareTo(MAX_RECORDING_DURATION_SECONDS) > 0) {
            throw new ShadowingException("VALIDATION_FAILED", "durationSeconds cannot exceed maximum permitted recording duration of " + MAX_RECORDING_DURATION_SECONDS + " seconds");
        }
        BigDecimal stripped = rawDuration.stripTrailingZeros();
        if (stripped.scale() > 2) {
            throw new ShadowingException("VALIDATION_FAILED", "durationSeconds precision cannot exceed 2 decimal places");
        }
        BigDecimal canonicalDuration = rawDuration.setScale(2, RoundingMode.HALF_UP);

        // 2. Transaction-scoped PostgreSQL advisory lock on (user_id, attempt_id)
        String lockKey = "shadowing:" + userId + ":" + request.getAttemptId();
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(?))")
            .setParameter(1, lockKey)
            .getSingleResult();

        // 3. Business-level idempotency check on shadowing_takes
        Optional<ShadowingTake> existingOpt = shadowingTakeRepository.findByUserIdAndAttemptId(userId, request.getAttemptId());
        if (existingOpt.isPresent()) {
            ShadowingTake existing = existingOpt.get();

            boolean contentMatches = existing.getContentId().equals(contentId);
            boolean ratingMatches = existing.getSelfRating().equals(request.getSelfRating());
            boolean durationMatches = existing.getRecordingDurationSeconds() != null
                && canonicalDuration.compareTo(existing.getRecordingDurationSeconds()) == 0;

            if (!contentMatches || !ratingMatches || !durationMatches) {
                throw new ShadowingConflictException(
                    "Conflicting attempt_id reuse for attempt " + request.getAttemptId() + " with mismatched payload"
                );
            }

            boolean hadActivity = learningActivityEventRepository
                .existsByUserIdAndEventTypeAndAttemptId(
                    userId,
                    LearningActivityEvent.EVENT_TYPE_SHADOWING_TAKE,
                    existing.getAttemptId()
                );

            ShadowingCompleteResponse res = new ShadowingCompleteResponse(
                existing.getAttemptId(),
                existing.getContentId(),
                existing.getSelfRating(),
                existing.getRecordingStorageKey() != null,
                hadActivity,
                existing.getCompletedAt()
            );
            return new ShadowingSubmissionResult(res, true);
        }

        // 4. Verify exercise content exists and is published SHADOWING content
        LearningContent content = learningContentRepository.findByIdAndContentTypeAndIsPublishedTrue(contentId, "SHADOWING")
            .orElseThrow(() -> new ShadowingExerciseNotFoundException("Shadowing exercise not found with id: " + contentId));

        if (!isAudioAvailable(content.getAudioStorageKey(), content.getTranscriptText())) {
            throw new ShadowingException("AUDIO_UNAVAILABLE", "Cannot complete shadowing practice because reference audio is missing or unapproved in this environment");
        }

        Instant nowUtc = Instant.now(clock);

        // 5. Persist shadowing_takes row
        ShadowingTake take = new ShadowingTake(
            UUID.randomUUID(),
            userId,
            content.getId(),
            request.getAttemptId(),
            request.getSelfRating(),
            null, // Ephemeral local browser recording in MVP
            canonicalDuration,
            nowUtc
        );
        shadowingTakeRepository.save(take);

        // 6. Transactionally log single SHADOWING_TAKE learning activity event
        // Only authentic spoken speech earns production learning credit. Synthetic test fixtures do not.
        boolean isSpoken = isSpokenSpeech(content.getAudioStorageKey());
        boolean activityLogged = false;

        if (isSpoken) {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

            ZoneId userZone = resolveUserZone(user.getTimezone());
            LocalDate userLocalDate = LocalDate.now(clock.withZone(userZone));

            LearningActivityEvent activityEvent = new LearningActivityEvent(
                userId,
                LearningActivityEvent.EVENT_TYPE_SHADOWING_TAKE,
                request.getAttemptId(),
                nowUtc,
                user.getTimezone(),
                userLocalDate,
                canonicalDuration.intValue()
            );
            learningActivityEventRepository.save(activityEvent);
            activityLogged = true;
        }

        ShadowingCompleteResponse response = new ShadowingCompleteResponse(
            take.getAttemptId(),
            content.getId(),
            take.getSelfRating(),
            false,
            activityLogged,
            take.getCompletedAt()
        );

        return new ShadowingSubmissionResult(response, false);
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
