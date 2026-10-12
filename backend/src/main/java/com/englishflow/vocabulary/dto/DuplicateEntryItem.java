package com.englishflow.vocabulary.dto;

import com.englishflow.vocabulary.Vocabulary;
import java.util.UUID;

public record DuplicateEntryItem(
    UUID id,
    String term,
    String partOfSpeech,
    String vietnameseMeaning
) {
    public static DuplicateEntryItem fromEntity(Vocabulary v) {
        return new DuplicateEntryItem(
            v.getId(),
            v.getTerm(),
            v.getPartOfSpeech(),
            v.getVietnameseMeaning()
        );
    }
}
