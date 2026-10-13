package com.englishflow.dictation.engine;

public enum DiffTokenType {
    EQUAL,         // Matching word
    MISSING,       // Reference word omitted by learner
    EXTRA,         // Extra word inserted by learner
    SUBSTITUTION   // Reference word replaced by incorrect learner word
}
