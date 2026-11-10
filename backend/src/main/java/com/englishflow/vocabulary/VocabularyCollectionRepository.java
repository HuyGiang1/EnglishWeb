package com.englishflow.vocabulary;

import com.englishflow.vocabulary.dto.CollectionSummaryDto;
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
public interface VocabularyCollectionRepository extends JpaRepository<VocabularyCollection, VocabularyCollectionId> {

    @Query("SELECT vc.vocabularyId FROM VocabularyCollection vc WHERE vc.collectionId = :collectionId AND vc.userId = :userId AND vc.vocabularyId IN :ids")
    List<UUID> findVocabularyIdsByCollectionAndUser(@Param("collectionId") UUID collectionId, @Param("userId") UUID userId, @Param("ids") List<UUID> ids);

    boolean existsByCollectionIdAndVocabularyId(UUID collectionId, UUID vocabularyId);

    Optional<VocabularyCollection> findByCollectionIdAndVocabularyIdAndUserId(UUID collectionId, UUID vocabularyId, UUID userId);

    @Query("""
        SELECT vc.collectionId AS collectionId, COUNT(v.id) AS wordCount
        FROM VocabularyCollection vc
        JOIN Vocabulary v ON v.id = vc.vocabularyId AND v.deletedAt IS NULL
        WHERE vc.collectionId IN :collectionIds
        GROUP BY vc.collectionId
    """)
    List<CollectionWordCountProjection> countActiveWordsByCollectionIds(@Param("collectionIds") List<UUID> collectionIds);

    @Query("""
        SELECT COUNT(v.id)
        FROM VocabularyCollection vc
        JOIN Vocabulary v ON v.id = vc.vocabularyId AND v.deletedAt IS NULL
        WHERE vc.collectionId = :collectionId
    """)
    long countActiveWordsByCollectionId(@Param("collectionId") UUID collectionId);

    @Query("""
        SELECT v FROM Vocabulary v
        JOIN VocabularyCollection vc ON vc.vocabularyId = v.id
        WHERE vc.collectionId = :collectionId
          AND vc.userId = :userId
          AND v.deletedAt IS NULL
        ORDER BY vc.addedAt DESC
    """)
    Page<Vocabulary> findVocabulariesByCollectionId(
        @Param("collectionId") UUID collectionId,
        @Param("userId") UUID userId,
        Pageable pageable
    );

    @Query("""
        SELECT new com.englishflow.vocabulary.dto.CollectionSummaryDto(c.id, c.title)
        FROM Collection c
        JOIN VocabularyCollection vc ON vc.collectionId = c.id
        WHERE vc.vocabularyId = :vocabularyId
          AND vc.userId = :userId
        ORDER BY c.title ASC
    """)
    List<CollectionSummaryDto> findCollectionsByVocabularyId(
        @Param("vocabularyId") UUID vocabularyId,
        @Param("userId") UUID userId
    );
}
