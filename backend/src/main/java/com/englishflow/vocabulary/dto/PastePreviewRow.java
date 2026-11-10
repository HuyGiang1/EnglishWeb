package com.englishflow.vocabulary.dto;

import java.util.List;

public record PastePreviewRow(
    int rowNumber, List<String> cells, String term, String vietnameseMeaning,
    String phoneticIpa, String partOfSpeech, boolean excluded,
    String status, List<String> issues, String existingMeaning, String existingIpa
) {}
