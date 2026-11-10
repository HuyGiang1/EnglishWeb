package com.englishflow.review;

import com.englishflow.review.dto.ReviewQueueResponse;
import com.englishflow.review.dto.ReviewSubmissionRequest;
import com.englishflow.review.dto.ReviewSubmissionResponse;
import com.englishflow.review.dto.SubmissionResult;
import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/review", "/api/v1/reviews"})
public class ReviewController {

    private final ReviewService reviewService;
    private final UserRepository userRepository;

    public ReviewController(ReviewService reviewService, UserRepository userRepository) {
        this.reviewService = reviewService;
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

    @GetMapping("/queue")
    public ResponseEntity<ReviewQueueResponse> getDueQueue(
        @RequestParam(name = "collectionId", required = false) String collectionId,
        @RequestParam(name = "limit", defaultValue = "50") int limit,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        ReviewQueueResponse response = reviewService.getDueQueue(userId, parseCollectionId(collectionId), limit);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/due-count")
    public com.englishflow.review.dto.ReviewDueCountResponse getDueCount(
        @RequestParam(name = "collectionId", required = false) String collectionId,
        Authentication authentication
    ) {
        return reviewService.getDueCount(getAuthenticatedUserId(authentication), parseCollectionId(collectionId));
    }

    private UUID parseCollectionId(String value) {
        if (value == null) return null;
        try {
            UUID id = UUID.fromString(value);
            if (id.toString().equalsIgnoreCase(value)) return id;
        } catch (IllegalArgumentException ignored) {
            // Never echo rejected scope input into API errors.
        }
        throw new IllegalArgumentException("collectionId must be a valid UUID.");
    }

    @PostMapping({"/grade", "/submissions"})
    public ResponseEntity<ReviewSubmissionResponse> submitGrade(
        @Valid @RequestBody ReviewSubmissionRequest request,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        SubmissionResult result = reviewService.submitGrade(userId, request);

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();
        if (result.isReplay()) {
            responseBuilder.header("Idempotent-Replay", "true");
        }

        return responseBuilder.body(result.response());
    }
}
