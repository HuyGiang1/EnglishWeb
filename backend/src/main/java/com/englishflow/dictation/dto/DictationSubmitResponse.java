package com.englishflow.dictation.dto;

import com.englishflow.dictation.engine.DiffToken;

import java.util.List;
import java.util.UUID;

public record DictationSubmitResponse(
    UUID attemptId,
    UUID contentId,
    String canonicalTranscript,
    String submittedTranscript,
    double accuracyScore,
    int wordCount,
    int errorCount,
    boolean isPassed,
    List<DiffToken> diffTokens
) {}
