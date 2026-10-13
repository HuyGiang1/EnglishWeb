package com.englishflow.vocabulary;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VocabularyRepository extends JpaRepository<Vocabulary, UUID> {

    Optional<Vocabulary> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    boolean existsByUserIdAndNormalizedTermAndDeletedAtIsNull(UUID userId, String normalizedTerm);

    List<Vocabulary> findByUserIdAndNormalizedTermAndDeletedAtIsNull(UUID userId, String normalizedTerm);

    List<Vocabulary> findByUserIdAndNormalizedTermAndPartOfSpeechAndDeletedAtIsNull(
        UUID userId,
        String normalizedTerm,
        String partOfSpeech
    );

    long countByUserIdAndDeletedAtIsNull(UUID userId);

    @Query("""
        SELECT v FROM Vocabulary v
        WHERE v.userId = :userId
          AND v.deletedAt IS NULL
          AND (:search IS NULL OR :search = '' OR (
              LOWER(v.term) LIKE LOWER(CONCAT('%', :search, '%'))
              OR (v.englishDefinition IS NOT NULL AND LOWER(v.englishDefinition) LIKE LOWER(CONCAT('%', :search, '%')))
              OR (v.vietnameseMeaning IS NOT NULL AND LOWER(v.vietnameseMeaning) LIKE LOWER(CONCAT('%', :search, '%')))
          ))
          AND (:partOfSpeech IS NULL OR :partOfSpeech = '' OR v.partOfSpeech = :partOfSpeech)
          AND (:difficultyLevel IS NULL OR :difficultyLevel = '' OR v.difficultyLevel = :difficultyLevel)
    """)
    Page<Vocabulary> searchVocabularies(
        @Param("userId") UUID userId,
        @Param("search") String search,
        @Param("partOfSpeech") String partOfSpeech,
        @Param("difficultyLevel") String difficultyLevel,
        Pageable pageable
    );

    @Query("""
        SELECT v FROM Vocabulary v
        WHERE v.userId = :userId
          AND v.deletedAt IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM com.englishflow.review.SrsItem s
              WHERE s.userId = :userId AND s.vocabularyId = v.id
          )
        ORDER BY v.createdAt ASC
    """)
    List<Vocabulary> findUnprovisionedVocabularies(@Param("userId") UUID userId);

    @Query("""
        SELECT COUNT(v) FROM Vocabulary v
        WHERE v.userId = :userId
          AND v.deletedAt IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM com.englishflow.review.SrsItem s
              WHERE s.userId = :userId AND s.vocabularyId = v.id
          )
    """)
    long countUnprovisionedVocabularies(@Param("userId") UUID userId);
}
