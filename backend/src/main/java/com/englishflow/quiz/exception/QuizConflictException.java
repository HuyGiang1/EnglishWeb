package com.englishflow.quiz.exception;

public class QuizConflictException extends QuizException {

    public QuizConflictException(String message) {
        super("CONFLICT", message);
    }
}
