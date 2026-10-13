package com.englishflow.dictation.exception;

public class ExerciseNotFoundException extends DictationException {
    public ExerciseNotFoundException(String message) {
        super("EXERCISE_NOT_FOUND", message);
    }
}
