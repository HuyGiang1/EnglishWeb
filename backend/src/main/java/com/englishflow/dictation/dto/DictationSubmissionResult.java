package com.englishflow.dictation.dto;

public record DictationSubmissionResult(
    DictationSubmitResponse response,
    boolean isReplay
) {}
