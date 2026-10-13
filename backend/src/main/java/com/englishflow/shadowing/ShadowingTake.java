package com.englishflow.shadowing;

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
 * Shadowing Take Entity.
 * Maps to table 'shadowing_takes' per docs/architecture/DATABASE.md Section 3.6.
 * Enforces business-level idempotency via composite uniqueness on (user_id, attempt_id).
 */
@Entity
@Table(name = "shadowing_takes")
public class ShadowingTake {

    public static final String RATING_NEEDS_WORK = "NEEDS_WORK";
    public static final String RATING_ACCEPTABLE = "ACCEPTABLE";
    public static final String RATING_GOOD = "GOOD";
    public static final String RATING_FLUENT = "FLUENT";

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "self_rating", nullable = false, length = 32)
    private String selfRating;

    @Column(name = "recording_storage_key", length = 255)
    private String recordingStorageKey;

    @Column(name = "recording_duration_seconds", precision = 6, scale = 2)
    private BigDecimal recordingDurationSeconds;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    public ShadowingTake() {
        this.id = UUID.randomUUID();
    }

    public ShadowingTake(
        UUID id,
        UUID userId,
        UUID contentId,
        UUID attemptId,
        String selfRating,
        String recordingStorageKey,
        BigDecimal recordingDurationSeconds,
        Instant completedAt
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.userId = userId;
        this.contentId = contentId;
        this.attemptId = attemptId;
        this.selfRating = selfRating;
        this.recordingStorageKey = recordingStorageKey;
        this.recordingDurationSeconds = recordingDurationSeconds;
        this.completedAt = completedAt != null ? completedAt : Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.completedAt == null) {
            this.completedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getContentId() {
        return contentId;
    }

    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(UUID attemptId) {
        this.attemptId = attemptId;
    }

    public String getSelfRating() {
        return selfRating;
    }

    public void setSelfRating(String selfRating) {
        this.selfRating = selfRating;
    }

    public String getRecordingStorageKey() {
        return recordingStorageKey;
    }

    public void setRecordingStorageKey(String recordingStorageKey) {
        this.recordingStorageKey = recordingStorageKey;
    }

    public BigDecimal getRecordingDurationSeconds() {
        return recordingDurationSeconds;
    }

    public void setRecordingDurationSeconds(BigDecimal recordingDurationSeconds) {
        this.recordingDurationSeconds = recordingDurationSeconds;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShadowingTake that = (ShadowingTake) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
