package com.englishflow.vocabulary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCollectionRequest(
    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title must not exceed 100 characters")
    String title,

    String description,
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = FolderIdDeserializer.class)
    UUID folderId
) {
    public CreateCollectionRequest(String title, String description) { this(title, description, null); }
}
