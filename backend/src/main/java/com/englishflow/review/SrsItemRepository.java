package com.englishflow.review;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SrsItemRepository extends JpaRepository<SrsItem, UUID> {

    Optional<SrsItem> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SrsItem s WHERE s.id = :id AND s.userId = :userId")
    Optional<SrsItem> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    Optional<SrsItem> findByUserIdAndVocabularyId(UUID userId, UUID vocabularyId);

    boolean existsByUserIdAndVocabularyId(UUID userId, UUID vocabularyId);

    @Query("""
        SELECT s, v FROM SrsItem s
        JOIN com.englishflow.vocabulary.Vocabulary v ON s.vocabularyId = v.id AND s.userId = v.userId
        WHERE s.userId = :userId
          AND s.state != 'SUSPENDED'
          AND s.dueDate <= :currentDate
          AND v.deletedAt IS NULL
        ORDER BY s.dueDate ASC, s.createdAt ASC
    """)
    List<Object[]> findDueCardsWithVocabulary(
        @Param("userId") UUID userId,
        @Param("currentDate") LocalDate currentDate,
        Pageable pageable
    );

    @Query("""
        SELECT COUNT(s) FROM SrsItem s
        JOIN com.englishflow.vocabulary.Vocabulary v ON s.vocabularyId = v.id AND s.userId = v.userId
        WHERE s.userId = :userId
          AND s.state != 'SUSPENDED'
          AND s.dueDate <= :currentDate
          AND v.deletedAt IS NULL
    """)
    long countDueCards(
        @Param("userId") UUID userId,
        @Param("currentDate") LocalDate currentDate
    );

    @Query("""
        SELECT s, v FROM SrsItem s
        JOIN com.englishflow.vocabulary.Vocabulary v ON s.vocabularyId = v.id AND s.userId = v.userId
        WHERE s.userId = :userId
          AND s.state != 'SUSPENDED'
          AND s.dueDate <= :currentDate
          AND v.deletedAt IS NULL
          AND EXISTS (
              SELECT 1 FROM com.englishflow.vocabulary.VocabularyCollection vc
              WHERE vc.collectionId = :collectionId
                AND vc.userId = :userId AND vc.vocabularyId = v.id
          )
        ORDER BY s.dueDate ASC, s.createdAt ASC
    """)
    List<Object[]> findDueCardsInCollection(
        @Param("userId") UUID userId,
        @Param("collectionId") UUID collectionId,
        @Param("currentDate") LocalDate currentDate,
        Pageable pageable
    );

    @Query("""
        SELECT COUNT(s) FROM SrsItem s
        JOIN com.englishflow.vocabulary.Vocabulary v ON s.vocabularyId = v.id AND s.userId = v.userId
        WHERE s.userId = :userId
          AND s.state != 'SUSPENDED'
          AND s.dueDate <= :currentDate
          AND v.deletedAt IS NULL
          AND EXISTS (
              SELECT 1 FROM com.englishflow.vocabulary.VocabularyCollection vc
              WHERE vc.collectionId = :collectionId
                AND vc.userId = :userId AND vc.vocabularyId = v.id
          )
    """)
    long countDueCardsInCollection(
        @Param("userId") UUID userId,
        @Param("collectionId") UUID collectionId,
        @Param("currentDate") LocalDate currentDate
    );

    long countByUserId(UUID userId);
}
