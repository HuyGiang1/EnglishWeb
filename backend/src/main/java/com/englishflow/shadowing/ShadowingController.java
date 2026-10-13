package com.englishflow.shadowing;

import com.englishflow.shadowing.dto.ShadowingCompleteRequest;
import com.englishflow.shadowing.dto.ShadowingCompleteResponse;
import com.englishflow.shadowing.dto.ShadowingExerciseDetailResponse;
import com.englishflow.shadowing.dto.ShadowingExerciseSummaryResponse;
import com.englishflow.shadowing.dto.ShadowingSubmissionResult;
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
@RequestMapping("/api/v1/shadowing")
public class ShadowingController {

    private final ShadowingService shadowingService;
    private final UserRepository userRepository;

    public ShadowingController(ShadowingService shadowingService, UserRepository userRepository) {
        this.shadowingService = shadowingService;
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
     * Lists available shadowing exercises for the authenticated learner.
     */
    @GetMapping
    public ResponseEntity<List<ShadowingExerciseSummaryResponse>> listExercises(
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        List<ShadowingExerciseSummaryResponse> exercises = shadowingService.listExercises(userId);
        return ResponseEntity.ok(exercises);
    }

    /**
     * Retrieves prompt, audio URL, and reference transcript for a shadowing exercise.
     */
    @GetMapping("/{contentId}")
    public ResponseEntity<ShadowingExerciseDetailResponse> getExercise(
        @PathVariable UUID contentId,
        Authentication authentication
    ) {
        getAuthenticatedUserId(authentication); // Verify authenticated
        ShadowingExerciseDetailResponse response = shadowingService.getExercise(contentId);
        return ResponseEntity.ok(response);
    }

    /**
     * Completes a shadowing practice take with user self-assessment.
     * Enforces idempotency via attemptId and logs a single SHADOWING_TAKE event.
     */
    @PostMapping("/{contentId}/complete")
    public ResponseEntity<ShadowingCompleteResponse> completeTake(
        @PathVariable UUID contentId,
        @Valid @RequestBody ShadowingCompleteRequest request,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        ShadowingSubmissionResult result = shadowingService.completeTake(userId, contentId, request);

        HttpHeaders headers = new HttpHeaders();
        if (result.isReplay()) {
            headers.set("Idempotent-Replay", "true");
        }

        return ResponseEntity.ok().headers(headers).body(result.getResponse());
    }
}
