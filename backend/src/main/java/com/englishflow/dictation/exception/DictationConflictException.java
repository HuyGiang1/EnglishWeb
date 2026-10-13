package com.englishflow.dictation.exception;

public class DictationConflictException extends DictationException {
    public DictationConflictException(String message) {
        super("IDEMPOTENCY_CONFLICT", message);
    }
}
