package com.englishflow.dictation.exception;

public class DictationException extends RuntimeException {
    private final String code;

    public DictationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
