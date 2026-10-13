package com.englishflow.shadowing.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

public class ShadowingCompleteRequest {

    @NotNull(message = "attemptId is required")
    private UUID attemptId;

    @NotBlank(message = "selfRating is required")
    @Pattern(
        regexp = "NEEDS_WORK|ACCEPTABLE|GOOD|FLUENT",
        message = "selfRating must be one of: NEEDS_WORK, ACCEPTABLE, GOOD, FLUENT"
    )
    private String selfRating;

    @NotNull(message = "durationSeconds is required and must be positive")
    @DecimalMin(value = "0.01", message = "durationSeconds must be positive")
    @DecimalMax(value = "65.00", message = "durationSeconds cannot exceed maximum permitted recording duration of 65.00 seconds")
    @JsonProperty("durationSeconds")
    @JsonAlias({"recordingDurationSeconds", "duration_seconds", "recording_duration_seconds"})
    private BigDecimal durationSeconds;

    public ShadowingCompleteRequest() {
    }

    public ShadowingCompleteRequest(UUID attemptId, String selfRating, BigDecimal durationSeconds) {
        this.attemptId = attemptId;
        this.selfRating = selfRating;
        this.durationSeconds = durationSeconds;
    }

    public ShadowingCompleteRequest(UUID attemptId, String selfRating, Integer durationSeconds) {
        this.attemptId = attemptId;
        this.selfRating = selfRating;
        this.durationSeconds = durationSeconds != null ? BigDecimal.valueOf(durationSeconds) : null;
    }

    public ShadowingCompleteRequest(UUID attemptId, String selfRating, Double durationSeconds) {
        this.attemptId = attemptId;
        this.selfRating = selfRating;
        this.durationSeconds = durationSeconds != null ? BigDecimal.valueOf(durationSeconds) : null;
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

    public BigDecimal getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(BigDecimal durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
}
