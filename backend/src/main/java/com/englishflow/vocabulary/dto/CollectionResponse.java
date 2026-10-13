package com.englishflow.vocabulary.dto;

import com.englishflow.vocabulary.Collection;
import java.time.Instant;
import java.util.UUID;

public record CollectionResponse(
    UUID id,
    String title,
    String description,
    long wordCount,
    Instant createdAt,
    Instant updatedAt
) {
    public static CollectionResponse fromEntity(Collection c, long wordCount) {
        return new CollectionResponse(
            c.getId(),
            c.getTitle(),
            c.getDescription(),
            wordCount,
            c.getCreatedAt(),
            c.getUpdatedAt()
        );
    }
}
