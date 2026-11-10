package com.englishflow.vocabulary;

import com.englishflow.vocabulary.dto.CreateVocabularyRequest;
import com.englishflow.vocabulary.dto.DuplicateCheckResponse;
import com.englishflow.vocabulary.dto.DuplicateEntryItem;
import com.englishflow.vocabulary.dto.UpdateVocabularyRequest;
import com.englishflow.vocabulary.dto.VocabularyPageResponse;
import com.englishflow.vocabulary.dto.VocabularyResponse;
import com.englishflow.vocabulary.exception.DuplicateVocabularyException;
import com.englishflow.vocabulary.exception.VocabularyNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class VocabularyService {

    private final VocabularyRepository vocabularyRepository;
    private final com.englishflow.review.ReviewService reviewService;
    private final CollectionRepository collectionRepository;
    private final VocabularyCollectionRepository vocabularyCollectionRepository;
    private final UserVocabularyLockCoordinator userVocabularyLockCoordinator;

    public VocabularyService(
        VocabularyRepository vocabularyRepository,
        com.englishflow.review.ReviewService reviewService,
        CollectionRepository collectionRepository,
        VocabularyCollectionRepository vocabularyCollectionRepository,
        UserVocabularyLockCoordinator userVocabularyLockCoordinator
    ) {
        this.vocabularyRepository = vocabularyRepository;
        this.reviewService = reviewService;
        this.collectionRepository = collectionRepository;
        this.vocabularyCollectionRepository = vocabularyCollectionRepository;
        this.userVocabularyLockCoordinator = userVocabularyLockCoordinator;
    }

    @Transactional
    public VocabularyResponse createVocabulary(UUID userId, CreateVocabularyRequest request) {
        userVocabularyLockCoordinator.acquireUserVocabularyLock(userId);

        Collection targetCollection = null;
        if (request.collectionId() != null) {
            targetCollection = collectionRepository.findByIdAndUserId(request.collectionId(), userId)
                .orElseThrow(() -> new com.englishflow.vocabulary.exception.CollectionNotFoundException(
                    "Collection not found: " + request.collectionId()
                ));
        }

        String normalized = Vocabulary.normalize(request.term());
        String pos = request.partOfSpeech() != null ? request.partOfSpeech().toLowerCase(Locale.ROOT).trim() : null;

        List<Vocabulary> existing = vocabularyRepository.findByUserIdAndNormalizedTermAndPartOfSpeechAndDeletedAtIsNull(
            userId, normalized, pos
        );
        if (!existing.isEmpty()) {
            throw new DuplicateVocabularyException(
                "A vocabulary entry for '" + request.term() + "' (" + pos + ") already exists in your library."
            );
        }

        Vocabulary vocabulary = new Vocabulary(
            userId,
            request.term().trim(),
            pos,
            request.phoneticIpa() != null ? request.phoneticIpa().trim() : null,
            request.vietnameseMeaning().trim(),
            request.englishDefinition() != null ? request.englishDefinition().trim() : null,
            request.exampleSentence() != null ? request.exampleSentence().trim() : null,
            request.exampleTranslation() != null ? request.exampleTranslation().trim() : null,
            request.notes() != null ? request.notes().trim() : null,
            request.source() != null ? request.source().trim() : null,
            request.difficultyLevel()
        );

        Vocabulary saved = vocabularyRepository.save(vocabulary);
        reviewService.provisionCardForVocabulary(userId, saved.getId(), null);

        if (targetCollection != null) {
            vocabularyCollectionRepository.save(
                new VocabularyCollection(userId, targetCollection.getId(), saved.getId(), java.time.Instant.now())
            );
            targetCollection.setUpdatedAt(java.time.Instant.now());
            collectionRepository.save(targetCollection);
        }

        return VocabularyResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public VocabularyResponse getVocabulary(UUID userId, UUID vocabularyId) {
        Vocabulary vocabulary = vocabularyRepository.findByIdAndUserIdAndDeletedAtIsNull(vocabularyId, userId)
            .orElseThrow(() -> new VocabularyNotFoundException("Vocabulary entry not found with id: " + vocabularyId));

        return VocabularyResponse.fromEntity(vocabulary);
    }

    @Transactional(readOnly = true)
    public VocabularyPageResponse listVocabularies(
        UUID userId,
        String search,
        String partOfSpeech,
        String difficultyLevel,
        Pageable pageable
    ) {
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String cleanPos = (partOfSpeech != null && !partOfSpeech.isBlank())
            ? partOfSpeech.toLowerCase(Locale.ROOT).trim()
            : null;
        String cleanDifficulty = (difficultyLevel != null && !difficultyLevel.isBlank())
            ? difficultyLevel.trim()
            : null;

        Page<Vocabulary> page = vocabularyRepository.searchVocabularies(
            userId,
            cleanSearch,
            cleanPos,
            cleanDifficulty,
            pageable
        );

        Page<VocabularyResponse> dtoPage = page.map(VocabularyResponse::fromEntity);
        return VocabularyPageResponse.fromPage(dtoPage);
    }

    @Transactional
    public VocabularyResponse updateVocabulary(UUID userId, UUID vocabularyId, UpdateVocabularyRequest request) {
        userVocabularyLockCoordinator.acquireUserVocabularyLock(userId);
        Vocabulary vocabulary = vocabularyRepository.findByIdAndUserIdAndDeletedAtIsNull(vocabularyId, userId)
            .orElseThrow(() -> new VocabularyNotFoundException("Vocabulary entry not found with id: " + vocabularyId));

        String normalized = Vocabulary.normalize(request.term());
        String pos = request.partOfSpeech() != null ? request.partOfSpeech().toLowerCase(Locale.ROOT).trim() : null;

        List<Vocabulary> existing = vocabularyRepository.findByUserIdAndNormalizedTermAndPartOfSpeechAndDeletedAtIsNull(
            userId, normalized, pos
        );
        for (Vocabulary other : existing) {
            if (!other.getId().equals(vocabularyId)) {
                throw new DuplicateVocabularyException(
                    "Another vocabulary entry for '" + request.term() + "' (" + pos + ") already exists in your library."
                );
            }
        }

        vocabulary.setTerm(request.term().trim());
        vocabulary.setPartOfSpeech(pos);
        vocabulary.setPhoneticIpa(request.phoneticIpa() != null ? request.phoneticIpa().trim() : null);
        vocabulary.setVietnameseMeaning(request.vietnameseMeaning().trim());
        vocabulary.setEnglishDefinition(request.englishDefinition() != null ? request.englishDefinition().trim() : null);
        vocabulary.setExampleSentence(request.exampleSentence() != null ? request.exampleSentence().trim() : null);
        vocabulary.setExampleTranslation(request.exampleTranslation() != null ? request.exampleTranslation().trim() : null);
        vocabulary.setNotes(request.notes() != null ? request.notes().trim() : null);
        vocabulary.setSource(request.source() != null ? request.source().trim() : null);
        vocabulary.setDifficultyLevel(request.difficultyLevel());

        Vocabulary updated = vocabularyRepository.save(vocabulary);
        return VocabularyResponse.fromEntity(updated);
    }

    @Transactional
    public void deleteVocabulary(UUID userId, UUID vocabularyId) {
        userVocabularyLockCoordinator.acquireUserVocabularyLock(userId);
        Vocabulary vocabulary = vocabularyRepository.findByIdAndUserIdAndDeletedAtIsNull(vocabularyId, userId)
            .orElseThrow(() -> new VocabularyNotFoundException("Vocabulary entry not found with id: " + vocabularyId));

        vocabulary.softDelete();
        vocabularyRepository.save(vocabulary);
        reviewService.suspendCardForVocabulary(userId, vocabularyId);
    }

    @Transactional(readOnly = true)
    public DuplicateCheckResponse checkDuplicates(UUID userId, String term, String partOfSpeech) {
        String normalized = Vocabulary.normalize(term);
        List<Vocabulary> entries;

        if (partOfSpeech != null && !partOfSpeech.isBlank()) {
            entries = vocabularyRepository.findByUserIdAndNormalizedTermAndPartOfSpeechAndDeletedAtIsNull(
                userId,
                normalized,
                partOfSpeech.toLowerCase(Locale.ROOT).trim()
            );
        } else {
            entries = vocabularyRepository.findByUserIdAndNormalizedTermAndDeletedAtIsNull(userId, normalized);
        }

        List<DuplicateEntryItem> items = entries.stream()
            .map(DuplicateEntryItem::fromEntity)
            .toList();

        return DuplicateCheckResponse.of(items);
    }
}
