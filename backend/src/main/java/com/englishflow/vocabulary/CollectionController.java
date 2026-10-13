package com.englishflow.vocabulary;

import com.englishflow.user.User;
import com.englishflow.user.UserRepository;
import com.englishflow.vocabulary.dto.AssignVocabularyRequest;
import com.englishflow.vocabulary.dto.AssignVocabularyResponse;
import com.englishflow.vocabulary.dto.CollectionDetailResponse;
import com.englishflow.vocabulary.dto.CollectionPageResponse;
import com.englishflow.vocabulary.dto.CollectionResponse;
import com.englishflow.vocabulary.dto.CreateCollectionRequest;
import com.englishflow.vocabulary.dto.UpdateCollectionRequest;
import com.englishflow.vocabulary.dto.VocabularyPageResponse;
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
@RequestMapping("/api/v1/collections")
public class CollectionController {

    private final CollectionService collectionService;
    private final UserRepository userRepository;

    public CollectionController(CollectionService collectionService, UserRepository userRepository) {
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
    public ResponseEntity<CollectionResponse> createCollection(
        @Valid @RequestBody CreateCollectionRequest request,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        CollectionResponse response = collectionService.createCollection(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<CollectionPageResponse> listCollections(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "updatedAt,desc") String sort,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);

        int boundedSize = Math.max(1, Math.min(size, 100));
        Sort sortObj = Sort.by(Sort.Direction.DESC, "updatedAt");
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String field = parts[0].trim();
            Sort.Direction direction = (parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

            if ("title".equalsIgnoreCase(field)) {
                sortObj = Sort.by(direction, "title");
            } else if ("createdAt".equalsIgnoreCase(field)) {
                sortObj = Sort.by(direction, "createdAt");
            } else {
                sortObj = Sort.by(direction, "updatedAt");
            }
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), boundedSize, sortObj);
        CollectionPageResponse response = collectionService.listCollections(userId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CollectionDetailResponse> getCollection(
        @PathVariable UUID id,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        CollectionDetailResponse response = collectionService.getCollection(userId, id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CollectionResponse> updateCollection(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateCollectionRequest request,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        CollectionResponse response = collectionService.updateCollection(userId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCollection(
        @PathVariable UUID id,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        collectionService.deleteCollection(userId, id);
    }

    @GetMapping("/{id}/vocabularies")
    public ResponseEntity<VocabularyPageResponse> getVocabulariesInCollection(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        int boundedSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(Math.max(0, page), boundedSize);
        VocabularyPageResponse response = collectionService.getVocabulariesInCollection(userId, id, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping({ "/{id}/vocabularies", "/{id}/words" })
    public ResponseEntity<AssignVocabularyResponse> assignVocabularies(
        @PathVariable UUID id,
        @Valid @RequestBody AssignVocabularyRequest request,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        AssignVocabularyResponse response = collectionService.assignVocabularies(userId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping({ "/{id}/vocabularies/{vocabId}", "/{id}/words/{vocabId}" })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeVocabularyFromCollection(
        @PathVariable UUID id,
        @PathVariable UUID vocabId,
        Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);
        collectionService.removeVocabularyFromCollection(userId, id, vocabId);
    }
}
