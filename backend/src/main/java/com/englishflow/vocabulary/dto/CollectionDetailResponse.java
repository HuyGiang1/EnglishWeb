package com.englishflow.vocabulary.dto;

import com.englishflow.vocabulary.Collection;
import java.time.Instant;
import java.util.UUID;

public record CollectionDetailResponse(
    UUID id,
    String title,
    String description,
    long wordCount,
    Instant createdAt,
    Instant updatedAt,
    UUID folderId
) {
    public static CollectionDetailResponse fromEntity(Collection c, long wordCount) {
        return new CollectionDetailResponse(
            c.getId(),
            c.getTitle(),
            c.getDescription(),
            wordCount,
            c.getCreatedAt(),
            c.getUpdatedAt(),
            c.getFolderId()
        );
    }
}
