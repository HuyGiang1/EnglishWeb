package com.englishflow.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SrsReviewHistoryRepository extends JpaRepository<SrsReviewHistory, UUID> {

    Optional<SrsReviewHistory> findByUserIdAndAttemptId(UUID userId, UUID attemptId);

    boolean existsByUserIdAndAttemptId(UUID userId, UUID attemptId);

    long countByUserId(UUID userId);
}
