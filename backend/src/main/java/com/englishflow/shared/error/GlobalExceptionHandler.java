package com.englishflow.shared.error;

import com.englishflow.auth.exception.EmailAlreadyRegisteredException;
import com.englishflow.auth.exception.InvalidCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Global Exception Handler mapping exceptions to RFC 7807 compliant ApiErrorResponse DTOs.
 * Strictly redacts passwords, raw SQL, and stack traces.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "password", "passwordhash", "secret", "token", "credentials", "rawpassword"
    );

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<ApiValidationError> validationErrors = new ArrayList<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String field = fieldError.getField();
            Object rejectedValue = fieldError.getRejectedValue();

            // Redact sensitive inputs (passwords, tokens) to prevent information disclosure
            if (field != null && SENSITIVE_FIELDS.contains(field.toLowerCase().replaceAll("[^a-z]", ""))) {
                rejectedValue = "[REDACTED]";
            }

            validationErrors.add(new ApiValidationError(
                field,
                rejectedValue,
                fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value"
            ));
        }

        ApiErrorResponse response = ApiErrorResponse.of(
            "VALIDATION_FAILED",
            "The request contains invalid or missing fields.",
            HttpStatus.BAD_REQUEST.value(),
            validationErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "EMAIL_ALREADY_REGISTERED",
            ex.getMessage(),
            HttpStatus.CONFLICT.value()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(com.englishflow.vocabulary.exception.FolderNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleFolderNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiErrorResponse.of("NOT_FOUND", "Folder not found.", 404));
    }

    @ExceptionHandler(com.englishflow.vocabulary.exception.FolderNameConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleFolderNameConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse.of("FOLDER_NAME_EXISTS", "A folder with this name already exists.", 409));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidIdentifier() {
        return ResponseEntity.badRequest().body(
            ApiErrorResponse.of("VALIDATION_FAILED", "A request parameter has an invalid format.", 400));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Database integrity violation occurred.");
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (msg.contains("uq_folder_user_name")) return handleFolderNameConflict();

        if (msg.contains("uq_app_users_email") || (msg.contains("app_users") && msg.contains("email"))) {
            ApiErrorResponse response = ApiErrorResponse.of(
                "EMAIL_ALREADY_REGISTERED",
                "An account with this email address already exists.",
                HttpStatus.CONFLICT.value()
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        if (msg.contains("uq_srs_review_attempt") || msg.contains("uq_activity_user_attempt") || msg.contains("uq_dictation_user_attempt") || msg.contains("uq_shadowing_user_attempt")) {
            ApiErrorResponse response = ApiErrorResponse.of(
                "IDEMPOTENCY_CONFLICT",
                "Attempt ID has already been processed with a concurrent submission.",
                HttpStatus.CONFLICT.value()
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        if (msg.contains("uq_collection_user_title") || msg.contains("uq_collection_user_lower_title")) {
            ApiErrorResponse response = ApiErrorResponse.of(
                "TITLE_ALREADY_EXISTS",
                "A collection with this title already exists.",
                HttpStatus.CONFLICT.value()
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        if (msg.contains("fk_vocab_col_collection") || msg.contains("fk_vocab_col_vocabulary")) {
            ApiErrorResponse response = ApiErrorResponse.of(
                "CROSS_USER_INTEGRITY_VIOLATION",
                "Cannot associate items belonging to different users.",
                HttpStatus.FORBIDDEN.value()
            );
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        ApiErrorResponse response = ApiErrorResponse.of(
            "CONFLICT",
            "A database integrity conflict occurred.",
            HttpStatus.CONFLICT.value()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(com.englishflow.vocabulary.exception.CollectionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCollectionNotFound(com.englishflow.vocabulary.exception.CollectionNotFoundException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "NOT_FOUND",
            ex.getMessage(),
            HttpStatus.NOT_FOUND.value()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(com.englishflow.vocabulary.exception.DuplicateCollectionTitleException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateCollectionTitle(com.englishflow.vocabulary.exception.DuplicateCollectionTitleException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "TITLE_ALREADY_EXISTS",
            ex.getMessage(),
            HttpStatus.CONFLICT.value()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(com.englishflow.vocabulary.exception.VocabularyNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleVocabularyNotFound(com.englishflow.vocabulary.exception.VocabularyNotFoundException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "NOT_FOUND",
            ex.getMessage(),
            HttpStatus.NOT_FOUND.value()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(com.englishflow.vocabulary.exception.DuplicateVocabularyException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateVocabulary(com.englishflow.vocabulary.exception.DuplicateVocabularyException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "DUPLICATE_VOCABULARY",
            ex.getMessage(),
            HttpStatus.CONFLICT.value()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(com.englishflow.vocabulary.exception.BulkImportException.class)
    public ResponseEntity<ApiErrorResponse> handleBulkImportException(com.englishflow.vocabulary.exception.BulkImportException ex) {
        HttpStatus status = "CONFLICT".equalsIgnoreCase(ex.getErrorCode()) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        ApiErrorResponse response = ApiErrorResponse.of(
            ex.getErrorCode() != null ? ex.getErrorCode() : "BAD_REQUEST",
            ex.getMessage(),
            status.value()
        );
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(com.englishflow.review.exception.IdempotencyConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(com.englishflow.review.exception.IdempotencyConflictException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "IDEMPOTENCY_CONFLICT",
            ex.getMessage(),
            HttpStatus.CONFLICT.value()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(com.englishflow.review.exception.SrsItemNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleSrsItemNotFound(com.englishflow.review.exception.SrsItemNotFoundException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "NOT_FOUND",
            ex.getMessage(),
            HttpStatus.NOT_FOUND.value()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(com.englishflow.dictation.exception.ExerciseNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleExerciseNotFound(com.englishflow.dictation.exception.ExerciseNotFoundException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "NOT_FOUND",
            ex.getMessage(),
            HttpStatus.NOT_FOUND.value()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler({
        org.springframework.web.servlet.resource.NoResourceFoundException.class,
        org.springframework.web.servlet.NoHandlerFoundException.class
    })
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(Exception ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "RESOURCE_NOT_FOUND",
            "The requested resource was not found.",
            HttpStatus.NOT_FOUND.value()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(com.englishflow.dictation.exception.DictationConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleDictationConflict(com.englishflow.dictation.exception.DictationConflictException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "IDEMPOTENCY_CONFLICT",
            ex.getMessage(),
            HttpStatus.CONFLICT.value()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(com.englishflow.dictation.exception.DictationException.class)
    public ResponseEntity<ApiErrorResponse> handleDictationException(com.englishflow.dictation.exception.DictationException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            ex.getCode() != null ? ex.getCode() : "BAD_REQUEST",
            ex.getMessage(),
            HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(com.englishflow.shadowing.exception.ShadowingExerciseNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleShadowingExerciseNotFound(com.englishflow.shadowing.exception.ShadowingExerciseNotFoundException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "NOT_FOUND",
            ex.getMessage(),
            HttpStatus.NOT_FOUND.value()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(com.englishflow.shadowing.exception.ShadowingConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleShadowingConflict(com.englishflow.shadowing.exception.ShadowingConflictException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "IDEMPOTENCY_CONFLICT",
            ex.getMessage(),
            HttpStatus.CONFLICT.value()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(com.englishflow.shadowing.exception.ShadowingException.class)
    public ResponseEntity<ApiErrorResponse> handleShadowingException(com.englishflow.shadowing.exception.ShadowingException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            ex.getCode() != null ? ex.getCode() : "VALIDATION_FAILED",
            ex.getMessage(),
            HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(com.englishflow.quiz.exception.QuizNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleQuizNotFound(com.englishflow.quiz.exception.QuizNotFoundException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "QUIZ_NOT_FOUND",
            ex.getMessage(),
            HttpStatus.NOT_FOUND.value()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(com.englishflow.quiz.exception.QuizConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleQuizConflict(com.englishflow.quiz.exception.QuizConflictException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "IDEMPOTENCY_CONFLICT",
            ex.getMessage(),
            HttpStatus.CONFLICT.value()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(com.englishflow.quiz.exception.QuizException.class)
    public ResponseEntity<ApiErrorResponse> handleQuizException(com.englishflow.quiz.exception.QuizException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            ex.getCode() != null ? ex.getCode() : "VALIDATION_FAILED",
            ex.getMessage(),
            HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(com.englishflow.user.exception.InvalidSettingsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidSettings(com.englishflow.user.exception.InvalidSettingsException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "VALIDATION_FAILED",
            ex.getMessage(),
            HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(com.englishflow.user.exception.InvalidCurrentPasswordException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCurrentPassword(com.englishflow.user.exception.InvalidCurrentPasswordException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "INVALID_CURRENT_PASSWORD",
            ex.getMessage(),
            HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(com.englishflow.user.exception.UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(com.englishflow.user.exception.UserNotFoundException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "USER_NOT_FOUND",
            ex.getMessage(),
            HttpStatus.NOT_FOUND.value()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "VALIDATION_FAILED",
            ex.getMessage(),
            HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "VALIDATION_FAILED",
            "The request payload is malformed, invalid, or contains unsupported non-finite numbers.",
            HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLockingFailure(org.springframework.dao.OptimisticLockingFailureException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "OPTIMISTIC_LOCK_CONFLICT",
            "A concurrent modification conflict occurred. Please retry your review.",
            HttpStatus.CONFLICT.value()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler({InvalidCredentialsException.class, BadCredentialsException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(Exception ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "INVALID_CREDENTIALS",
            "Invalid email or password.",
            HttpStatus.UNAUTHORIZED.value()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "FORBIDDEN",
            "Access denied or CSRF verification failed.",
            HttpStatus.FORBIDDEN.value()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "UNAUTHORIZED",
            "Full authentication is required to access this resource.",
            HttpStatus.UNAUTHORIZED.value()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        ApiErrorResponse response = ApiErrorResponse.of(
            "METHOD_NOT_ALLOWED",
            "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint.",
            HttpStatus.METHOD_NOT_ALLOWED.value()
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled server exception caught in GlobalExceptionHandler", ex);
        ApiErrorResponse response = ApiErrorResponse.of(
            "INTERNAL_SERVER_ERROR",
            "An unexpected internal error occurred. Please try again later.",
            HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
