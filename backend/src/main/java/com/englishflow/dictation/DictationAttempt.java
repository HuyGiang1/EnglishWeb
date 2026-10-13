package com.englishflow.dictation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dictation_attempts")
public class DictationAttempt {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "submitted_transcript", nullable = false, columnDefinition = "TEXT")
    private String submittedTranscript;

    @Column(name = "accuracy_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal accuracyScore;

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "duration_seconds")
    private int durationSeconds;

    @Column(name = "canonical_transcript_snapshot", columnDefinition = "TEXT")
    private String canonicalTranscriptSnapshot;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "diff_tokens", columnDefinition = "jsonb")
    private String diffTokens;

    @CreationTimestamp
    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    public DictationAttempt() {
        this.id = UUID.randomUUID();
    }

    public DictationAttempt(
        UUID id,
        UUID userId,
        UUID contentId,
        UUID attemptId,
        String submittedTranscript,
        BigDecimal accuracyScore,
        int wordCount,
        int errorCount,
        int durationSeconds,
        Instant completedAt
    ) {
        this(id, userId, contentId, attemptId, submittedTranscript, accuracyScore, wordCount, errorCount, durationSeconds, null, null, completedAt);
    }

    public DictationAttempt(
        UUID id,
        UUID userId,
        UUID contentId,
        UUID attemptId,
        String submittedTranscript,
        BigDecimal accuracyScore,
        int wordCount,
        int errorCount,
        int durationSeconds,
        String canonicalTranscriptSnapshot,
        String diffTokens,
        Instant completedAt
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.userId = userId;
        this.contentId = contentId;
        this.attemptId = attemptId;
        this.submittedTranscript = submittedTranscript;
        this.accuracyScore = accuracyScore;
        this.wordCount = wordCount;
        this.errorCount = errorCount;
        this.durationSeconds = durationSeconds;
        this.canonicalTranscriptSnapshot = canonicalTranscriptSnapshot;
        this.diffTokens = diffTokens;
        this.completedAt = completedAt;
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

    public String getSubmittedTranscript() {
        return submittedTranscript;
    }

    public void setSubmittedTranscript(String submittedTranscript) {
        this.submittedTranscript = submittedTranscript;
    }

    public BigDecimal getAccuracyScore() {
        return accuracyScore;
    }

    public void setAccuracyScore(BigDecimal accuracyScore) {
        this.accuracyScore = accuracyScore;
    }

    public int getWordCount() {
        return wordCount;
    }

    public void setWordCount(int wordCount) {
        this.wordCount = wordCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getCanonicalTranscriptSnapshot() {
        return canonicalTranscriptSnapshot;
    }

    public void setCanonicalTranscriptSnapshot(String canonicalTranscriptSnapshot) {
        this.canonicalTranscriptSnapshot = canonicalTranscriptSnapshot;
    }

    public String getDiffTokens() {
        return diffTokens;
    }

    public void setDiffTokens(String diffTokens) {
        this.diffTokens = diffTokens;
    }
}
