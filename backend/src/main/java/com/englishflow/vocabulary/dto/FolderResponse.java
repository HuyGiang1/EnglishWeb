package com.englishflow.vocabulary.dto;

import com.englishflow.vocabulary.Folder;
import java.time.Instant;
import java.util.UUID;

public record FolderResponse(UUID id, String name, String description, long studySetCount,
                             Instant createdAt, Instant updatedAt) {
    public static FolderResponse from(Folder f, long count) {
        return new FolderResponse(f.getId(), f.getName(), f.getDescription(), count, f.getCreatedAt(), f.getUpdatedAt());
    }
}
