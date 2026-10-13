package com.englishflow.vocabulary;

import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.englishflow.vocabulary.dto.CreateVocabularyRequest;
import com.englishflow.vocabulary.dto.DuplicateCheckResponse;
import com.englishflow.vocabulary.dto.UpdateVocabularyRequest;
import com.englishflow.vocabulary.dto.VocabularyPageResponse;
import com.englishflow.vocabulary.dto.VocabularyResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/vocabulary", "/api/v1/vocabularies"})
public class VocabularyController {

    private final VocabularyService vocabularyService;
    private final CollectionService collectionService;
    private final UserRepository userRepository;

    public VocabularyController(
        VocabularyService vocabularyService,
        CollectionService collectionService,
        UserRepository userRepository
    ) {
        this.vocabularyService = vocabularyService;
        this.collectionService = collectionService;
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

    @PostMapping
    public ResponseEntity<VocabularyResponse> createVocabulary(
        @Valid @RequestBody CreateVocabularyRequest request,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        VocabularyResponse response = vocabularyService.createVocabulary(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<VocabularyPageResponse> listVocabularies(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String partOfSpeech,
        @RequestParam(required = false) String difficultyLevel,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);

        int boundedSize = Math.max(1, Math.min(size, 100));
        Sort sortObj = Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String field = parts[0].trim();
            Sort.Direction direction = (parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

            if ("term".equalsIgnoreCase(field)) {
                sortObj = Sort.by(direction, "term");
            } else if ("updatedAt".equalsIgnoreCase(field)) {
                sortObj = Sort.by(direction, "updatedAt");
            } else {
                sortObj = Sort.by(direction, "createdAt");
            }
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), boundedSize, sortObj);
        VocabularyPageResponse response = vocabularyService.listVocabularies(
            userId,
            search,
            partOfSpeech,
            difficultyLevel,
            pageable
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/duplicates/check")
    public ResponseEntity<DuplicateCheckResponse> checkDuplicates(
        @RequestParam("term") String term,
        @RequestParam(value = "partOfSpeech", required = false) String partOfSpeech,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        DuplicateCheckResponse response = vocabularyService.checkDuplicates(userId, term, partOfSpeech);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<VocabularyResponse> getVocabulary(
        @PathVariable UUID id,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        VocabularyResponse response = vocabularyService.getVocabulary(userId, id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VocabularyResponse> updateVocabulary(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateVocabularyRequest request,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        VocabularyResponse response = vocabularyService.updateVocabulary(userId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVocabulary(
        @PathVariable UUID id,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        vocabularyService.deleteVocabulary(userId, id);
    }

    @GetMapping("/{id}/collections")
    public ResponseEntity<java.util.List<com.englishflow.vocabulary.dto.CollectionSummaryDto>> getCollectionsForVocabulary(
        @PathVariable UUID id,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        java.util.List<com.englishflow.vocabulary.dto.CollectionSummaryDto> collections =
            collectionService.getCollectionsForVocabulary(userId, id);
        return ResponseEntity.ok(collections);
    }
}
