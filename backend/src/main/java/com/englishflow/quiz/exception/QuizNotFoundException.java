package com.englishflow.quiz.exception;

public class QuizNotFoundException extends QuizException {

    public QuizNotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
