package com.englishflow.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Learning Activity Event Entity.
 * Maps to canonical table 'learning_activity_events' per docs/architecture/DATABASE.md Section 3.8
 * and docs/design/HEATMAP_SPEC.md Section 4.
 */
@Entity
@Table(name = "learning_activity_events")
public class LearningActivityEvent {

    public static final String EVENT_TYPE_FLASHCARD_REVIEW = "FLASHCARD_REVIEW";
    public static final String EVENT_TYPE_DICTATION_SENTENCE = "DICTATION_SENTENCE";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "completed_at_utc", nullable = false)
    private Instant completedAtUtc;

    @Column(name = "timezone_at_event", nullable = false, length = 64)
    private String timezoneAtEvent;

    @Column(name = "historical_local_date", nullable = false)
    private LocalDate historicalLocalDate;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LearningActivityEvent() {
    }

    public LearningActivityEvent(
        UUID userId,
        String eventType,
        UUID attemptId,
        Instant completedAtUtc,
        String timezoneAtEvent,
        LocalDate historicalLocalDate,
        int durationSeconds
    ) {
        this.userId = userId;
        this.eventType = eventType;
        this.attemptId = attemptId;
        this.completedAtUtc = completedAtUtc != null ? completedAtUtc : Instant.now();
        this.timezoneAtEvent = timezoneAtEvent != null ? timezoneAtEvent : "UTC";
        this.historicalLocalDate = historicalLocalDate;
        this.durationSeconds = Math.max(0, durationSeconds);
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.completedAtUtc == null) {
            this.completedAtUtc = Instant.now();
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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(UUID attemptId) {
        this.attemptId = attemptId;
    }

    public Instant getCompletedAtUtc() {
        return completedAtUtc;
    }

    public void setCompletedAtUtc(Instant completedAtUtc) {
        this.completedAtUtc = completedAtUtc;
    }

    public String getTimezoneAtEvent() {
        return timezoneAtEvent;
    }

    public void setTimezoneAtEvent(String timezoneAtEvent) {
        this.timezoneAtEvent = timezoneAtEvent;
    }

    public LocalDate getHistoricalLocalDate() {
        return historicalLocalDate;
    }

    public void setHistoricalLocalDate(LocalDate historicalLocalDate) {
        this.historicalLocalDate = historicalLocalDate;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LearningActivityEvent that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
