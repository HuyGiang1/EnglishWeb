package com.englishflow.vocabulary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Join entity with composite foreign key enforcement between collections and vocabularies.
 * Canonical table 'vocabulary_collections' per docs/architecture/DATABASE.md Section 3.2.
 */
@Entity
@Table(name = "vocabulary_collections")
@IdClass(VocabularyCollectionId.class)
public class VocabularyCollection {

    @Id
    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;

    @Id
    @Column(name = "vocabulary_id", nullable = false)
    private UUID vocabularyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    public VocabularyCollection() {
    }

    public VocabularyCollection(UUID userId, UUID collectionId, UUID vocabularyId) {
        this.userId = userId;
        this.collectionId = collectionId;
        this.vocabularyId = vocabularyId;
    }

    public VocabularyCollection(UUID userId, UUID collectionId, UUID vocabularyId, Instant addedAt) {
        this.userId = userId;
        this.collectionId = collectionId;
        this.vocabularyId = vocabularyId;
        this.addedAt = addedAt;
    }

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) {
            addedAt = Instant.now();
        }
    }

    public UUID getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(UUID collectionId) {
        this.collectionId = collectionId;
    }

    public UUID getVocabularyId() {
        return vocabularyId;
    }

    public void setVocabularyId(UUID vocabularyId) {
        this.vocabularyId = vocabularyId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(Instant addedAt) {
        this.addedAt = addedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VocabularyCollection that)) return false;
        return Objects.equals(collectionId, that.collectionId) &&
               Objects.equals(vocabularyId, that.vocabularyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collectionId, vocabularyId);
    }
}
