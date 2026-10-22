package com.englishflow.content.audio;

public enum AudioReadinessStatus {
    MISSING,
    SYNTHETIC_FIXTURE,
    LOCAL_SPOKEN,
    PENDING_LICENSE,
    PENDING_HUMAN_QA,
    LICENSED_DISTRIBUTABLE;

    public boolean isSpokenSpeech() {
        return this == LOCAL_SPOKEN || this == LICENSED_DISTRIBUTABLE;
    }

    public boolean isProductionApproved() {
        return this == LICENSED_DISTRIBUTABLE;
    }
}
