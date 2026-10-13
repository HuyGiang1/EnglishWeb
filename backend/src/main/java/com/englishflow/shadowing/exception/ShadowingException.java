package com.englishflow.shadowing.exception;

public class ShadowingException extends RuntimeException {

    private final String code;

    public ShadowingException(String message) {
        super(message);
        this.code = "VALIDATION_FAILED";
    }

    public ShadowingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ShadowingException(String message, Throwable cause) {
        super(message, cause);
        this.code = "VALIDATION_FAILED";
    }

    public String getCode() {
        return code;
    }
}
