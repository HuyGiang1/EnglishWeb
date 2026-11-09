package com.englishflow.review;

import com.englishflow.analytics.LearningActivityEvent;
import com.englishflow.analytics.LearningActivityEventRepository;
import com.englishflow.review.dto.ReviewCardDto;
import com.englishflow.review.dto.ReviewQueueResponse;
import com.englishflow.review.dto.ReviewSubmissionRequest;
import com.englishflow.review.dto.ReviewSubmissionResponse;
import com.englishflow.review.dto.SubmissionResult;
import com.englishflow.review.exception.IdempotencyConflictException;
import com.englishflow.review.exception.SrsItemNotFoundException;
import com.englishflow.review.scheduler.SchedulingResult;
import com.englishflow.review.scheduler.SrsScheduler;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.englishflow.vocabulary.CollectionService;
import com.englishflow.vocabulary.Vocabulary;
import com.englishflow.vocabulary.VocabularyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final SrsItemRepository srsItemRepository;
    private final SrsReviewHistoryRepository srsReviewHistoryRepository;
    private final LearningActivityEventRepository learningActivityEventRepository;
    private final VocabularyRepository vocabularyRepository;
    private final UserRepository userRepository;
    private final SrsScheduler srsScheduler;
    private final Clock clock;
    private final CollectionService collectionService;

    public ReviewService(
        SrsItemRepository srsItemRepository,
        SrsReviewHistoryRepository srsReviewHistoryRepository,
        LearningActivityEventRepository learningActivityEventRepository,
        VocabularyRepository vocabularyRepository,
        UserRepository userRepository,
        SrsScheduler srsScheduler,
        Clock clock,
        CollectionService collectionService
    ) {
        this.srsItemRepository = srsItemRepository;
        this.srsReviewHistoryRepository = srsReviewHistoryRepository;
        this.learningActivityEventRepository = learningActivityEventRepository;
        this.vocabularyRepository = vocabularyRepository;
        this.userRepository = userRepository;
        this.srsScheduler = srsScheduler;
        this.clock = clock;
        this.collectionService = collectionService;
    }

    /**
     * Fetch due cards queue for the authenticated user based on their current local date.
     * Automatically and idempotently provisions any unprovisioned active vocabulary entries.
     */
    @Transactional
    public ReviewQueueResponse getDueQueue(UUID userId, Integer limit) {
        return getDueQueue(userId, null, limit);
    }

    @Transactional
    public ReviewQueueResponse getDueQueue(UUID userId, UUID collectionId, Integer limit) {
        // Validate ownership before provisioning or looking up any scoped vocabulary.
        if (collectionId != null) collectionService.getCollection(userId, collectionId);
        ZoneId zoneId = resolveUserZone(userId);
        LocalDate userLocalDate = LocalDate.now(clock.withZone(zoneId));

        // Lazily provision any active vocabularies that lack an SRS card
        if (collectionId == null) {
            provisionMissingSrsItems(userId, userLocalDate);
        } else {
            for (Vocabulary vocab : vocabularyRepository.findUnprovisionedVocabulariesInCollection(userId, collectionId)) {
                provisionCardForVocabulary(userId, vocab.getId(), userLocalDate);
            }
        }

        int fetchLimit = (limit != null && limit > 0) ? Math.min(limit, 100) : 50;
        Pageable pageable = PageRequest.of(0, fetchLimit);

        List<Object[]> rows = collectionId == null
            ? srsItemRepository.findDueCardsWithVocabulary(userId, userLocalDate, pageable)
            : srsItemRepository.findDueCardsInCollection(userId, collectionId, userLocalDate, pageable);
        long totalDue = collectionId == null
            ? srsItemRepository.countDueCards(userId, userLocalDate)
            : srsItemRepository.countDueCardsInCollection(userId, collectionId, userLocalDate);

        List<ReviewCardDto> items = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            SrsItem s = (SrsItem) row[0];
            Vocabulary v = (Vocabulary) row[1];
            items.add(new ReviewCardDto(
                s.getId(),
                v.getId(),
                v.getTerm(),
                v.getPartOfSpeech(),
                v.getPhoneticIpa(),
                v.getVietnameseMeaning(),
                v.getEnglishDefinition(),
                v.getExampleSentence(),
                s.getIntervalDays(),
                s.getEaseFactor()
            ));
        }

        return new ReviewQueueResponse(totalDue, items);
    }

    /**
     * Submit card grade with concurrency-safe idempotency.
     * Replays on identical attempt ID, rejects on parameter conflict, or performs atomic SM-2 state transition.
     */
    @Transactional
    public SubmissionResult submitGrade(UUID userId, ReviewSubmissionRequest request) {
        ZoneId zoneId = resolveUserZone(userId);

        // 1. Fast pre-check: Was this attempt already recorded?
        Optional<SrsReviewHistory> existingHistoryOpt = srsReviewHistoryRepository
            .findByUserIdAndAttemptId(userId, request.attemptId());

        if (existingHistoryOpt.isPresent()) {
            return handleExistingAttempt(existingHistoryOpt.get(), request, zoneId);
        }

        // 2. Concurrency gate: Acquire pessimistic write lock on the target SRS item
        SrsItem srsItem = srsItemRepository.findByIdAndUserIdForUpdate(request.srsItemId(), userId)
            .orElseThrow(() -> new SrsItemNotFoundException(
                "SRS item not found with id: " + request.srsItemId()
            ));

        if ("SUSPENDED".equals(srsItem.getState())) {
            throw new SrsItemNotFoundException("Cannot review suspended card with id: " + request.srsItemId());
        }

        if (vocabularyRepository.findByIdAndUserIdAndDeletedAtIsNull(srsItem.getVocabularyId(), userId).isEmpty()) {
            throw new SrsItemNotFoundException("Cannot review card for deleted vocabulary item");
        }

        // 3. Double-check inside pessimistic lock (in case a concurrent thread just committed)
        existingHistoryOpt = srsReviewHistoryRepository.findByUserIdAndAttemptId(userId, request.attemptId());
        if (existingHistoryOpt.isPresent()) {
            return handleExistingAttempt(existingHistoryOpt.get(), request, zoneId);
        }

        // 4. Resolve user context and current time
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        Instant now = Instant.now(clock);
        LocalDate userLocalDate = now.atZone(zoneId).toLocalDate();

        // 5. Execute SM-2 algorithm calculation
        SchedulingResult scheduling = srsScheduler.calculate(
            srsItem.getIntervalDays(),
            srsItem.getEaseFactor(),
            srsItem.getRepetitionCount(),
            request.grade(),
            userLocalDate
        );

        int prevInterval = srsItem.getIntervalDays();
        BigDecimal prevEaseFactor = srsItem.getEaseFactor();

        // 6. Mutate SRS item state
        srsItem.setIntervalDays(scheduling.newIntervalDays());
        srsItem.setEaseFactor(scheduling.newEaseFactor());
        srsItem.setRepetitionCount(scheduling.newRepetitionCount());
        srsItem.setState(scheduling.nextState());
        srsItem.setDueDate(scheduling.nextDueDate());
        srsItem.setLastReviewedAt(now);
        srsItemRepository.save(srsItem);

        // 7. Persist review history record
        int durationSec = request.durationSeconds() != null ? request.durationSeconds() : 0;
        SrsReviewHistory history = new SrsReviewHistory(
            userId,
            srsItem.getId(),
            request.attemptId(),
            request.grade().getGrade(),
            prevInterval,
            scheduling.newIntervalDays(),
            prevEaseFactor,
            scheduling.newEaseFactor(),
            durationSec,
            now
        );
        srsReviewHistoryRepository.save(history);

        // 8. Persist verified learning activity event with immutable historical local date
        LearningActivityEvent activityEvent = new LearningActivityEvent(
            userId,
            LearningActivityEvent.EVENT_TYPE_FLASHCARD_REVIEW,
            request.attemptId(),
            now,
            user.getTimezone() != null ? user.getTimezone() : "UTC",
            userLocalDate,
            durationSec
        );
        learningActivityEventRepository.save(activityEvent);

        ReviewSubmissionResponse response = new ReviewSubmissionResponse(
            srsItem.getId(),
            request.attemptId(),
            scheduling.newIntervalDays(),
            scheduling.newEaseFactor(),
            scheduling.nextDueDate(),
            scheduling.nextReviewLabel()
        );

        return new SubmissionResult(response, false);
    }

    /**
     * Idempotently provision missing SRS cards for all active vocabularies belonging to the user.
     */
    @Transactional
    public void provisionMissingSrsItems(UUID userId, LocalDate referenceDate) {
        List<Vocabulary> unprovisioned = vocabularyRepository.findUnprovisionedVocabularies(userId);
        for (Vocabulary vocab : unprovisioned) {
            provisionCardForVocabulary(userId, vocab.getId(), referenceDate);
        }
    }

    /**
     * Provision a single SRS item for a specific vocabulary entry.
     */
    @Transactional
    public void provisionCardForVocabulary(UUID userId, UUID vocabularyId, LocalDate referenceDate) {
        if (!srsItemRepository.existsByUserIdAndVocabularyId(userId, vocabularyId)) {
            LocalDate dueDate = referenceDate != null ? referenceDate : LocalDate.now(clock.withZone(resolveUserZone(userId)));
            SrsItem item = new SrsItem(userId, vocabularyId, dueDate);
            try {
                srsItemRepository.save(item);
            } catch (Exception ex) {
                // Ignore unique constraint race if another thread provisioned simultaneously
                log.debug("Concurrent SRS item provisioning caught for vocab {}: {}", vocabularyId, ex.getMessage());
            }
        }
    }

    /**
     * Mark an SRS card as SUSPENDED when its vocabulary is soft-deleted.
     */
    @Transactional
    public void suspendCardForVocabulary(UUID userId, UUID vocabularyId) {
        srsItemRepository.findByUserIdAndVocabularyId(userId, vocabularyId).ifPresent(item -> {
            item.setState("SUSPENDED");
            srsItemRepository.save(item);
        });
    }

    private SubmissionResult handleExistingAttempt(
        SrsReviewHistory existing,
        ReviewSubmissionRequest request,
        ZoneId zoneId
    ) {
        // Enforce strict parameter match: attemptId cannot be reused with a different srsItemId, grade, or duration
        boolean srsItemMatches = existing.getSrsItemId().equals(request.srsItemId());
        boolean gradeMatches = existing.getGrade() == request.grade().getGrade();
        boolean durationMatches = request.durationSeconds() == null ||
            request.durationSeconds() == existing.getDurationSeconds();

        if (!srsItemMatches || !gradeMatches || !durationMatches) {
            throw new IdempotencyConflictException(
                "Attempt ID '" + request.attemptId() + "' has already been processed with different submission parameters."
            );
        }

        // Reconstruct authoritative replay response
        LocalDate reviewDate = existing.getReviewedAt().atZone(zoneId).toLocalDate();
        LocalDate calculatedDueDate = reviewDate.plusDays(existing.getNewIntervalDays());
        String label = SchedulingResult.formatIntervalLabel(existing.getNewIntervalDays());

        ReviewSubmissionResponse replayResponse = new ReviewSubmissionResponse(
            existing.getSrsItemId(),
            existing.getAttemptId(),
            existing.getNewIntervalDays(),
            existing.getNewEaseFactor(),
            calculatedDueDate,
            label
        );

        return new SubmissionResult(replayResponse, true);
    }

    private ZoneId resolveUserZone(UUID userId) {
        return userRepository.findById(userId)
            .map(User::getTimezone)
            .filter(tz -> tz != null && !tz.isBlank())
            .map(tz -> {
                try {
                    return ZoneId.of(tz);
                } catch (Exception ex) {
                    log.warn("Invalid timezone '{}' for user {}, falling back to UTC", tz, userId);
                    return ZoneId.of("UTC");
                }
            })
            .orElse(ZoneId.of("UTC"));
    }
}
