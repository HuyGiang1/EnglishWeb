package com.englishflow.vocabulary.dto;

import java.util.List;

public record DuplicateCheckResponse(
    boolean exists,
    List<DuplicateEntryItem> existingEntries
) {
    public static DuplicateCheckResponse of(List<DuplicateEntryItem> entries) {
        return new DuplicateCheckResponse(!entries.isEmpty(), entries);
    }
}
