package com.englishflow.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Spaced Repetition Item Entity.
 * Maps to canonical table 'srs_items' per docs/architecture/DATABASE.md Section 3.3.
 */
@Entity
@Table(name = "srs_items")
public class SrsItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "vocabulary_id", nullable = false)
    private UUID vocabularyId;

    @Column(name = "state", nullable = false, length = 16)
    private String state = "NEW";

    @Column(name = "algorithm", nullable = false, length = 16)
    private String algorithm = "SM2";

    @Column(name = "repetition_count", nullable = false)
    private int repetitionCount = 0;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays = 0;

    @Column(name = "ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal easeFactor = new BigDecimal("2.50");

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate = LocalDate.now();

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "fsrs_stability", precision = 8, scale = 4)
    private BigDecimal fsrsStability;

    @Column(name = "fsrs_difficulty", precision = 8, scale = 4)
    private BigDecimal fsrsDifficulty;

    public SrsItem() {
    }

    public SrsItem(UUID userId, UUID vocabularyId, LocalDate dueDate) {
        this.userId = userId;
        this.vocabularyId = vocabularyId;
        this.state = "NEW";
        this.algorithm = "SM2";
        this.repetitionCount = 0;
        this.intervalDays = 0;
        this.easeFactor = new BigDecimal("2.50");
        this.dueDate = dueDate != null ? dueDate : LocalDate.now();
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
        if (this.state == null) {
            this.state = "NEW";
        }
        if (this.algorithm == null) {
            this.algorithm = "SM2";
        }
        if (this.easeFactor == null) {
            this.easeFactor = new BigDecimal("2.50");
        }
        if (this.dueDate == null) {
            this.dueDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
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

    public UUID getVocabularyId() {
        return vocabularyId;
    }

    public void setVocabularyId(UUID vocabularyId) {
        this.vocabularyId = vocabularyId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public int getRepetitionCount() {
        return repetitionCount;
    }

    public void setRepetitionCount(int repetitionCount) {
        this.repetitionCount = repetitionCount;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public void setIntervalDays(int intervalDays) {
        this.intervalDays = intervalDays;
    }

    public BigDecimal getEaseFactor() {
        return easeFactor;
    }

    public void setEaseFactor(BigDecimal easeFactor) {
        this.easeFactor = easeFactor;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Instant getLastReviewedAt() {
        return lastReviewedAt;
    }

    public void setLastReviewedAt(Instant lastReviewedAt) {
        this.lastReviewedAt = lastReviewedAt;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public BigDecimal getFsrsStability() {
        return fsrsStability;
    }

    public void setFsrsStability(BigDecimal fsrsStability) {
        this.fsrsStability = fsrsStability;
    }

    public BigDecimal getFsrsDifficulty() {
        return fsrsDifficulty;
    }

    public void setFsrsDifficulty(BigDecimal fsrsDifficulty) {
        this.fsrsDifficulty = fsrsDifficulty;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SrsItem srsItem)) return false;
        return Objects.equals(id, srsItem.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
