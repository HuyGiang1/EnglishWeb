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

    long countByUserIdAndHistoricalLocalDate(UUID userId, LocalDate date);

    @org.springframework.data.jpa.repository.Query("""
        SELECT DISTINCT e.historicalLocalDate
        FROM LearningActivityEvent e
        WHERE e.userId = :userId
        ORDER BY e.historicalLocalDate DESC
    """)
    List<LocalDate> findDistinctHistoricalLocalDates(@org.springframework.data.repository.query.Param("userId") UUID userId);

    @org.springframework.data.jpa.repository.Query("""
        SELECT e.eventType, COUNT(e.id), MAX(e.completedAtUtc)
        FROM LearningActivityEvent e
        WHERE e.userId = :userId
        GROUP BY e.eventType
        ORDER BY MAX(e.completedAtUtc) DESC
    """)
    List<Object[]> findRecentSessionSummaries(@org.springframework.data.repository.query.Param("userId") UUID userId);

    long countByUserId(UUID userId);
}
