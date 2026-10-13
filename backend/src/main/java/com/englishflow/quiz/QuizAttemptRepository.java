package com.englishflow.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    Optional<QuizAttempt> findByUserIdAndAttemptId(UUID userId, UUID attemptId);

    Optional<QuizAttempt> findByIdAndUserId(UUID id, UUID userId);

    List<QuizAttempt> findByUserIdAndStatusOrderByCompletedAtDesc(UUID userId, String status);

    long countByUserIdAndContentIdAndStatus(UUID userId, UUID contentId, String status);

    Optional<QuizAttempt> findFirstByUserIdAndContentIdAndStatusOrderByScorePercentageDesc(UUID userId, UUID contentId, String status);

    Optional<QuizAttempt> findFirstByUserIdAndContentIdAndStatusOrderByCompletedAtDesc(UUID userId, UUID contentId, String status);
}
