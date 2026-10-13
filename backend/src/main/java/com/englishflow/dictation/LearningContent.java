package com.englishflow.dictation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learning_contents")
public class LearningContent {

    @Id
    private UUID id;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType; // DICTATION, SHADOWING, QUIZ

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "cefr_level", nullable = false, length = 8)
    private String cefrLevel;

    @Column(name = "topic_tag", nullable = false, length = 64)
    private String topicTag;

    @Column(name = "transcript_text", nullable = false, columnDefinition = "TEXT")
    private String transcriptText;

    @Column(name = "audio_storage_key", length = 255)
    private String audioStorageKey;

    @Column(name = "audio_duration_seconds", precision = 6, scale = 2)
    private BigDecimal audioDurationSeconds;

    @Column(name = "audio_mime_type", length = 64)
    private String audioMimeType;

    @Column(name = "audio_file_size_bytes")
    private Long audioFileSizeBytes;

    @Column(name = "source_attribution", length = 255)
    private String sourceAttribution;

    @Column(name = "is_published", nullable = false)
    private boolean isPublished = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public LearningContent() {
        this.id = UUID.randomUUID();
    }

    public LearningContent(
        UUID id,
        String contentType,
        String title,
        String cefrLevel,
        String topicTag,
        String transcriptText,
        String audioStorageKey,
        BigDecimal audioDurationSeconds,
        String audioMimeType,
        Long audioFileSizeBytes,
        String sourceAttribution,
        boolean isPublished
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.contentType = contentType;
        this.title = title;
        this.cefrLevel = cefrLevel;
        this.topicTag = topicTag;
        this.transcriptText = transcriptText;
        this.audioStorageKey = audioStorageKey;
        this.audioDurationSeconds = audioDurationSeconds;
        this.audioMimeType = audioMimeType;
        this.audioFileSizeBytes = audioFileSizeBytes;
        this.sourceAttribution = sourceAttribution;
        this.isPublished = isPublished;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCefrLevel() {
        return cefrLevel;
    }

    public void setCefrLevel(String cefrLevel) {
        this.cefrLevel = cefrLevel;
    }

    public String getTopicTag() {
        return topicTag;
    }

    public void setTopicTag(String topicTag) {
        this.topicTag = topicTag;
    }

    public String getTranscriptText() {
        return transcriptText;
    }

    public void setTranscriptText(String transcriptText) {
        this.transcriptText = transcriptText;
    }

    public String getAudioStorageKey() {
        return audioStorageKey;
    }

    public void setAudioStorageKey(String audioStorageKey) {
        this.audioStorageKey = audioStorageKey;
    }

    public BigDecimal getAudioDurationSeconds() {
        return audioDurationSeconds;
    }

    public void setAudioDurationSeconds(BigDecimal audioDurationSeconds) {
        this.audioDurationSeconds = audioDurationSeconds;
    }

    public String getAudioMimeType() {
        return audioMimeType;
    }

    public void setAudioMimeType(String audioMimeType) {
        this.audioMimeType = audioMimeType;
    }

    public Long getAudioFileSizeBytes() {
        return audioFileSizeBytes;
    }

    public void setAudioFileSizeBytes(Long audioFileSizeBytes) {
        this.audioFileSizeBytes = audioFileSizeBytes;
    }

    public String getSourceAttribution() {
        return sourceAttribution;
    }

    public void setSourceAttribution(String sourceAttribution) {
        this.sourceAttribution = sourceAttribution;
    }

    public boolean isPublished() {
        return isPublished;
    }

    public void setPublished(boolean published) {
        isPublished = published;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
