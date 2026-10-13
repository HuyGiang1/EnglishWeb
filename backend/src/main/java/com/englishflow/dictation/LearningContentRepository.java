package com.englishflow.dictation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LearningContentRepository extends JpaRepository<LearningContent, UUID> {

    List<LearningContent> findByContentTypeAndIsPublishedTrueOrderByCefrLevelAsc(String contentType);

    Optional<LearningContent> findByIdAndContentTypeAndIsPublishedTrue(UUID id, String contentType);
}
