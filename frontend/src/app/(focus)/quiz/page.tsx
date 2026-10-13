"use client";

import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import {
  HelpCircle,
  CheckCircle2,
  XCircle,
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  RotateCcw,
  ShieldCheck,
  Check,
  LayoutList,
  Send,
  BookOpen,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import {
  quizApi,
  QuizSummary,
  QuizQuestion,
  QuizCompleteResponse,
  QuizHistoryItem,
} from "@/lib/api/quiz";
import { ApiError } from "@/lib/api/client";

type PageMode = "DIRECTORY" | "PRACTICE" | "RESULTS";

export default function QuizPage() {
  // Directory state
  const [mode, setMode] = useState<PageMode>("DIRECTORY");
  const [quizzes, setQuizzes] = useState<QuizSummary[]>([]);
  const [history, setHistory] = useState<QuizHistoryItem[]>([]);
  const [isLoadingList, setIsLoadingList] = useState(true);
  const [listError, setListError] = useState<string | null>(null);

  // Active Quiz practice state
  const [activeQuizId, setActiveQuizId] = useState<string | null>(null);
  const [activeQuizTitle, setActiveQuizTitle] = useState<string>("");
  const [quizAttemptId, setQuizAttemptId] = useState<string>("");
  const [attemptId, setAttemptId] = useState<string>("");
  const [questions, setQuestions] = useState<QuizQuestion[]>([]);
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState<number>(0);
  const [userAnswers, setUserAnswers] = useState<Record<string, string>>({});
  const [isStartingQuiz, setIsStartingQuiz] = useState<boolean>(false);
  const [isSubmittingFinal, setIsSubmittingFinal] = useState<boolean>(false);
  const [practiceError, setPracticeError] = useState<string | null>(null);

  // Review modal state before submitting
  const [showReviewConfirm, setShowReviewConfirm] = useState<boolean>(false);

  // Results state
  const [results, setResults] = useState<QuizCompleteResponse | null>(null);

  // Load directory list and history
  const loadDirectory = useCallback(async () => {
    setIsLoadingList(true);
    setListError(null);
    try {
      const [quizList, userHistory] = await Promise.all([
        quizApi.listQuizzes(),
        quizApi.getUserHistory().catch(() => []),
      ]);
      setQuizzes(quizList);
      setHistory(userHistory);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setListError(err.message || "Failed to load diagnostic quizzes.");
      } else {
        setListError("Network connectivity error. Please refresh the page.");
      }
    } finally {
      setIsLoadingList(false);
    }
  }, []);

  useEffect(() => {
    loadDirectory();
  }, [loadDirectory]);

  // Start a quiz attempt
  const handleStartQuiz = async (contentId: string, title: string) => {
    setIsStartingQuiz(true);
    setPracticeError(null);
    try {
      const newAttemptId = crypto.randomUUID();
      const startRes = await quizApi.startQuiz(contentId, newAttemptId);

      setActiveQuizId(contentId);
      setActiveQuizTitle(title || startRes.title);
      setQuizAttemptId(startRes.quizAttemptId);
      setAttemptId(startRes.attemptId);
      setQuestions(startRes.questions);
      setCurrentQuestionIndex(0);

      // Restore any draft answers if resumed
      const initialAnswers: Record<string, string> = {};
      if (startRes.savedAnswers && startRes.savedAnswers.length > 0) {
        startRes.savedAnswers.forEach((ans) => {
          if (ans.selectedAnswer) {
            initialAnswers[ans.questionId] = ans.selectedAnswer;
          }
        });
      }
      setUserAnswers(initialAnswers);
      setShowReviewConfirm(false);
      setResults(null);
      setMode("PRACTICE");
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setPracticeError(err.message || "Failed to start quiz attempt.");
      } else {
        setPracticeError("Network error while starting quiz.");
      }
    } finally {
      setIsStartingQuiz(false);
    }
  };

  // Handle answering a question (draft save)
  const handleSelectAnswer = async (questionId: string, answerValue: string) => {
    const updated = { ...userAnswers, [questionId]: answerValue };
    setUserAnswers(updated);

    // Save draft asynchronously without blocking UI
    try {
      await quizApi.submitAnswer(quizAttemptId, {
        questionId,
        attemptId,
        selectedAnswer: answerValue,
      });
    } catch {
      // Draft network save failure is non-fatal since final submit sends full payload
    }
  };

  // Final submission
  const handleConfirmSubmit = async () => {
    if (isSubmittingFinal) return;
    setIsSubmittingFinal(true);
    setPracticeError(null);

    try {
      // Build answers payload
      const answerPayload = questions.map((q) => ({
        questionId: q.questionId,
        selectedAnswer: userAnswers[q.questionId] || "",
      }));

      const completeRes = await quizApi.completeQuiz(quizAttemptId, {
        attemptId,
        answers: answerPayload,
      });

      setResults(completeRes);
      setShowReviewConfirm(false);
      setMode("RESULTS");
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setPracticeError(err.message || "Quiz evaluation failed. Please retry submission.");
      } else {
        setPracticeError("Network error submitting quiz. Your answers are preserved.");
      }
    } finally {
      setIsSubmittingFinal(false);
    }
  };

  // View historical completed attempt
  const handleViewHistoricalResult = async (historyItem: QuizHistoryItem) => {
    setIsLoadingList(true);
    try {
      const scorecard = await quizApi.getCompletedAttempt(historyItem.quizAttemptId);
      setResults(scorecard);
      setActiveQuizTitle(historyItem.title);
      setMode("RESULTS");
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setListError(err.message || "Failed to load past scorecard.");
      } else {
        setListError("Failed to load past scorecard.");
      }
    } finally {
      setIsLoadingList(false);
    }
  };

  // Helpers for answered status
  const currentQuestion = questions[currentQuestionIndex];
  const totalCount = questions.length;
  const answeredCount = Object.keys(userAnswers).filter(
    (qid) => userAnswers[qid] && userAnswers[qid].trim().length > 0
  ).length;
  const unansweredCount = totalCount - answeredCount;

  // =========================================================================
  // VIEW: DIRECTORY
  // =========================================================================
  if (mode === "DIRECTORY") {
    return (
      <div className="w-full max-w-4xl mx-auto space-y-8 py-6">
        {/* Header */}
        <div className="space-y-2 border-b border-border pb-6">
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs uppercase tracking-widest text-muted-foreground">
              Assessment Engine
            </span>
            <Badge variant="outline" className="font-mono text-[10px] bg-muted">
              Milestone M2
            </Badge>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
            Diagnostic Quizzes
          </h1>
          <p className="text-sm text-muted-foreground max-w-2xl">
            Server-evaluated assessments measuring vocabulary recognition, grammar structures, and formal discourse.
            Completed quizzes record verified daily study telemetry.
          </p>
        </div>

        {/* Error Banner */}
        {listError && (
          <div className="p-4 rounded-md border border-destructive/50 bg-destructive/10 text-destructive text-sm flex items-start gap-3">
            <AlertCircle className="w-5 h-5 shrink-0 mt-0.5" />
            <div>
              <p className="font-medium">Failed to load quizzes</p>
              <p className="text-xs mt-1 text-destructive/90">{listError}</p>
              <Button
                variant="outline"
                size="sm"
                onClick={loadDirectory}
                className="mt-3 text-xs"
              >
                Retry Loading
              </Button>
            </div>
          </div>
        )}

        {/* Loading Skeleton */}
        {isLoadingList ? (
          <div className="grid gap-4 sm:grid-cols-2">
            {[1, 2].map((i) => (
              <Card key={i} className="p-6 border-border bg-card space-y-4 animate-pulse">
                <div className="h-4 bg-muted rounded w-1/3" />
                <div className="h-6 bg-muted rounded w-3/4" />
                <div className="h-12 bg-muted rounded w-full" />
                <div className="h-10 bg-muted rounded w-full mt-4" />
              </Card>
            ))}
          </div>
        ) : (
          <div className="space-y-8">
            {/* Quiz Catalog */}
            <div className="space-y-4">
              <h2 className="text-sm font-semibold tracking-wide uppercase text-muted-foreground">
                Available Assessments
              </h2>
              <div className="grid gap-4 sm:grid-cols-2">
                {quizzes.map((quiz) => (
                  <Card
                    key={quiz.id}
                    className="p-6 border-border bg-card flex flex-col justify-between hover:border-foreground/40 transition-colors"
                  >
                    <div className="space-y-3">
                      <div className="flex items-center justify-between gap-2">
                        <Badge variant="outline" className="font-mono text-xs font-semibold">
                          CEFR {quiz.cefrLevel}
                        </Badge>
                        <Badge variant="secondary" className="text-[11px] font-normal">
                          {quiz.topicTag}
                        </Badge>
                      </div>

                      <div>
                        <h3 className="text-base font-semibold text-foreground">
                          {quiz.title}
                        </h3>
                        <p className="text-xs text-muted-foreground mt-1 line-clamp-3">
                          {quiz.description}
                        </p>
                      </div>

                      <div className="flex items-center gap-4 text-xs font-mono text-muted-foreground pt-2 border-t border-border">
                        <span className="flex items-center gap-1.5">
                          <HelpCircle className="w-3.5 h-3.5" />
                          {quiz.questionCount} Questions
                        </span>
                        <span>·</span>
                        <span>
                          {quiz.attemptCount > 0
                            ? `${quiz.attemptCount} Attempt${quiz.attemptCount === 1 ? "" : "s"}`
                            : "Not attempted"}
                        </span>
                        {quiz.bestScore !== null && (
                          <>
                            <span>·</span>
                            <span className="text-foreground font-semibold">
                              Best: {quiz.bestScore.toFixed(1)}%
                            </span>
                          </>
                        )}
                      </div>
                    </div>

                    <div className="pt-6">
                      <Button
                        className="w-full font-medium"
                        onClick={() => handleStartQuiz(quiz.id, quiz.title)}
                        disabled={isStartingQuiz}
                      >
                        {quiz.attemptCount > 0 ? "Retake Assessment" : "Start Diagnostic Quiz"}
                        <ArrowRight className="w-4 h-4 ml-2" />
                      </Button>
                    </div>
                  </Card>
                ))}
              </div>
            </div>

            {/* Historical Submissions */}
            {history.length > 0 && (
              <div className="space-y-4 pt-6 border-t border-border">
                <div className="flex items-center justify-between">
                  <h2 className="text-sm font-semibold tracking-wide uppercase text-muted-foreground">
                    Attempt History
                  </h2>
                  <span className="text-xs font-mono text-muted-foreground">
                    {history.length} completed attempt{history.length === 1 ? "" : "s"}
                  </span>
                </div>

                <div className="divide-y divide-border border border-border rounded-lg overflow-hidden bg-card">
                  {history.map((item, idx) => (
                    <div
                      key={item.attemptId || idx}
                      className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs"
                    >
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-foreground text-sm">
                            {item.title}
                          </span>
                          <Badge variant="outline" className="font-mono text-[10px]">
                            {item.cefrLevel}
                          </Badge>
                        </div>
                        <div className="flex items-center gap-3 text-muted-foreground font-mono text-[11px]">
                          <span>
                            {new Date(item.completedAt).toLocaleDateString(undefined, {
                              year: "numeric",
                              month: "short",
                              day: "numeric",
                              hour: "2-digit",
                              minute: "2-digit",
                            })}
                          </span>
                          <span>·</span>
                          <span>
                            {item.correctAnswers} / {item.totalQuestions} Correct
                          </span>
                        </div>
                      </div>

                      <div className="flex items-center gap-4">
                        <div className="text-right">
                          <span className="font-mono text-base font-bold text-foreground">
                            {item.scorePercentage.toFixed(1)}%
                          </span>
                        </div>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleViewHistoricalResult(item)}
                          className="text-xs"
                        >
                          Review Scorecard
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    );
  }

  // =========================================================================
  // VIEW: PRACTICE (QUESTION INTERFACE & REVIEW MODAL)
  // =========================================================================
  if (mode === "PRACTICE" && currentQuestion) {
    const selectedAnswer = userAnswers[currentQuestion.questionId] || "";

    return (
      <div className="w-full max-w-2xl mx-auto space-y-6 py-4">
        {/* Top Navigation & Progress */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                if (window.confirm("Return to catalog? Your answered progress will be preserved for this attempt.")) {
                  loadDirectory();
                  setMode("DIRECTORY");
                }
              }}
              className="text-xs text-muted-foreground hover:text-foreground"
            >
              <ArrowLeft className="w-4 h-4 mr-1" />
              Exit to Catalog
            </Button>
            <div className="flex items-center gap-2">
              <span className="text-xs font-mono text-muted-foreground">
                Question {currentQuestionIndex + 1} of {totalCount}
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setShowReviewConfirm(true)}
                className="text-xs font-mono h-7"
              >
                <LayoutList className="w-3.5 h-3.5 mr-1.5" />
                Review ({answeredCount}/{totalCount})
              </Button>
            </div>
          </div>

          {/* Progress Indicator Bar */}
          <div className="h-1.5 w-full bg-muted rounded-full overflow-hidden">
            <div
              className="h-full bg-foreground transition-all duration-200"
              style={{
                width: `${Math.round(((currentQuestionIndex + 1) / totalCount) * 100)}%`,
              }}
            />
          </div>

          {/* Question Dots Navigation */}
          <div className="flex items-center justify-center gap-1.5 pt-2">
            {questions.map((q, idx) => {
              const isAnswered = Boolean(userAnswers[q.questionId]?.trim());
              const isCurrent = idx === currentQuestionIndex;

              return (
                <button
                  key={q.questionId}
                  onClick={() => setCurrentQuestionIndex(idx)}
                  className={`w-7 h-7 rounded text-xs font-mono transition-colors flex items-center justify-center border ${
                    isCurrent
                      ? "border-foreground bg-foreground text-background font-bold shadow-sm"
                      : isAnswered
                      ? "border-border bg-card text-foreground font-medium hover:border-foreground"
                      : "border-dashed border-border bg-muted/40 text-muted-foreground hover:border-border"
                  }`}
                  aria-label={`Jump to question ${idx + 1}`}
                >
                  {idx + 1}
                </button>
              );
            })}
          </div>
        </div>

        {/* Practice Error Message */}
        {practiceError && (
          <div className="p-4 rounded-md border border-destructive/50 bg-destructive/10 text-destructive text-sm flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{practiceError}</span>
          </div>
        )}

        {/* Question Card */}
        <Card className="p-6 sm:p-8 border-border bg-card space-y-6">
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <Badge variant="outline" className="font-mono text-[11px]">
                {currentQuestion.questionType === "CLOZE" ? "Fill in the Blank" : "Multiple Choice"}
              </Badge>
              <span className="font-mono text-xs text-muted-foreground">
                Question {currentQuestionIndex + 1}
              </span>
            </div>

            <h2 className="text-lg sm:text-xl font-medium text-foreground leading-relaxed">
              {currentQuestion.prompt}
            </h2>
          </div>

          {/* Options / Input Area */}
          <div className="pt-2">
            {currentQuestion.questionType === "MULTIPLE_CHOICE" ? (
              <div className="space-y-3">
                {currentQuestion.options.map((option, optIdx) => {
                  const isSelected = selectedAnswer === option;
                  const optionLetters = ["A", "B", "C", "D", "E"];
                  const letter = optionLetters[optIdx] || `${optIdx + 1}`;

                  return (
                    <button
                      key={optIdx}
                      type="button"
                      onClick={() => handleSelectAnswer(currentQuestion.questionId, option)}
                      className={`w-full p-4 rounded-lg border text-left transition-colors flex items-center justify-between ${
                        isSelected
                          ? "border-foreground bg-foreground/5 shadow-sm text-foreground font-medium"
                          : "border-border bg-card hover:border-foreground/50 text-foreground"
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <span
                          className={`w-6 h-6 rounded flex items-center justify-center font-mono text-xs ${
                            isSelected
                              ? "bg-foreground text-background font-bold"
                              : "border border-border text-muted-foreground"
                          }`}
                        >
                          {letter}
                        </span>
                        <span className="text-sm">{option}</span>
                      </div>
                      {isSelected && <Check className="w-4 h-4 text-foreground shrink-0" />}
                    </button>
                  );
                })}
              </div>
            ) : (
              // CLOZE (Fill in the Blank)
              <div className="space-y-3">
                <label className="text-xs font-mono text-muted-foreground block">
                  Enter your answer:
                </label>
                <input
                  type="text"
                  value={selectedAnswer}
                  onChange={(e) =>
                    handleSelectAnswer(currentQuestion.questionId, e.target.value)
                  }
                  placeholder="Type word or phrase..."
                  className="w-full px-4 py-3 rounded-lg border border-border bg-background text-foreground font-mono text-sm focus:outline-none focus:ring-1 focus:ring-foreground"
                  autoFocus
                />
                <p className="text-[11px] text-muted-foreground">
                  Case-insensitive; spelling and syntax are strictly verified.
                </p>
              </div>
            )}
          </div>
        </Card>

        {/* Pager Footer */}
        <div className="flex items-center justify-between pt-2">
          <Button
            variant="outline"
            onClick={() => setCurrentQuestionIndex((prev) => Math.max(0, prev - 1))}
            disabled={currentQuestionIndex === 0}
            className="text-xs"
          >
            <ArrowLeft className="w-4 h-4 mr-1.5" />
            Previous
          </Button>

          {currentQuestionIndex < totalCount - 1 ? (
            <Button
              onClick={() =>
                setCurrentQuestionIndex((prev) => Math.min(totalCount - 1, prev + 1))
              }
              className="text-xs"
            >
              Next Question
              <ArrowRight className="w-4 h-4 ml-1.5" />
            </Button>
          ) : (
            <Button
              onClick={() => setShowReviewConfirm(true)}
              className="text-xs font-semibold bg-foreground text-background hover:bg-foreground/90"
            >
              Review & Submit
              <Send className="w-4 h-4 ml-1.5" />
            </Button>
          )}
        </div>

        {/* ===================================================================
            MODAL: REVIEW BEFORE SUBMIT
           =================================================================== */}
        {showReviewConfirm && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-background/80 backdrop-blur-sm">
            <Card className="w-full max-w-md p-6 border-border bg-card shadow-lg space-y-6 animate-in fade-in zoom-in-95 duration-100">
              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <ShieldCheck className="w-5 h-5 text-foreground" />
                  <h3 className="text-lg font-bold text-foreground">Confirm Submission</h3>
                </div>
                <p className="text-xs text-muted-foreground">
                  Review your completion status before final authoritative server grading.
                </p>
              </div>

              {/* Status summary */}
              <div className="grid grid-cols-2 gap-3 p-4 rounded-lg border border-border bg-muted/20 font-mono text-xs">
                <div>
                  <span className="text-muted-foreground block text-[11px]">Answered</span>
                  <span className="text-foreground text-lg font-bold">
                    {answeredCount} / {totalCount}
                  </span>
                </div>
                <div>
                  <span className="text-muted-foreground block text-[11px]">Unanswered</span>
                  <span
                    className={`text-lg font-bold ${
                      unansweredCount > 0 ? "text-amber-500" : "text-foreground"
                    }`}
                  >
                    {unansweredCount}
                  </span>
                </div>
              </div>

              {unansweredCount > 0 && (
                <div className="p-3 rounded border border-amber-500/30 bg-amber-500/10 text-amber-500 text-xs space-y-2">
                  <div className="flex items-center gap-2 font-medium">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>Unanswered questions receive 0 points</span>
                  </div>
                  <div className="flex flex-wrap gap-1 pt-1">
                    {questions.map((q, idx) => {
                      if (!userAnswers[q.questionId]?.trim()) {
                        return (
                          <button
                            key={q.questionId}
                            onClick={() => {
                              setCurrentQuestionIndex(idx);
                              setShowReviewConfirm(false);
                            }}
                            className="px-2 py-0.5 rounded bg-amber-500/20 text-amber-500 text-[11px] font-mono hover:bg-amber-500/30"
                          >
                            Q{idx + 1}
                          </button>
                        );
                      }
                      return null;
                    })}
                  </div>
                </div>
              )}

              {/* Action Buttons */}
              <div className="flex items-center justify-end gap-3 pt-2">
                <Button
                  variant="outline"
                  onClick={() => setShowReviewConfirm(false)}
                  disabled={isSubmittingFinal}
                  className="text-xs"
                >
                  Return to Questions
                </Button>
                <Button
                  onClick={handleConfirmSubmit}
                  disabled={isSubmittingFinal}
                  className="text-xs font-semibold"
                >
                  {isSubmittingFinal ? "Grading..." : "Submit Quiz"}
                </Button>
              </div>
            </Card>
          </div>
        )}
      </div>
    );
  }

  // =========================================================================
  // VIEW: RESULTS SCORECARD
  // =========================================================================
  if (mode === "RESULTS" && results) {
    return (
      <div className="w-full max-w-3xl mx-auto space-y-8 py-6">
        {/* Results Banner Card */}
        <Card className="p-8 border-border bg-card text-center space-y-6">
          <div className="space-y-2">
            <Badge variant="outline" className="font-mono text-xs uppercase tracking-wider">
              Diagnostic Scorecard
            </Badge>
            <h1 className="text-2xl sm:text-3xl font-bold text-foreground">
              {results.title || activeQuizTitle || "Assessment Completed"}
            </h1>
          </div>

          {/* Primary Score */}
          <div className="py-2">
            <div className="text-5xl sm:text-6xl font-mono font-bold tracking-tight text-foreground">
              {results.scorePercentage.toFixed(1)}%
            </div>
            <p className="text-xs font-mono text-muted-foreground mt-2">
              Server-Evaluated Accuracy Rate
            </p>
          </div>

          {/* Breakdown Matrix */}
          <div className="grid grid-cols-3 gap-2 sm:gap-4 max-w-md mx-auto p-4 rounded-lg border border-border bg-muted/20 font-mono text-xs">
            <div className="space-y-1">
              <span className="text-muted-foreground text-[11px] block">Correct</span>
              <span className="text-foreground text-lg font-bold">
                {results.correctCount}
              </span>
            </div>
            <div className="space-y-1 border-x border-border">
              <span className="text-muted-foreground text-[11px] block">Incorrect</span>
              <span className="text-foreground text-lg font-bold">
                {results.incorrectCount}
              </span>
            </div>
            <div className="space-y-1">
              <span className="text-muted-foreground text-[11px] block">Unanswered</span>
              <span className="text-foreground text-lg font-bold">
                {results.unansweredCount}
              </span>
            </div>
          </div>

          {/* Activity telemetry badge */}
          {results.verifiedActivityCreated && (
            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full border border-border bg-muted/40 text-xs font-mono text-foreground">
              <ShieldCheck className="w-4 h-4 text-foreground" />
              <span>Verified learning activity logged to Dashboard</span>
            </div>
          )}

          {/* Action Row */}
          <div className="flex flex-wrap items-center justify-center gap-3 pt-4 border-t border-border">
            {activeQuizId && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => handleStartQuiz(activeQuizId, activeQuizTitle)}
                className="text-xs"
              >
                <RotateCcw className="w-3.5 h-3.5 mr-1.5" />
                Retake Assessment
              </Button>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                loadDirectory();
                setMode("DIRECTORY");
              }}
              className="text-xs"
            >
              <LayoutList className="w-3.5 h-3.5 mr-1.5" />
              Quiz Catalog
            </Button>
            <Link href="/dashboard">
              <Button size="sm" className="text-xs">
                View Dashboard
                <ArrowRight className="w-3.5 h-3.5 ml-1.5" />
              </Button>
            </Link>
          </div>
        </Card>

        {/* Detailed Question Review List */}
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold tracking-wide uppercase text-muted-foreground">
              Question-by-Question Review
            </h2>
            <span className="text-xs font-mono text-muted-foreground">
              {results.questions.length} Items
            </span>
          </div>

          <div className="space-y-4">
            {results.questions.map((q, idx) => {
              const isUnanswered = !q.selectedAnswer || q.selectedAnswer.trim() === "";

              return (
                <Card
                  key={q.questionId}
                  className={`p-6 border bg-card space-y-4 transition-colors ${
                    q.isCorrect
                      ? "border-border hover:border-foreground/30"
                      : "border-border/80 hover:border-foreground/30"
                  }`}
                >
                  {/* Question header */}
                  <div className="flex items-start justify-between gap-3">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-xs font-semibold text-muted-foreground">
                          Question {idx + 1}
                        </span>
                        <Badge variant="outline" className="font-mono text-[10px]">
                          {q.questionType === "CLOZE" ? "Cloze" : "Multiple Choice"}
                        </Badge>
                      </div>
                      <p className="text-base font-medium text-foreground leading-relaxed">
                        {q.prompt}
                      </p>
                    </div>

                    <div className="shrink-0 mt-1">
                      {q.isCorrect ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-foreground text-background font-mono text-xs font-bold">
                          <CheckCircle2 className="w-3.5 h-3.5" />
                          Correct
                        </span>
                      ) : isUnanswered ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-muted text-muted-foreground font-mono text-xs">
                          Unanswered
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md border border-border text-foreground font-mono text-xs font-semibold">
                          <XCircle className="w-3.5 h-3.5" />
                          Incorrect
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Answers Comparison */}
                  <div className="grid sm:grid-cols-2 gap-3 p-4 rounded-lg border border-border bg-muted/20 text-xs font-mono">
                    <div className="space-y-1">
                      <span className="text-muted-foreground text-[11px] block">Your Response</span>
                      <span
                        className={`font-semibold ${
                          q.isCorrect
                            ? "text-foreground"
                            : isUnanswered
                            ? "text-muted-foreground italic"
                            : "text-foreground line-through"
                        }`}
                      >
                        {isUnanswered ? "None (Skipped)" : q.selectedAnswer}
                      </span>
                    </div>
                    <div className="space-y-1">
                      <span className="text-muted-foreground text-[11px] block">Canonical Answer</span>
                      <span className="text-foreground font-bold">{q.canonicalAnswer}</span>
                    </div>
                  </div>

                  {/* Explanation card */}
                  {q.explanation && (
                    <div className="p-3.5 rounded border border-border bg-muted/10 text-xs space-y-1">
                      <span className="font-semibold text-foreground flex items-center gap-1.5">
                        <BookOpen className="w-3.5 h-3.5 text-foreground" />
                        Explanation
                      </span>
                      <p className="text-muted-foreground leading-relaxed pl-5">
                        {q.explanation}
                      </p>
                    </div>
                  )}
                </Card>
              );
            })}
          </div>
        </div>
      </div>
    );
  }

  return null;
}
