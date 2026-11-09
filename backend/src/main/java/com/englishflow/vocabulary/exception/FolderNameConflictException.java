package com.englishflow.vocabulary.exception;
public class FolderNameConflictException extends RuntimeException {
    public FolderNameConflictException() { super("A folder with this name already exists."); }
}
