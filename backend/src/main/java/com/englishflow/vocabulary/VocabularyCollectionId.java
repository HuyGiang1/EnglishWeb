package com.englishflow.vocabulary;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite Primary Key for VocabularyCollection entity.
 */
public class VocabularyCollectionId implements Serializable {

    private UUID collectionId;
    private UUID vocabularyId;

    public VocabularyCollectionId() {
    }

    public VocabularyCollectionId(UUID collectionId, UUID vocabularyId) {
        this.collectionId = collectionId;
        this.vocabularyId = vocabularyId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VocabularyCollectionId that)) return false;
        return Objects.equals(collectionId, that.collectionId) &&
               Objects.equals(vocabularyId, that.vocabularyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collectionId, vocabularyId);
    }
}
