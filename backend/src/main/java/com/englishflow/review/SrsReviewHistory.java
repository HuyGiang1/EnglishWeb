package com.englishflow.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * SRS Review History Log Entity.
 * Maps to canonical table 'srs_review_history' per docs/architecture/DATABASE.md Section 3.3.
 */
@Entity
@Table(name = "srs_review_history")
public class SrsReviewHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "srs_item_id", nullable = false)
    private UUID srsItemId;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "grade", nullable = false)
    private int grade;

    @Column(name = "previous_interval_days", nullable = false)
    private int previousIntervalDays;

    @Column(name = "new_interval_days", nullable = false)
    private int newIntervalDays;

    @Column(name = "previous_ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal previousEaseFactor;

    @Column(name = "new_ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal newEaseFactor;

    @Column(name = "duration_seconds")
    private int durationSeconds;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;

    public SrsReviewHistory() {
    }

    public SrsReviewHistory(
        UUID userId,
        UUID srsItemId,
        UUID attemptId,
        int grade,
        int previousIntervalDays,
        int newIntervalDays,
        BigDecimal previousEaseFactor,
        BigDecimal newEaseFactor,
        int durationSeconds,
        Instant reviewedAt
    ) {
        this.userId = userId;
        this.srsItemId = srsItemId;
        this.attemptId = attemptId;
        this.grade = grade;
        this.previousIntervalDays = previousIntervalDays;
        this.newIntervalDays = newIntervalDays;
        this.previousEaseFactor = previousEaseFactor;
        this.newEaseFactor = newEaseFactor;
        this.durationSeconds = durationSeconds;
        this.reviewedAt = reviewedAt != null ? reviewedAt : Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.reviewedAt == null) {
            this.reviewedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getSrsItemId() {
        return srsItemId;
    }

    public void setSrsItemId(UUID srsItemId) {
        this.srsItemId = srsItemId;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(UUID attemptId) {
        this.attemptId = attemptId;
    }

    public int getGrade() {
        return grade;
    }

    public void setGrade(int grade) {
        this.grade = grade;
    }

    public int getPreviousIntervalDays() {
        return previousIntervalDays;
    }

    public void setPreviousIntervalDays(int previousIntervalDays) {
        this.previousIntervalDays = previousIntervalDays;
    }

    public int getNewIntervalDays() {
        return newIntervalDays;
    }

    public void setNewIntervalDays(int newIntervalDays) {
        this.newIntervalDays = newIntervalDays;
    }

    public BigDecimal getPreviousEaseFactor() {
        return previousEaseFactor;
    }

    public void setPreviousEaseFactor(BigDecimal previousEaseFactor) {
        this.previousEaseFactor = previousEaseFactor;
    }

    public BigDecimal getNewEaseFactor() {
        return newEaseFactor;
    }

    public void setNewEaseFactor(BigDecimal newEaseFactor) {
        this.newEaseFactor = newEaseFactor;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SrsReviewHistory that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
