package com.englishflow.shadowing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for ShadowingTake.
 */
@Repository
public interface ShadowingTakeRepository extends JpaRepository<ShadowingTake, UUID> {

    Optional<ShadowingTake> findByUserIdAndAttemptId(UUID userId, UUID attemptId);

    long countByUserIdAndContentId(UUID userId, UUID contentId);

    Optional<ShadowingTake> findFirstByUserIdAndContentIdOrderByCompletedAtDesc(UUID userId, UUID contentId);

    List<ShadowingTake> findByUserIdOrderByCompletedAtDesc(UUID userId);
}
