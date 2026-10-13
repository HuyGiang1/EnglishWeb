package com.englishflow.dictation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DictationAttemptRepository extends JpaRepository<DictationAttempt, UUID> {

    Optional<DictationAttempt> findByUserIdAndAttemptId(UUID userId, UUID attemptId);

    List<DictationAttempt> findByUserIdAndContentIdOrderByCompletedAtDesc(UUID userId, UUID contentId);

    Optional<DictationAttempt> findTopByUserIdAndContentIdOrderByCompletedAtDesc(UUID userId, UUID contentId);

    long countByUserId(UUID userId);
}
