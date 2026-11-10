package com.englishflow.vocabulary.dto;

import java.util.List;

public record PastePreviewResponse(String delimiter, List<String> mapping, List<String> warnings,
                                   List<PastePreviewRow> rows) {}
