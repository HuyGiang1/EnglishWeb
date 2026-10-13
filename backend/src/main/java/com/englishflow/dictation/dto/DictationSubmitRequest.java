package com.englishflow.dictation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DictationSubmitRequest(
    @NotNull(message = "Attempt ID is required")
    UUID attemptId,

    @NotNull(message = "Submitted transcript must not be null")
    @Size(max = 2000, message = "Submitted transcript cannot exceed 2000 characters")
    String submittedTranscript,

    Integer durationSeconds
) {}
