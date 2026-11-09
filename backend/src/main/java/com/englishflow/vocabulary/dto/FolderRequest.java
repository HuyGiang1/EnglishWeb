package com.englishflow.vocabulary.dto;

import jakarta.validation.constraints.*;

public record FolderRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 1000) String description
) {}
