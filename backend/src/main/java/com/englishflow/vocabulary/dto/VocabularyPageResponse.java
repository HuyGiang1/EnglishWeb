package com.englishflow.vocabulary.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record VocabularyPageResponse(
    List<VocabularyResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static VocabularyPageResponse fromPage(Page<VocabularyResponse> page) {
        return new VocabularyPageResponse(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
