"use client";

import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import {
  ArrowLeft,
  CheckCircle2,
  AlertCircle,
  RotateCcw,
  BookOpen,
  Volume2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { reviewApi, type ReviewCard, type ReviewRating, type ReviewSubmissionResponse } from "@/lib/api/review";

interface SessionResultItem {
  term: string;
  grade: ReviewRating;
  newIntervalDays: number;
}

export default function ReviewFocusPage() {
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [cards, setCards] = useState<ReviewCard[]>([]);
  const [currentIndex, setCurrentIndex] = useState<number>(0);
  const [isRevealed, setIsRevealed] = useState<boolean>(false);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  // Each card attempt has a stable attempt_id that is preserved on network retries
  const [currentAttemptId, setCurrentAttemptId] = useState<string>("");
  const [lastGradeAttempted, setLastGradeAttempted] = useState<ReviewRating | null>(null);

  // Real review session summary items
  const [sessionResults, setSessionResults] = useState<SessionResultItem[]>([]);
  const [startTime, setStartTime] = useState<number>(Date.now());

  // Load review queue on mount
  const loadQueue = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await reviewApi.getQueue({ limit: 50 });
      setCards(res.items || []);
      setCurrentIndex(0);
      setIsRevealed(false);
      setCurrentAttemptId(crypto.randomUUID());
      setLastGradeAttempted(null);
      setSessionResults([]);
      setStartTime(Date.now());
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to load review queue";
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadQueue();
  }, [loadQueue]);

  const currentCard: ReviewCard | undefined = cards[currentIndex];
  const isComplete = !loading && !error && cards.length > 0 && currentIndex >= cards.length;

  // Grade submission handler
  const handleGrade = useCallback(
    async (grade: ReviewRating) => {
      if (!currentCard || submitting) return;

      setSubmitting(true);
      setSubmitError(null);
      setLastGradeAttempted(grade);

      const elapsedSeconds = Math.max(1, Math.round((Date.now() - startTime) / 1000));

      try {
        const response: ReviewSubmissionResponse = await reviewApi.submitGrade({
          srsItemId: currentCard.srsItemId,
          attemptId: currentAttemptId,
          grade,
          durationSeconds: elapsedSeconds,
        });

        // Record real result in session summary
        setSessionResults((prev) => [
          ...prev,
          {
            term: currentCard.term,
            grade,
            newIntervalDays: response.newIntervalDays,
          },
        ]);

        // Advance to next card
        setCurrentIndex((prev) => prev + 1);
        setIsRevealed(false);
        setCurrentAttemptId(crypto.randomUUID());
        setLastGradeAttempted(null);
        setStartTime(Date.now());
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : "Grade submission failed. Please retry.";
        setSubmitError(msg);
      } finally {
        setSubmitting(false);
      }
    },
    [currentCard, currentAttemptId, submitting, startTime]
  );

  // Retry the same attempt with the same attempt_id
  const handleRetry = useCallback(() => {
    if (lastGradeAttempted) {
      handleGrade(lastGradeAttempted);
    }
  }, [handleGrade, lastGradeAttempted]);

  // Keyboard navigation
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Don't intercept when user is typing in form controls
      const target = e.target as HTMLElement | null;
      if (
        target &&
        (target.tagName === "INPUT" ||
          target.tagName === "TEXTAREA" ||
          target.isContentEditable)
      ) {
        return;
      }

      if (loading || error || !currentCard || submitting) {
        return;
      }

      if (!isRevealed) {
        if (e.code === "Space" || e.code === "Enter") {
          e.preventDefault();
          setIsRevealed(true);
        }
      } else {
        if (e.key === "1") {
          e.preventDefault();
          handleGrade("AGAIN");
        } else if (e.key === "2") {
          e.preventDefault();
          handleGrade("HARD");
        } else if (e.key === "3") {
          e.preventDefault();
          handleGrade("GOOD");
        } else if (e.key === "4") {
          e.preventDefault();
          handleGrade("EASY");
        }
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [loading, error, currentCard, isRevealed, submitting, handleGrade]);

  // Pronunciation audio preview (browser Web Speech API)
  const speakTerm = (term: string) => {
    if (typeof window !== "undefined" && "speechSynthesis" in window) {
      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(term);
      utterance.lang = "en-US";
      utterance.rate = 0.9;
      window.speechSynthesis.speak(utterance);
    }
  };

  // 1. Loading State
  if (loading) {
    return (
      <div className="w-full flex flex-col items-center justify-center min-h-[380px] gap-4" data-testid="review-loading">
        <div className="size-8 rounded-full border-2 border-border border-t-foreground animate-spin" />
        <p className="text-sm text-muted-foreground font-mono">Loading review queue...</p>
      </div>
    );
  }

  // 2. Queue Error State
  if (error) {
    return (
      <Card className="w-full max-w-lg p-8 border-border bg-card text-center flex flex-col items-center gap-4 mx-auto" data-testid="review-error">
        <div className="size-10 rounded-full bg-destructive/10 text-destructive flex items-center justify-center">
          <AlertCircle className="size-5" />
        </div>
        <div>
          <h2 className="text-lg font-semibold text-foreground">Failed to Load Review Queue</h2>
          <p className="text-sm text-muted-foreground mt-1">{error}</p>
        </div>
        <div className="flex items-center gap-3 mt-2">
          <Button onClick={loadQueue} variant="outline" size="sm" className="gap-2">
            <RotateCcw className="size-3.5" />
            <span>Try Again</span>
          </Button>
          <Link href="/dashboard">
            <Button variant="ghost" size="sm">Return to Dashboard</Button>
          </Link>
        </div>
      </Card>
    );
  }

  // 3. Empty State (Zero cards due)
  if (cards.length === 0) {
    return (
      <Card className="w-full max-w-md p-8 sm:p-10 border-border bg-card text-center flex flex-col items-center gap-4 mx-auto" data-testid="review-empty">
        <div className="size-12 rounded-full bg-muted flex items-center justify-center text-foreground">
          <CheckCircle2 className="size-6 text-foreground" />
        </div>
        <div>
          <h2 className="text-xl font-bold tracking-tight text-foreground">All Caught Up</h2>
          <p className="text-sm text-muted-foreground mt-1.5 leading-relaxed">
            You have zero flashcards due for review right now. Add new words to your library or check back later.
          </p>
        </div>
        <div className="flex items-center gap-3 mt-4">
          <Link href="/vocabulary">
            <Button size="sm" className="gap-2">
              <BookOpen className="size-3.5" />
              <span>Add Words</span>
            </Button>
          </Link>
          <Link href="/dashboard">
            <Button variant="outline" size="sm">
              Dashboard
            </Button>
          </Link>
        </div>
      </Card>
    );
  }

  // 4. Session Complete Summary
  if (isComplete) {
    const againCount = sessionResults.filter((r) => r.grade === "AGAIN").length;
    const hardCount = sessionResults.filter((r) => r.grade === "HARD").length;
    const goodCount = sessionResults.filter((r) => r.grade === "GOOD").length;
    const easyCount = sessionResults.filter((r) => r.grade === "EASY").length;

    return (
      <Card className="w-full max-w-lg p-8 sm:p-10 border-border bg-card flex flex-col items-center text-center gap-6 mx-auto" data-testid="review-complete">
        <div className="size-12 rounded-full bg-foreground text-background flex items-center justify-center">
          <CheckCircle2 className="size-6" />
        </div>

        <div>
          <span className="text-xs font-mono uppercase tracking-wider text-muted-foreground">Session Summary</span>
          <h2 className="text-2xl font-bold tracking-tight text-foreground mt-1">Review Session Completed</h2>
          <p className="text-sm text-muted-foreground mt-1">
            You reviewed {sessionResults.length} flashcard{sessionResults.length === 1 ? "" : "s"} today.
          </p>
        </div>

        {/* Real Outcome Breakdown */}
        <div className="w-full grid grid-cols-4 gap-2 pt-2 border-t border-border">
          <div className="p-3 rounded-lg border border-border bg-muted/30">
            <div className="text-xs font-mono text-muted-foreground">Again</div>
            <div className="text-lg font-bold font-mono text-foreground mt-0.5">{againCount}</div>
          </div>
          <div className="p-3 rounded-lg border border-border bg-muted/30">
            <div className="text-xs font-mono text-muted-foreground">Hard</div>
            <div className="text-lg font-bold font-mono text-foreground mt-0.5">{hardCount}</div>
          </div>
          <div className="p-3 rounded-lg border border-border bg-muted/30">
            <div className="text-xs font-mono text-muted-foreground">Good</div>
            <div className="text-lg font-bold font-mono text-foreground mt-0.5">{goodCount}</div>
          </div>
          <div className="p-3 rounded-lg border border-border bg-muted/30">
            <div className="text-xs font-mono text-muted-foreground">Easy</div>
            <div className="text-lg font-bold font-mono text-foreground mt-0.5">{easyCount}</div>
          </div>
        </div>

        <div className="flex items-center gap-3 pt-2">
          <Link href="/dashboard">
            <Button size="sm">
              Return to Workspace
            </Button>
          </Link>
          <Link href="/vocabulary">
            <Button variant="outline" size="sm">
              Vocabulary Library
            </Button>
          </Link>
        </div>
      </Card>
    );
  }

  // 5. Active Flashcard Study Card
  const progressPercent = Math.round((currentIndex / cards.length) * 100);

  return (
    <div className="w-full max-w-2xl mx-auto flex flex-col items-center gap-6" data-testid="review-container">
      {/* Session Progress Header */}
      <div className="w-full flex items-center justify-between text-xs text-muted-foreground font-mono">
        <span data-testid="card-position">
          Card {currentIndex + 1} of {cards.length}
        </span>
        <span>{progressPercent}% Complete</span>
      </div>

      <div className="w-full bg-muted rounded-full h-1 overflow-hidden" role="progressbar" aria-valuenow={progressPercent} aria-valuemin={0} aria-valuemax={100}>
        <div
          className="bg-foreground h-full transition-all duration-300 ease-out"
          style={{ width: `${progressPercent}%` }}
        />
      </div>

      {/* Network Submission Error Alert */}
      {submitError && (
        <div className="w-full p-3.5 rounded-lg border border-destructive/30 bg-destructive/5 text-destructive text-xs flex items-center justify-between gap-3" data-testid="submit-error">
          <div className="flex items-center gap-2">
            <AlertCircle className="size-4 shrink-0" />
            <span>{submitError}</span>
          </div>
          <Button
            size="sm"
            variant="outline"
            onClick={handleRetry}
            disabled={submitting}
            className="h-7 text-xs border-destructive/40 text-destructive hover:bg-destructive/10"
            data-testid="retry-submit-btn"
          >
            Retry
          </Button>
        </div>
      )}

      {/* Central Flashcard Canvas */}
      <Card
        className={`w-full p-8 sm:p-12 border-border bg-card text-center flex flex-col items-center justify-center min-h-[360px] transition-all relative ${
          isRevealed ? "shadow-sm" : ""
        }`}
        data-testid="flashcard-canvas"
      >
        {/* Term Category Badge */}
        <div className="inline-flex items-center gap-2 rounded-full border border-border bg-muted/60 px-3 py-0.5 text-[11px] font-mono text-muted-foreground mb-4">
          <span className="capitalize">{currentCard?.partOfSpeech || "vocabulary"}</span>
          {currentCard?.intervalDays !== undefined && (
            <>
              <span className="text-border">·</span>
              <span>Interval: {currentCard.intervalDays}d</span>
            </>
          )}
        </div>

        {/* English Term */}
        <div className="flex items-center justify-center gap-2">
          <h1 className="text-3xl sm:text-5xl font-bold tracking-tight text-foreground select-text" data-testid="card-term">
            {currentCard?.term}
          </h1>
          <button
            type="button"
            onClick={() => currentCard && speakTerm(currentCard.term)}
            aria-label="Listen to pronunciation"
            className="p-1.5 rounded-full hover:bg-muted text-muted-foreground hover:text-foreground transition-colors"
          >
            <Volume2 className="size-4" />
          </button>
        </div>

        {/* Phonetic IPA */}
        {currentCard?.phoneticIpa && (
          <p className="mt-2 text-sm font-mono text-muted-foreground" data-testid="card-ipa">
            {currentCard.phoneticIpa}
          </p>
        )}

        {/* Revealed Content: Meaning, Definition, Example */}
        {isRevealed ? (
          <div className="mt-6 pt-6 border-t border-border w-full flex flex-col items-center gap-4 animate-in fade-in-50 duration-200" data-testid="revealed-content">
            <div className="text-xl sm:text-2xl font-semibold text-foreground tracking-tight" data-testid="card-meaning">
              {currentCard?.vietnameseMeaning}
            </div>

            {currentCard?.englishDefinition && (
              <p className="text-sm text-foreground/80 max-w-md leading-relaxed italic" data-testid="card-definition">
                &ldquo;{currentCard.englishDefinition}&rdquo;
              </p>
            )}

            {currentCard?.exampleSentence && (
              <div className="mt-2 p-3 rounded-lg bg-muted/40 border border-border/60 text-xs sm:text-sm text-muted-foreground max-w-md text-left" data-testid="card-example">
                <span className="font-mono text-[10px] uppercase text-muted-foreground/80 block mb-1">Example</span>
                <span>{currentCard.exampleSentence}</span>
              </div>
            )}
          </div>
        ) : (
          <div className="mt-8 flex flex-col items-center gap-3">
            <Button
              size="lg"
              onClick={() => setIsRevealed(true)}
              className="gap-2 px-6"
              data-testid="reveal-btn"
            >
              <span>Reveal Answer</span>
              <kbd className="hidden sm:inline-block rounded border border-border/60 bg-muted/20 px-1.5 py-0.5 text-[10px] font-mono">
                Space
              </kbd>
            </Button>
            <span className="text-[11px] font-mono text-muted-foreground">Press Space or Enter to reveal</span>
          </div>
        )}
      </Card>

      {/* Grading Action Buttons (Displayed only after answer is revealed) */}
      {isRevealed && (
        <div className="w-full flex flex-col items-center gap-3 animate-in fade-in-50 duration-200" data-testid="rating-actions">
          <div className="w-full grid grid-cols-2 sm:grid-cols-4 gap-2.5">
            {/* 1. AGAIN */}
            <Button
              type="button"
              variant="outline"
              disabled={submitting}
              onClick={() => handleGrade("AGAIN")}
              className="h-auto py-3 px-3 flex flex-col items-center gap-1 border-border hover:border-foreground/40 hover:bg-muted/60 transition-all text-center"
              data-testid="grade-again-btn"
            >
              <div className="flex items-center gap-1.5">
                <span className="font-medium text-xs sm:text-sm text-foreground">Again</span>
                <kbd className="rounded border border-border bg-muted px-1 py-0.2 text-[10px] font-mono text-muted-foreground">
                  1
                </kbd>
              </div>
              <span className="text-[10px] font-mono text-muted-foreground">Reset (1d)</span>
            </Button>

            {/* 2. HARD */}
            <Button
              type="button"
              variant="outline"
              disabled={submitting}
              onClick={() => handleGrade("HARD")}
              className="h-auto py-3 px-3 flex flex-col items-center gap-1 border-border hover:border-foreground/40 hover:bg-muted/60 transition-all text-center"
              data-testid="grade-hard-btn"
            >
              <div className="flex items-center gap-1.5">
                <span className="font-medium text-xs sm:text-sm text-foreground">Hard</span>
                <kbd className="rounded border border-border bg-muted px-1 py-0.2 text-[10px] font-mono text-muted-foreground">
                  2
                </kbd>
              </div>
              <span className="text-[10px] font-mono text-muted-foreground">Difficult</span>
            </Button>

            {/* 3. GOOD */}
            <Button
              type="button"
              variant="outline"
              disabled={submitting}
              onClick={() => handleGrade("GOOD")}
              className="h-auto py-3 px-3 flex flex-col items-center gap-1 border-border hover:border-foreground/40 hover:bg-muted/60 transition-all text-center"
              data-testid="grade-good-btn"
            >
              <div className="flex items-center gap-1.5">
                <span className="font-medium text-xs sm:text-sm text-foreground">Good</span>
                <kbd className="rounded border border-border bg-muted px-1 py-0.2 text-[10px] font-mono text-muted-foreground">
                  3
                </kbd>
              </div>
              <span className="text-[10px] font-mono text-muted-foreground">Standard</span>
            </Button>

            {/* 4. EASY */}
            <Button
              type="button"
              variant="outline"
              disabled={submitting}
              onClick={() => handleGrade("EASY")}
              className="h-auto py-3 px-3 flex flex-col items-center gap-1 border-border hover:border-foreground/40 hover:bg-muted/60 transition-all text-center"
              data-testid="grade-easy-btn"
            >
              <div className="flex items-center gap-1.5">
                <span className="font-medium text-xs sm:text-sm text-foreground">Easy</span>
                <kbd className="rounded border border-border bg-muted px-1 py-0.2 text-[10px] font-mono text-muted-foreground">
                  4
                </kbd>
              </div>
              <span className="text-[10px] font-mono text-muted-foreground">Rapid</span>
            </Button>
          </div>

          <p className="text-[11px] font-mono text-muted-foreground text-center">
            Shortcut keys 1, 2, 3, 4 trigger instant grading.
          </p>
        </div>
      )}

      {/* Return Action */}
      <div className="flex items-center gap-3 pt-2">
        <Link href="/dashboard">
          <Button variant="ghost" size="sm" className="gap-2 text-xs text-muted-foreground hover:text-foreground">
            <ArrowLeft className="size-3.5" />
            <span>Exit Focus Review</span>
          </Button>
        </Link>
      </div>
    </div>
  );
}
