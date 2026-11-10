package com.englishflow.vocabulary.dto;

// Editable values only. No client-supplied vocabulary identity or authoritative status.
public record PasteImportRow(int rowNumber, String term, String vietnameseMeaning,
                             String phoneticIpa, String partOfSpeech, boolean excluded) {}
