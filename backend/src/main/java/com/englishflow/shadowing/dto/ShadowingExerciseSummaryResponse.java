package com.englishflow.shadowing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

public class ShadowingExerciseSummaryResponse {

    private UUID contentId;
    private String title;
    private String topicTag;
    private String cefrLevel;
    private BigDecimal audioDurationSeconds;
    private boolean isAudioAvailable;
    private String audioReadiness;
    private boolean isSpokenSpeech;
    private int takeCount;
    private String lastSelfRating;

    public ShadowingExerciseSummaryResponse() {
    }

    public ShadowingExerciseSummaryResponse(
        UUID contentId,
        String title,
        String topicTag,
        String cefrLevel,
        BigDecimal audioDurationSeconds,
        boolean isAudioAvailable,
        String audioReadiness,
        boolean isSpokenSpeech,
        int takeCount,
        String lastSelfRating
    ) {
        this.contentId = contentId;
        this.title = title;
        this.topicTag = topicTag;
        this.cefrLevel = cefrLevel;
        this.audioDurationSeconds = audioDurationSeconds;
        this.isAudioAvailable = isAudioAvailable;
        this.audioReadiness = audioReadiness;
        this.isSpokenSpeech = isSpokenSpeech;
        this.takeCount = takeCount;
        this.lastSelfRating = lastSelfRating;
    }

    public UUID getContentId() {
        return contentId;
    }

    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTopicTag() {
        return topicTag;
    }

    public void setTopicTag(String topicTag) {
        this.topicTag = topicTag;
    }

    public String getCefrLevel() {
        return cefrLevel;
    }

    public void setCefrLevel(String cefrLevel) {
        this.cefrLevel = cefrLevel;
    }

    public BigDecimal getAudioDurationSeconds() {
        return audioDurationSeconds;
    }

    public void setAudioDurationSeconds(BigDecimal audioDurationSeconds) {
        this.audioDurationSeconds = audioDurationSeconds;
    }

    @JsonProperty("isAudioAvailable")
    public boolean isAudioAvailable() {
        return isAudioAvailable;
    }

    public void setAudioAvailable(boolean audioAvailable) {
        isAudioAvailable = audioAvailable;
    }

    public String getAudioReadiness() {
        return audioReadiness;
    }

    public void setAudioReadiness(String audioReadiness) {
        this.audioReadiness = audioReadiness;
    }

    @JsonProperty("isSpokenSpeech")
    public boolean isSpokenSpeech() {
        return isSpokenSpeech;
    }

    public void setSpokenSpeech(boolean spokenSpeech) {
        isSpokenSpeech = spokenSpeech;
    }

    public int getTakeCount() {
        return takeCount;
    }

    public void setTakeCount(int takeCount) {
        this.takeCount = takeCount;
    }

    public String getLastSelfRating() {
        return lastSelfRating;
    }

    public void setLastSelfRating(String lastSelfRating) {
        this.lastSelfRating = lastSelfRating;
    }
}
