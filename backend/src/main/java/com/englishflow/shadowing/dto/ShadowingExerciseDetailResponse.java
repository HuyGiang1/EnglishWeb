package com.englishflow.shadowing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

public class ShadowingExerciseDetailResponse {

    private UUID contentId;
    private String title;
    private String topicTag;
    private String cefrLevel;
    private String transcriptText;
    private String audioUrl;
    private BigDecimal audioDurationSeconds;
    private boolean isAudioAvailable;
    private String audioReadiness;
    private boolean isSpokenSpeech;

    public ShadowingExerciseDetailResponse() {
    }

    public ShadowingExerciseDetailResponse(
        UUID contentId,
        String title,
        String topicTag,
        String cefrLevel,
        String transcriptText,
        String audioUrl,
        BigDecimal audioDurationSeconds,
        boolean isAudioAvailable,
        String audioReadiness,
        boolean isSpokenSpeech
    ) {
        this.contentId = contentId;
        this.title = title;
        this.topicTag = topicTag;
        this.cefrLevel = cefrLevel;
        this.transcriptText = transcriptText;
        this.audioUrl = audioUrl;
        this.audioDurationSeconds = audioDurationSeconds;
        this.isAudioAvailable = isAudioAvailable;
        this.audioReadiness = audioReadiness;
        this.isSpokenSpeech = isSpokenSpeech;
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

    public String getTranscriptText() {
        return transcriptText;
    }

    public void setTranscriptText(String transcriptText) {
        this.transcriptText = transcriptText;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
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
}
