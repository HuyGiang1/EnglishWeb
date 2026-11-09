package com.englishflow.vocabulary.exception;
public class FolderNotFoundException extends RuntimeException {
    public FolderNotFoundException() { super("Folder not found."); }
}
