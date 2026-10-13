package com.englishflow.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "quiz_answers")
public class QuizAnswer {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "quiz_attempt_id", nullable = false)
    private UUID quizAttemptId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "user_answer", nullable = false, columnDefinition = "TEXT")
    private String userAnswer;

    @Column(name = "is_correct", nullable = false)
    private boolean isCorrect = false;

    @Column(name = "prompt_snapshot", columnDefinition = "TEXT")
    private String promptSnapshot;

    @Column(name = "canonical_answer_snapshot", columnDefinition = "TEXT")
    private String canonicalAnswerSnapshot;

    @Column(name = "explanation_snapshot", columnDefinition = "TEXT")
    private String explanationSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_snapshot", columnDefinition = "jsonb")
    private List<String> optionsSnapshot = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "answered_at", nullable = false, updatable = false)
    private Instant answeredAt;

    public QuizAnswer() {
        this.id = UUID.randomUUID();
    }

    public QuizAnswer(
        UUID id,
        UUID quizAttemptId,
        UUID questionId,
        String userAnswer,
        boolean isCorrect,
        String promptSnapshot,
        String canonicalAnswerSnapshot,
        String explanationSnapshot,
        List<String> optionsSnapshot
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.quizAttemptId = quizAttemptId;
        this.questionId = questionId;
        this.userAnswer = userAnswer;
        this.isCorrect = isCorrect;
        this.promptSnapshot = promptSnapshot;
        this.canonicalAnswerSnapshot = canonicalAnswerSnapshot;
        this.explanationSnapshot = explanationSnapshot;
        this.optionsSnapshot = optionsSnapshot != null ? optionsSnapshot : new ArrayList<>();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getQuizAttemptId() {
        return quizAttemptId;
    }

    public void setQuizAttemptId(UUID quizAttemptId) {
        this.quizAttemptId = quizAttemptId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public void setQuestionId(UUID questionId) {
        this.questionId = questionId;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
    }

    public boolean isCorrect() {
        return isCorrect;
    }

    public void setCorrect(boolean correct) {
        isCorrect = correct;
    }

    public String getPromptSnapshot() {
        return promptSnapshot;
    }

    public void setPromptSnapshot(String promptSnapshot) {
        this.promptSnapshot = promptSnapshot;
    }

    public String getCanonicalAnswerSnapshot() {
        return canonicalAnswerSnapshot;
    }

    public void setCanonicalAnswerSnapshot(String canonicalAnswerSnapshot) {
        this.canonicalAnswerSnapshot = canonicalAnswerSnapshot;
    }

    public String getExplanationSnapshot() {
        return explanationSnapshot;
    }

    public void setExplanationSnapshot(String explanationSnapshot) {
        this.explanationSnapshot = explanationSnapshot;
    }

    public List<String> getOptionsSnapshot() {
        return optionsSnapshot;
    }

    public void setOptionsSnapshot(List<String> optionsSnapshot) {
        this.optionsSnapshot = optionsSnapshot != null ? optionsSnapshot : new ArrayList<>();
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(Instant answeredAt) {
        this.answeredAt = answeredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QuizAnswer that = (QuizAnswer) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
