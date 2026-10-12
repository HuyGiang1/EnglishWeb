package com.englishflow.vocabulary.exception;

public class VocabularyNotFoundException extends RuntimeException {
    public VocabularyNotFoundException(String message) {
        super(message);
    }
}
