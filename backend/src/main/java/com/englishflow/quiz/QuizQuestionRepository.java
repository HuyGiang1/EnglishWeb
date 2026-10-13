package com.englishflow.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    List<QuizQuestion> findByContentIdOrderByOrderIndexAsc(UUID contentId);

    Optional<QuizQuestion> findByIdAndContentId(UUID id, UUID contentId);

    long countByContentId(UUID contentId);
}
