package com.englishflow.vocabulary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Vocabulary Entry Entity.
 * Maps to canonical table 'vocabularies' per docs/architecture/DATABASE.md Section 3.2.
 */
@Entity
@Table(name = "vocabularies")
public class Vocabulary {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "term", nullable = false, length = 150)
    private String term;

    @Column(name = "normalized_term", nullable = false, length = 150)
    private String normalizedTerm;

    @Column(name = "part_of_speech", nullable = false, length = 32)
    private String partOfSpeech;

    @Column(name = "phonetic_ipa", length = 100)
    private String phoneticIpa;

    @Column(name = "vietnamese_meaning", nullable = false, columnDefinition = "TEXT")
    private String vietnameseMeaning;

    @Column(name = "english_definition", columnDefinition = "TEXT")
    private String englishDefinition;

    @Column(name = "example_sentence", columnDefinition = "TEXT")
    private String exampleSentence;

    @Column(name = "example_translation", columnDefinition = "TEXT")
    private String exampleTranslation;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "difficulty_level", length = 8)
    private String difficultyLevel;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Vocabulary() {
    }

    public Vocabulary(
        UUID userId,
        String term,
        String partOfSpeech,
        String phoneticIpa,
        String vietnameseMeaning,
        String englishDefinition,
        String exampleSentence,
        String exampleTranslation,
        String notes,
        String source,
        String difficultyLevel
    ) {
        this.userId = userId;
        this.term = term;
        this.normalizedTerm = normalize(term);
        this.partOfSpeech = partOfSpeech != null ? partOfSpeech.toLowerCase(Locale.ROOT).trim() : null;
        this.phoneticIpa = phoneticIpa;
        this.vietnameseMeaning = vietnameseMeaning;
        this.englishDefinition = englishDefinition;
        this.exampleSentence = exampleSentence;
        this.exampleTranslation = exampleTranslation;
        this.notes = notes;
        this.source = source;
        this.difficultyLevel = difficultyLevel;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.term != null) {
            this.normalizedTerm = normalize(this.term);
        }
        if (this.partOfSpeech != null) {
            this.partOfSpeech = this.partOfSpeech.toLowerCase(Locale.ROOT).trim();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
        if (this.term != null) {
            this.normalizedTerm = normalize(this.term);
        }
        if (this.partOfSpeech != null) {
            this.partOfSpeech = this.partOfSpeech.toLowerCase(Locale.ROOT).trim();
        }
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
        this.normalizedTerm = normalize(term);
    }

    public String getNormalizedTerm() {
        return normalizedTerm;
    }

    public void setNormalizedTerm(String normalizedTerm) {
        this.normalizedTerm = normalizedTerm;
    }

    public String getPartOfSpeech() {
        return partOfSpeech;
    }

    public void setPartOfSpeech(String partOfSpeech) {
        this.partOfSpeech = partOfSpeech != null ? partOfSpeech.toLowerCase(Locale.ROOT).trim() : null;
    }

    public String getPhoneticIpa() {
        return phoneticIpa;
    }

    public void setPhoneticIpa(String phoneticIpa) {
        this.phoneticIpa = phoneticIpa;
    }

    public String getVietnameseMeaning() {
        return vietnameseMeaning;
    }

    public void setVietnameseMeaning(String vietnameseMeaning) {
        this.vietnameseMeaning = vietnameseMeaning;
    }

    public String getEnglishDefinition() {
        return englishDefinition;
    }

    public void setEnglishDefinition(String englishDefinition) {
        this.englishDefinition = englishDefinition;
    }

    public String getExampleSentence() {
        return exampleSentence;
    }

    public void setExampleSentence(String exampleSentence) {
        this.exampleSentence = exampleSentence;
    }

    public String getExampleTranslation() {
        return exampleTranslation;
    }

    public void setExampleTranslation(String exampleTranslation) {
        this.exampleTranslation = exampleTranslation;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(String difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vocabulary that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
