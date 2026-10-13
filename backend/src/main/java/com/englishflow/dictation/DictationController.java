package com.englishflow.dictation;

import com.englishflow.dictation.dto.DictationExerciseDetailResponse;
import com.englishflow.dictation.dto.DictationExerciseSummaryResponse;
import com.englishflow.dictation.dto.DictationSubmissionResult;
import com.englishflow.dictation.dto.DictationSubmitRequest;
import com.englishflow.dictation.dto.DictationSubmitResponse;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dictation")
public class DictationController {

    private final DictationService dictationService;
    private final UserRepository userRepository;

    public DictationController(DictationService dictationService, UserRepository userRepository) {
        this.dictationService = dictationService;
        this.userRepository = userRepository;
    }

    private UUID getAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BadCredentialsException("Unauthenticated");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
            .map(User::getId)
            .orElseThrow(() -> new BadCredentialsException("User not found: " + email));
    }

    /**
     * Lists available dictation exercises for the authenticated learner.
     */
    @GetMapping
    public ResponseEntity<List<DictationExerciseSummaryResponse>> listExercises(
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        List<DictationExerciseSummaryResponse> exercises = dictationService.listExercises(userId);
        return ResponseEntity.ok(exercises);
    }

    /**
     * Retrieves metadata for a specific dictation exercise.
     * Masked canonical transcript is withheld to prevent cheating.
     */
    @GetMapping("/{contentId}")
    public ResponseEntity<DictationExerciseDetailResponse> getExercise(
        @PathVariable UUID contentId,
        Authentication authentication
    ) {
        getAuthenticatedUserId(authentication); // Verify authenticated
        DictationExerciseDetailResponse response = dictationService.getExercise(contentId);
        return ResponseEntity.ok(response);
    }

    /**
     * Submits a user's transcription attempt.
     * Evaluates accuracy, records activity, and returns diff evaluation.
     * Supports idempotent replay with 'Idempotent-Replay: true' header.
     */
    @PostMapping("/{contentId}/submit")
    public ResponseEntity<DictationSubmitResponse> submitTranscription(
        @PathVariable UUID contentId,
        @Valid @RequestBody DictationSubmitRequest request,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        DictationSubmissionResult result = dictationService.submitTranscription(
            userId,
            contentId,
            request
        );

        HttpHeaders headers = new HttpHeaders();
        if (result.isReplay()) {
            headers.add("Idempotent-Replay", "true");
        }

        return ResponseEntity.ok().headers(headers).body(result.response());
    }
}
