package com.englishflow.user.exception;

public class InvalidSettingsException extends RuntimeException {
    public InvalidSettingsException(String message) {
        super(message);
    }
}
