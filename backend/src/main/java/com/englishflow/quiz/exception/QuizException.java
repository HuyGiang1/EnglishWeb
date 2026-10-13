package com.englishflow.quiz.exception;

public class QuizException extends RuntimeException {

    private final String code;

    public QuizException(String message) {
        super(message);
        this.code = "VALIDATION_FAILED";
    }

    public QuizException(String code, String message) {
        super(message);
        this.code = code;
    }

    public QuizException(String message, Throwable cause) {
        super(message, cause);
        this.code = "VALIDATION_FAILED";
    }

    public String getCode() {
        return code;
    }
}
