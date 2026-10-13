package com.englishflow.shadowing.dto;

public class ShadowingSubmissionResult {

    private final ShadowingCompleteResponse response;
    private final boolean isReplay;

    public ShadowingSubmissionResult(ShadowingCompleteResponse response, boolean isReplay) {
        this.response = response;
        this.isReplay = isReplay;
    }

    public ShadowingCompleteResponse getResponse() {
        return response;
    }

    public boolean isReplay() {
        return isReplay;
    }
}
