package com.englishflow.vocabulary;

import com.englishflow.vocabulary.dto.AssignVocabularyRequest;
import com.englishflow.vocabulary.dto.AssignVocabularyResponse;
import com.englishflow.vocabulary.dto.CollectionDetailResponse;
import com.englishflow.vocabulary.dto.CollectionPageResponse;
import com.englishflow.vocabulary.dto.CollectionResponse;
import com.englishflow.vocabulary.dto.CollectionSummaryDto;
import com.englishflow.vocabulary.dto.CreateCollectionRequest;
import com.englishflow.vocabulary.dto.UpdateCollectionRequest;
import com.englishflow.vocabulary.dto.VocabularyPageResponse;
import com.englishflow.vocabulary.dto.VocabularyResponse;
import com.englishflow.vocabulary.exception.CollectionNotFoundException;
import com.englishflow.vocabulary.exception.DuplicateCollectionTitleException;
import com.englishflow.vocabulary.exception.VocabularyNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CollectionService {

    private final UserVocabularyLockCoordinator vocabularyLocks;
    private final FolderService folderService;
    private final CollectionRepository collectionRepository;
    private final VocabularyCollectionRepository vocabularyCollectionRepository;
    private final VocabularyRepository vocabularyRepository;

    public CollectionService(
        CollectionRepository collectionRepository,
        VocabularyCollectionRepository vocabularyCollectionRepository,
        VocabularyRepository vocabularyRepository,
        FolderService folderService,
        UserVocabularyLockCoordinator vocabularyLocks
    ) {
        this.vocabularyLocks = vocabularyLocks;
        this.folderService = folderService;
        this.collectionRepository = collectionRepository;
        this.vocabularyCollectionRepository = vocabularyCollectionRepository;
        this.vocabularyRepository = vocabularyRepository;
    }

    @Transactional
    public CollectionResponse createCollection(UUID userId, CreateCollectionRequest request) {
        UUID folderId = folderService.validateAssignment(userId, request.folderId());
        String trimmedTitle = request.title().trim();
        if (collectionRepository.existsByUserIdAndTitleIgnoreCase(userId, trimmedTitle)) {
            throw new DuplicateCollectionTitleException("A collection with title '" + trimmedTitle + "' already exists.");
        }

        Collection collection = new Collection(userId, trimmedTitle, request.description());
        collection.setFolderId(folderId);
        Collection saved = collectionRepository.save(collection);
        return CollectionResponse.fromEntity(saved, 0L);
    }

    @Transactional(readOnly = true)
    public CollectionPageResponse listCollections(UUID userId, Pageable pageable) {
        return listCollections(userId, pageable, null, false);
    }

    @Transactional(readOnly = true)
    public CollectionPageResponse listCollections(UUID userId, Pageable pageable, UUID folderId, boolean unfiled) {
        if (folderId != null && unfiled) throw new IllegalArgumentException("Choose either folderId or unfiled.");
        if (folderId != null) folderService.get(userId, folderId);
        Page<Collection> page = folderId != null
            ? collectionRepository.findByUserIdAndFolderId(userId, folderId, pageable)
            : unfiled ? collectionRepository.findByUserIdAndFolderIdIsNull(userId, pageable)
            : collectionRepository.findByUserId(userId, pageable);
        if (page.isEmpty()) {
            return new CollectionPageResponse(
                List.of(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }

        List<UUID> collectionIds = page.getContent().stream().map(Collection::getId).toList();
        List<CollectionWordCountProjection> countProjections =
            vocabularyCollectionRepository.countActiveWordsByCollectionIds(collectionIds);

        Map<UUID, Long> countMap = countProjections.stream()
            .collect(Collectors.toMap(
                CollectionWordCountProjection::getCollectionId,
                CollectionWordCountProjection::getWordCount,
                (c1, c2) -> c1
            ));

        List<CollectionResponse> responses = page.getContent().stream()
            .map(c -> CollectionResponse.fromEntity(c, countMap.getOrDefault(c.getId(), 0L)))
            .toList();

        return new CollectionPageResponse(
            responses,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public CollectionDetailResponse getCollection(UUID userId, UUID id) {
        Collection collection = collectionRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new CollectionNotFoundException("Collection not found: " + id));

        long wordCount = vocabularyCollectionRepository.countActiveWordsByCollectionId(id);
        return CollectionDetailResponse.fromEntity(collection, wordCount);
    }

    @Transactional
    public CollectionResponse updateCollection(UUID userId, UUID id, UpdateCollectionRequest request) {
        Collection collection = collectionRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new CollectionNotFoundException("Collection not found: " + id));

        String trimmedTitle = request.title().trim();
        if (collectionRepository.existsByUserIdAndTitleIgnoreCaseAndIdNot(userId, trimmedTitle, id)) {
            throw new DuplicateCollectionTitleException("A collection with title '" + trimmedTitle + "' already exists.");
        }

        collection.setTitle(trimmedTitle);
        collection.setDescription(request.description());
        Collection updated = collectionRepository.save(collection);

        long wordCount = vocabularyCollectionRepository.countActiveWordsByCollectionId(id);
        return CollectionResponse.fromEntity(updated, wordCount);
    }

    @Transactional
    public void deleteCollection(UUID userId, UUID id) {
        Collection collection = collectionRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new CollectionNotFoundException("Collection not found: " + id));

        // Non-destructive: DB foreign keys cascade to vocabulary_collections only.
        // Underlying vocabularies, srs_items, and learning events remain intact.
        collectionRepository.delete(collection);
    }

    @Transactional(readOnly = true)
    public VocabularyPageResponse getVocabulariesInCollection(UUID userId, UUID collectionId, Pageable pageable) {
        // Enforce collection ownership
        collectionRepository.findByIdAndUserId(collectionId, userId)
            .orElseThrow(() -> new CollectionNotFoundException("Collection not found: " + collectionId));

        Page<Vocabulary> page = vocabularyCollectionRepository.findVocabulariesByCollectionId(
            collectionId,
            userId,
            pageable
        );

        List<VocabularyResponse> items = page.getContent().stream()
            .map(VocabularyResponse::fromEntity)
            .toList();

        return new VocabularyPageResponse(
            items,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    @Transactional
    public AssignVocabularyResponse assignVocabularies(
        UUID userId,
        UUID collectionId,
        AssignVocabularyRequest request
    ) {
        vocabularyLocks.acquireUserVocabularyLock(userId);
        Collection collection = collectionRepository.findByIdAndUserId(collectionId, userId)
            .orElseThrow(() -> new CollectionNotFoundException("Collection not found: " + collectionId));

        int addedCount = 0;
        Instant now = Instant.now();

        for (UUID vocabId : request.vocabularyIds()) {
            // Verify vocabulary exists, is owned by user, and is not soft-deleted
            vocabularyRepository.findByIdAndUserIdAndDeletedAtIsNull(vocabId, userId)
                .orElseThrow(() -> new VocabularyNotFoundException("Active vocabulary not found: " + vocabId));

            // Enforce idempotency: prevent duplicate membership rows
            if (!vocabularyCollectionRepository.existsByCollectionIdAndVocabularyId(collectionId, vocabId)) {
                VocabularyCollection vc = new VocabularyCollection(userId, collectionId, vocabId, now);
                vocabularyCollectionRepository.save(vc);
                addedCount++;
            }
        }

        if (addedCount > 0) {
            collection.setUpdatedAt(now);
            collectionRepository.save(collection);
        }

        return new AssignVocabularyResponse(addedCount);
    }

    @Transactional
    public void removeVocabularyFromCollection(UUID userId, UUID collectionId, UUID vocabularyId) {
        // Enforce collection ownership
        vocabularyLocks.acquireUserVocabularyLock(userId);
        Collection collection = collectionRepository.findByIdAndUserId(collectionId, userId)
            .orElseThrow(() -> new CollectionNotFoundException("Collection not found: " + collectionId));

        VocabularyCollection vc = vocabularyCollectionRepository
            .findByCollectionIdAndVocabularyIdAndUserId(collectionId, vocabularyId, userId)
            .orElseThrow(() -> new VocabularyNotFoundException("Vocabulary is not associated with collection: " + vocabularyId));

        // Non-destructive: deletes join row only, underlying vocabulary is completely untouched
        vocabularyCollectionRepository.delete(vc);

        collection.setUpdatedAt(Instant.now());
        collectionRepository.save(collection);
    }

    @Transactional(readOnly = true)
    public List<CollectionSummaryDto> getCollectionsForVocabulary(UUID userId, UUID vocabularyId) {
        // Verify vocabulary exists and is owned by user
        vocabularyRepository.findByIdAndUserIdAndDeletedAtIsNull(vocabularyId, userId)
            .orElseThrow(() -> new VocabularyNotFoundException("Vocabulary not found: " + vocabularyId));

        return vocabularyCollectionRepository.findCollectionsByVocabularyId(vocabularyId, userId);
    }
}
