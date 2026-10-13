package com.englishflow.dictation.engine;

import java.util.List;

public record DictationEvaluationResult(
    double accuracyScore,
    int wordCount,
    int errorCount,
    boolean isPassed,
    List<DiffToken> diffTokens
) {}
