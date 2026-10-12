package com.englishflow.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
    String code,
    String message,
    int status,
    String correlationId,
    Instant timestamp,
    List<ApiValidationError> validationErrors
) {
    public static ApiErrorResponse of(String code, String message, int status) {
        return new ApiErrorResponse(code, message, status, null, Instant.now(), null);
    }

    public static ApiErrorResponse of(String code, String message, int status, List<ApiValidationError> validationErrors) {
        return new ApiErrorResponse(code, message, status, null, Instant.now(), validationErrors);
    }
}
