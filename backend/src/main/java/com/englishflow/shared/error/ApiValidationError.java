package com.englishflow.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiValidationError(
    String field,
    Object rejectedValue,
    String message
) {
    public ApiValidationError(String field, String message) {
        this(field, null, message);
    }
}
