package com.englishflow.shadowing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public class ShadowingCompleteResponse {

    private UUID attemptId;
    private UUID contentId;
    private String selfRating;
    private boolean hasRecordingSaved;
    private boolean verifiedActivityCreated;
    private Instant completedAt;

    public ShadowingCompleteResponse() {
    }

    public ShadowingCompleteResponse(
        UUID attemptId,
        UUID contentId,
        String selfRating,
        boolean hasRecordingSaved,
        Instant completedAt
    ) {
        this(attemptId, contentId, selfRating, hasRecordingSaved, true, completedAt);
    }

    public ShadowingCompleteResponse(
        UUID attemptId,
        UUID contentId,
        String selfRating,
        boolean hasRecordingSaved,
        boolean verifiedActivityCreated,
        Instant completedAt
    ) {
        this.attemptId = attemptId;
        this.contentId = contentId;
        this.selfRating = selfRating;
        this.hasRecordingSaved = hasRecordingSaved;
        this.verifiedActivityCreated = verifiedActivityCreated;
        this.completedAt = completedAt;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(UUID attemptId) {
        this.attemptId = attemptId;
    }

    public UUID getContentId() {
        return contentId;
    }

    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    public String getSelfRating() {
        return selfRating;
    }

    public void setSelfRating(String selfRating) {
        this.selfRating = selfRating;
    }

    @JsonProperty("hasRecordingSaved")
    public boolean isHasRecordingSaved() {
        return hasRecordingSaved;
    }

    public void setHasRecordingSaved(boolean hasRecordingSaved) {
        this.hasRecordingSaved = hasRecordingSaved;
    }

    @JsonProperty("verifiedActivityCreated")
    public boolean isVerifiedActivityCreated() {
        return verifiedActivityCreated;
    }

    public void setVerifiedActivityCreated(boolean verifiedActivityCreated) {
        this.verifiedActivityCreated = verifiedActivityCreated;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
