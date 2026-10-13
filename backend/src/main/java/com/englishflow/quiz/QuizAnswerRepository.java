package com.englishflow.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, UUID> {

    List<QuizAnswer> findByQuizAttemptIdOrderByAnsweredAtAsc(UUID quizAttemptId);

    Optional<QuizAnswer> findByQuizAttemptIdAndQuestionId(UUID quizAttemptId, UUID questionId);

    void deleteByQuizAttemptId(UUID quizAttemptId);
}
