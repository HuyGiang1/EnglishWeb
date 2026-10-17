package com.englishflow.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank(message = "Display name must not be blank.")
    @Size(min = 2, max = 100, message = "Display name must be between 2 and 100 characters.")
    String displayName
) {
}
