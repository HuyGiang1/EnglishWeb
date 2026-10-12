package com.englishflow.vocabulary.dto;

import java.time.LocalDate;

public record SrsStatusDto(
    String state,
    LocalDate dueDate,
    int intervalDays
) {
    public static SrsStatusDto initial() {
        return new SrsStatusDto("NEW", null, 0);
    }
}
