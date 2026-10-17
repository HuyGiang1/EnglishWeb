package com.englishflow.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "Current password must not be blank.")
    String currentPassword,

    @NotBlank(message = "New password must not be blank.")
    @Size(min = 8, max = 100, message = "New password must be between 8 and 100 characters.")
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[^a-zA-Z0-9]).{8,100}$",
        message = "New password must contain at least one digit and one special character."
    )
    String newPassword,

    String confirmPassword
) {
}
