package com.englishflow.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LearningActivityEventRepository extends JpaRepository<LearningActivityEvent, UUID> {

    Optional<LearningActivityEvent> findByUserIdAndEventTypeAndAttemptId(
        UUID userId,
        String eventType,
        UUID attemptId
    );

    boolean existsByUserIdAndEventTypeAndAttemptId(
        UUID userId,
        String eventType,
        UUID attemptId
    );

    List<LearningActivityEvent> findByUserIdAndHistoricalLocalDate(UUID userId, LocalDate date);

    long countByUserId(UUID userId);
}
