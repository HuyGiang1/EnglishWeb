"use client";

import { useLocale } from "@/lib/i18n/locale-provider";
import type { TranslationKey } from "@/lib/i18n/dictionaries";
import { learningErrorKey } from "@/lib/i18n/learning-errors";

import { Suspense, useEffect, useState, useCallback, useRef } from "react";
import { useSearchParams } from "next/navigation";
import { ApiError } from "@/lib/api/client";
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
import { reviewApi, type ReviewCard, type ReviewRating, type ReviewSubmissionResponse, type ReviewSubmissionPayload } from "@/lib/api/review";

interface SessionResultItem {
  term: string;
  grade: ReviewRating;
  newIntervalDays: number;
}

export default function ReviewFocusPage() {
  return <Suspense fallback={null}><ReviewRoute /></Suspense>;
}

function ReviewRoute() {
  const params = useSearchParams();
  const { t } = useLocale();
  const collectionId = params.has("collectionId") ? params.getAll("collectionId").join(",") : undefined;
  const scoped = collectionId !== undefined;
  return (
    <section className="w-full space-y-4">
      <nav aria-label={t("learnReviewContext")} className="flex flex-wrap items-center justify-between gap-3 text-sm">
        <span className="font-medium" data-testid="review-scope">{t(scoped ? "learnReviewStudySet" : "learnReviewAll")}</span>
        {scoped && <Link className="underline underline-offset-4" href={`/collections/${encodeURIComponent(collectionId)}`}>{t("learnBackToStudySet")}</Link>}
      </nav>
      <ReviewSession key={collectionId ?? "global"} collectionId={collectionId} />
    </section>
  );
}

function ReviewSession({ collectionId }: { collectionId?: string }) {
  const { t } = useLocale();
  const scoped = collectionId !== undefined;
  const [totalDue, setTotalDue] = useState(0);
  const pendingSubmission = useRef<ReviewSubmissionPayload | null>(null);
  const submissionInFlight = useRef(false);
  const queueGeneration = useRef(0);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<TranslationKey | null>(null);
  const [cards, setCards] = useState<ReviewCard[]>([]);
  const [currentIndex, setCurrentIndex] = useState<number>(0);
  const [isRevealed, setIsRevealed] = useState<boolean>(false);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [submitError, setSubmitError] = useState<TranslationKey | null>(null);

  // Each card attempt has a stable attempt_id that is preserved on network retries
  const [currentAttemptId, setCurrentAttemptId] = useState<string>("");
  const [lastGradeAttempted, setLastGradeAttempted] = useState<ReviewRating | null>(null);

  // Real review session summary items
  const [sessionResults, setSessionResults] = useState<SessionResultItem[]>([]);
  const [startTime, setStartTime] = useState<number>(Date.now());

  // Load review queue on mount
  const loadQueue = useCallback(async () => {
    const generation = ++queueGeneration.current;
    setLoading(true);
    setError(null);
    setSubmitError(null);
    try {
      const res = await reviewApi.getQueue({ collectionId, limit: 50 });
      if (generation !== queueGeneration.current) return;
      setTotalDue(res.totalDue);
      pendingSubmission.current = null;
      setCards(res.items || []);
      setCurrentIndex(0);
      setIsRevealed(false);
      setCurrentAttemptId(crypto.randomUUID());
      setLastGradeAttempted(null);
      setSessionResults([]);
      setStartTime(Date.now());
    } catch (err: unknown) {
      if (generation !== queueGeneration.current) return;
      const msg = scoped && err instanceof ApiError && err.code === "NOT_FOUND"
        ? "learnStudySetUnavailable"
        : learningErrorKey(err, "learnFailedToLoadReviewQueue2");
      setError(msg);
    } finally {
      if (generation === queueGeneration.current) setLoading(false);
    }
  }, [collectionId, scoped]);

  const invalidateQueue = useCallback(() => { queueGeneration.current++; }, []);
  useEffect(() => {
    loadQueue();
    return invalidateQueue;
  }, [loadQueue, invalidateQueue]);

  const currentCard: ReviewCard | undefined = cards[currentIndex];
  const isComplete = !loading && !error && cards.length > 0 && currentIndex >= cards.length;

  // Grade submission handler
  const handleGrade = useCallback(
    async (grade: ReviewRating) => {
      if (!currentCard || submissionInFlight.current) return;
      submissionInFlight.current = true;

      setSubmitting(true);
      setSubmitError(null);
      const payload = pendingSubmission.current ?? {
        srsItemId: currentCard.srsItemId,
        attemptId: currentAttemptId,
        grade,
        durationSeconds: Math.max(1, Math.round((Date.now() - startTime) / 1000)),
      };
      pendingSubmission.current = payload;
      setLastGradeAttempted(payload.grade);

      try {
        const response: ReviewSubmissionResponse = await reviewApi.submitGrade(payload);

        // Record real result in session summary
        setSessionResults((prev) => [
          ...prev,
          {
            term: currentCard.term,
            grade: payload.grade,
            newIntervalDays: response.newIntervalDays,
          },
        ]);

        // Advance only after an acknowledged response; retries keep every payload field.
        pendingSubmission.current = null;
        setCurrentIndex((prev) => prev + 1);
        setIsRevealed(false);
        setCurrentAttemptId(crypto.randomUUID());
        setLastGradeAttempted(null);
        setStartTime(Date.now());
      } catch (err: unknown) {
        const msg = learningErrorKey(err, "learnGradeSubmissionFailedPleaseRetry");
        setSubmitError(msg);
      } finally {
        submissionInFlight.current = false;
        setSubmitting(false);
      }
    },
    [currentCard, currentAttemptId, startTime]
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
      // Preserve native form-control and link keyboard behavior
      const target = e.target as HTMLElement | null;
      if (
        target &&
        (target.tagName === "INPUT" ||
          target.tagName === "TEXTAREA" || target.tagName === "SELECT" || target.tagName === "BUTTON" || target.tagName === "A" ||
          target.isContentEditable)
      ) {
        return;
      }

      if (loading || error || !currentCard || submitting || submitError) {
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
  }, [loading, error, currentCard, isRevealed, submitting, submitError, handleGrade]);

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
      <div className="w-full flex flex-col items-center justify-center min-h-[380px] gap-4" data-testid="review-loading" role="status">
        <div className="size-8 rounded-full border-2 border-border border-t-foreground animate-spin" />
        <p className="text-sm text-muted-foreground font-mono">{t("learnLoadingReviewQueue")}</p>
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
          <h1 className="text-lg font-semibold text-foreground">{t("learnFailedToLoadReviewQueue")}</h1>
          <p className="text-sm text-muted-foreground mt-1">{t(error)}</p>
        </div>
        <div className="flex items-center gap-3 mt-2">
          <Button onClick={loadQueue} variant="outline" size="sm" className="gap-2">
            <RotateCcw className="size-3.5" />
            <span>{t("learnTryAgain")}</span>
          </Button>
          <Link href="/dashboard">
            <Button variant="ghost" size="sm">{t("learnReturnToDashboard")}</Button>
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
          <h1 className="text-xl font-bold tracking-tight text-foreground">{t("learnAllCaughtUp")}</h1>
          <p className="text-sm text-muted-foreground mt-1.5 leading-relaxed">{t(scoped ? "learnNoStudySetVocabularyDue" : "learnYouHaveZeroFlashcardsDueForReviewRightNowAddNewWordsToYourLibraryOrCheckBackLater")}</p>
        </div>
        <div className="flex items-center gap-3 mt-4">
          <Link href="/vocabulary">
            <Button size="sm" className="gap-2">
              <BookOpen className="size-3.5" />
              <span>{t("learnAddWords")}</span>
            </Button>
          </Link>
          <Link href="/dashboard">
            <Button variant="outline" size="sm">{t("learnDashboard")}</Button>
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
          <span className="text-xs font-mono uppercase tracking-wider text-muted-foreground">{t("learnSessionSummary")}</span>
          <h1 className="text-2xl font-bold tracking-tight text-foreground mt-1">{t("learnReviewSessionCompleted")}</h1>
          <p className="text-sm text-muted-foreground mt-1">{t("learnReviewSummary", { count: sessionResults.length })}</p>
        </div>

        {/* Real Outcome Breakdown */}
        <div className="w-full grid grid-cols-2 sm:grid-cols-4 gap-2 pt-2 border-t border-border">
          <div className="p-3 rounded-lg border border-border bg-muted/30">
            <div className="text-xs font-mono text-muted-foreground">{t("learnAgain")}</div>
            <div className="text-lg font-bold font-mono text-foreground mt-0.5">{againCount}</div>
          </div>
          <div className="p-3 rounded-lg border border-border bg-muted/30">
            <div className="text-xs font-mono text-muted-foreground">{t("learnHard")}</div>
            <div className="text-lg font-bold font-mono text-foreground mt-0.5">{hardCount}</div>
          </div>
          <div className="p-3 rounded-lg border border-border bg-muted/30">
            <div className="text-xs font-mono text-muted-foreground">{t("learnGood")}</div>
            <div className="text-lg font-bold font-mono text-foreground mt-0.5">{goodCount}</div>
          </div>
          <div className="p-3 rounded-lg border border-border bg-muted/30">
            <div className="text-xs font-mono text-muted-foreground">{t("learnEasy")}</div>
            <div className="text-lg font-bold font-mono text-foreground mt-0.5">{easyCount}</div>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3 pt-2">
          <Button variant="outline" size="sm" onClick={loadQueue}>{t("learnCheckDueQueue")}</Button>
          <Link href="/dashboard">
            <Button size="sm">{t("learnReturnToWorkspace")}</Button>
          </Link>
          <Link href="/vocabulary">
            <Button variant="outline" size="sm">{t("learnVocabularyLibrary")}</Button>
          </Link>
        </div>
      </Card>
    );
  }

  // 5. Active Flashcard Study Card
  const progressPercent = Math.round((currentIndex / cards.length) * 100);

  return (
    <div className="w-full max-w-2xl mx-auto flex flex-col items-center gap-6" data-testid="review-container">
      <p className="text-sm text-muted-foreground" data-testid="review-due-count">{t("learnDueAtQueueLoad", { count: totalDue })}</p>
      {/* Session Progress Header */}
      <div className="w-full flex items-center justify-between text-xs text-muted-foreground font-mono">
        <span data-testid="card-position" role="status" aria-live="polite">{t("learnCard")}{" "}{currentIndex + 1}{" "}{t("learnOf")}{" "}{cards.length}
        </span>
        <span>{progressPercent}{t("learnComplete")}</span>
      </div>

      <div className="w-full bg-muted rounded-full h-1 overflow-hidden" role="progressbar" aria-label={t("learnLearningProgress")} aria-valuenow={progressPercent} aria-valuemin={0} aria-valuemax={100}>
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
            <span>{t(submitError)}</span>
          </div>
          <Button
            size="sm"
            variant="outline"
            onClick={handleRetry}
            disabled={submitting}
            className="h-7 text-xs border-destructive/40 text-destructive hover:bg-destructive/10"
            data-testid="retry-submit-btn"
          >{t("learnRetry")}</Button>
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
          <span className="capitalize">{currentCard?.partOfSpeech || t("navVocabulary")}</span>
          {currentCard?.intervalDays !== undefined && (
            <>
              <span className="text-border">·</span>
              <span>{t("learnInterval")}{" "}{currentCard.intervalDays}{t("learnD")}</span>
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
            aria-label={t("learnListenToPronunciation")}
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
                <span className="font-mono text-[10px] uppercase text-muted-foreground/80 block mb-1">{t("learnExample")}</span>
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
              <span>{t("learnRevealAnswer")}</span>
              <kbd className="hidden sm:inline-block rounded border border-border/60 bg-muted/20 px-1.5 py-0.5 text-[10px] font-mono">{t("learnSpace")}</kbd>
            </Button>
            <span className="text-[11px] font-mono text-muted-foreground">{t("learnPressSpaceOrEnterToReveal")}</span>
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
              disabled={submitting || !!submitError}
              onClick={() => handleGrade("AGAIN")}
              className="h-auto py-3 px-3 flex flex-col items-center gap-1 border-border hover:border-foreground/40 hover:bg-muted/60 transition-all text-center"
              data-testid="grade-again-btn"
            >
              <div className="flex items-center gap-1.5">
                <span className="font-medium text-xs sm:text-sm text-foreground">{t("learnAgain")}</span>
                <kbd className="rounded border border-border bg-muted px-1 py-0.2 text-[10px] font-mono text-muted-foreground">
                  1
                </kbd>
              </div>
              <span className="text-[10px] font-mono text-muted-foreground">{t("learnReset1d")}</span>
            </Button>

            {/* 2. HARD */}
            <Button
              type="button"
              variant="outline"
              disabled={submitting || !!submitError}
              onClick={() => handleGrade("HARD")}
              className="h-auto py-3 px-3 flex flex-col items-center gap-1 border-border hover:border-foreground/40 hover:bg-muted/60 transition-all text-center"
              data-testid="grade-hard-btn"
            >
              <div className="flex items-center gap-1.5">
                <span className="font-medium text-xs sm:text-sm text-foreground">{t("learnHard")}</span>
                <kbd className="rounded border border-border bg-muted px-1 py-0.2 text-[10px] font-mono text-muted-foreground">
                  2
                </kbd>
              </div>
              <span className="text-[10px] font-mono text-muted-foreground">{t("learnDifficult")}</span>
            </Button>

            {/* 3. GOOD */}
            <Button
              type="button"
              variant="outline"
              disabled={submitting || !!submitError}
              onClick={() => handleGrade("GOOD")}
              className="h-auto py-3 px-3 flex flex-col items-center gap-1 border-border hover:border-foreground/40 hover:bg-muted/60 transition-all text-center"
              data-testid="grade-good-btn"
            >
              <div className="flex items-center gap-1.5">
                <span className="font-medium text-xs sm:text-sm text-foreground">{t("learnGood")}</span>
                <kbd className="rounded border border-border bg-muted px-1 py-0.2 text-[10px] font-mono text-muted-foreground">
                  3
                </kbd>
              </div>
              <span className="text-[10px] font-mono text-muted-foreground">{t("learnStandard")}</span>
            </Button>

            {/* 4. EASY */}
            <Button
              type="button"
              variant="outline"
              disabled={submitting || !!submitError}
              onClick={() => handleGrade("EASY")}
              className="h-auto py-3 px-3 flex flex-col items-center gap-1 border-border hover:border-foreground/40 hover:bg-muted/60 transition-all text-center"
              data-testid="grade-easy-btn"
            >
              <div className="flex items-center gap-1.5">
                <span className="font-medium text-xs sm:text-sm text-foreground">{t("learnEasy")}</span>
                <kbd className="rounded border border-border bg-muted px-1 py-0.2 text-[10px] font-mono text-muted-foreground">
                  4
                </kbd>
              </div>
              <span className="text-[10px] font-mono text-muted-foreground">{t("learnRapid")}</span>
            </Button>
          </div>

          <p className="text-[11px] font-mono text-muted-foreground text-center">{t("learnShortcutKeys1234TriggerInstantGrading")}</p>
        </div>
      )}

      {/* Return Action */}
      <div className="flex items-center gap-3 pt-2">
        <Link href="/dashboard">
          <Button variant="ghost" size="sm" className="gap-2 text-xs text-muted-foreground hover:text-foreground">
            <ArrowLeft className="size-3.5" />
            <span>{t("learnExitFocusReview")}</span>
          </Button>
        </Link>
      </div>
    </div>
  );
}
