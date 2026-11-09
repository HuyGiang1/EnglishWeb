package com.englishflow.vocabulary;

import com.englishflow.vocabulary.dto.*;
import com.englishflow.vocabulary.exception.*;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FolderService {
    private final FolderRepository folders;
    private final CollectionRepository collections;
    private final VocabularyCollectionRepository memberships;
    private final EntityManager entityManager;

    public FolderService(FolderRepository folders, CollectionRepository collections,
                         VocabularyCollectionRepository memberships, EntityManager entityManager) {
        this.folders = folders; this.collections = collections;
        this.memberships = memberships; this.entityManager = entityManager;
    }

    // Transaction-scoped owner lock serializes folder create/rename/delete and assignment.
    // Call only inside a mutating transaction, before loading the destination/collection.
    public void lockOwner(UUID userId) {
        entityManager.createNativeQuery(
            "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:owner AS text), 3302))")
            .setParameter("owner", userId.toString()).getSingleResult();
    }

    public UUID validateAssignment(UUID userId, UUID folderId) {
        lockOwner(userId);
        if (folderId != null) owned(userId, folderId);
        return folderId;
    }

    private Folder owned(UUID userId, UUID id) {
        return folders.findByIdAndUserId(id, userId).orElseThrow(FolderNotFoundException::new);
    }

    private String normalize(String value) {
        if (value == null) throw new IllegalArgumentException("Folder name is required.");
        String name = value.replaceAll("[\\x09-\\x0D ]+", " ").replaceAll("^ +| +$", "");
        if (name.isBlank() || name.length() > 100)
            throw new IllegalArgumentException("Folder name must contain 1 to 100 characters.");
        return name;
    }

    private void unique(UUID userId, String name, UUID excluded) {
        if (folders.nameExists(userId, name, excluded == null ? null : excluded.toString()))
            throw new FolderNameConflictException();
    }

    @Transactional
    public FolderResponse create(UUID userId, FolderRequest request) {
        lockOwner(userId);
        String name = normalize(request.name());
        unique(userId, name, null);
        return FolderResponse.from(folders.saveAndFlush(new Folder(userId, name, request.description())), 0);
    }

    @Transactional(readOnly = true)
    public FolderPageResponse list(UUID userId, int page, int size) {
        Page<Folder> result = folders.findByUserId(userId,
            PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by("name").and(Sort.by("id"))));
        Map<UUID, Long> counts = result.isEmpty() ? Map.of() : folders.countStudySets(userId,
            result.getContent().stream().map(Folder::getId).toList()).stream()
            .collect(Collectors.toMap(FolderRepository.SetCount::getFolderId, FolderRepository.SetCount::getSetCount));
        return new FolderPageResponse(result.map(f -> FolderResponse.from(f, counts.getOrDefault(f.getId(), 0L))).getContent(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public FolderResponse get(UUID userId, UUID id) {
        Folder folder = owned(userId, id);
        return FolderResponse.from(folder, collections.countByUserIdAndFolderId(userId, id));
    }

    @Transactional
    public FolderResponse update(UUID userId, UUID id, FolderRequest request) {
        lockOwner(userId);
        Folder folder = owned(userId, id);
        String name = normalize(request.name());
        unique(userId, name, id);
        folder.setName(name); folder.setDescription(request.description());
        folders.flush();
        return FolderResponse.from(folder, collections.countByUserIdAndFolderId(userId, id));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        lockOwner(userId);
        owned(userId, id);
        entityManager.createQuery("UPDATE Collection c SET c.folderId = NULL, c.updatedAt = :now WHERE c.userId = :owner AND c.folderId = :id")
            .setParameter("now", Instant.now()).setParameter("owner", userId).setParameter("id", id).executeUpdate();
        entityManager.flush();
        entityManager.clear();
        folders.deleteById(id);
        folders.flush();
    }

    @Transactional
    public CollectionResponse move(UUID userId, UUID collectionId, UUID destination) {
        lockOwner(userId);
        Collection collection = collections.findByIdAndUserId(collectionId, userId)
            .orElseThrow(() -> new CollectionNotFoundException("Collection not found."));
        if (destination != null) owned(userId, destination);
        collection.setFolderId(destination);
        collections.flush();
        return CollectionResponse.fromEntity(collection, memberships.countActiveWordsByCollectionId(collectionId));
    }
}
