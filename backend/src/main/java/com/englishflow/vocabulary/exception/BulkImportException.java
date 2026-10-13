package com.englishflow.vocabulary.exception;

public class BulkImportException extends RuntimeException {

    private final String errorCode;

    public BulkImportException(String message) {
        super(message);
        this.errorCode = "BAD_REQUEST";
    }

    public BulkImportException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
