"use client";

import { useEffect, useState, useRef, useCallback } from "react";
import Link from "next/link";
import {
  ArrowLeft,
  ArrowRight,
  Play,
  Pause,
  RotateCcw,
  Headphones,
  CheckCircle2,
  AlertCircle,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  dictationApi,
  type DictationExerciseSummary,
  type DictationExerciseDetail,
  type DictationSubmitResponse,
  type DiffToken,
} from "@/lib/api/dictation";
import { settingsApi } from "@/lib/api/settings";

interface CompletedRecord {
  title: string;
  cefrLevel: string;
  accuracyScore: number;
  isPassed: boolean;
}

export default function DictationPage() {
  const [exercises, setExercises] = useState<DictationExerciseSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Active Exercise State
  const [activeExercise, setActiveExercise] = useState<DictationExerciseDetail | null>(null);
  const [activeExerciseIndex, setActiveExerciseIndex] = useState<number>(0);

  // Audio Playback State
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [playbackRate, setPlaybackRate] = useState<number>(1.0);
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [duration, setDuration] = useState<number>(0);
  const [audioError, setAudioError] = useState<string | null>(null);

  // Input & Evaluation State
  const [transcript, setTranscript] = useState<string>("");
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [evalResult, setEvalResult] = useState<DictationSubmitResponse | null>(null);
  const [currentAttemptId, setCurrentAttemptId] = useState<string>("");
  const [startTime, setStartTime] = useState<number>(Date.now());

  // Session Progress
  const [sessionRecords, setSessionRecords] = useState<CompletedRecord[]>([]);
  const [isSessionCompleted, setIsSessionCompleted] = useState<boolean>(false);

  // Load available exercises on mount
  const loadExercises = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await dictationApi.listExercises();
      setExercises(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to load dictation exercises";
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadExercises();
    settingsApi.getSettings().then((s) => {
      if (s?.preferredAudioSpeed) {
        setPlaybackRate(s.preferredAudioSpeed);
      }
    }).catch(() => {
      // Graceful fallback to default 1.0
    });
  }, [loadExercises]);

  // Start a specific exercise
  const selectExercise = useCallback(async (contentId: string, index: number) => {
    setAudioError(null);
    setEvalResult(null);
    setTranscript("");
    setCurrentAttemptId(crypto.randomUUID());
    setStartTime(Date.now());
    setIsPlaying(false);

    try {
      const detail = await dictationApi.getExercise(contentId);
      setActiveExercise(detail);
      setActiveExerciseIndex(index);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to load exercise details";
      setAudioError(msg);
    }
  }, []);

  // Audio Play / Pause toggle
  const togglePlay = useCallback(() => {
    if (!audioRef.current) return;
    if (isPlaying) {
      audioRef.current.pause();
      setIsPlaying(false);
    } else {
      audioRef.current.play().then(() => {
        setIsPlaying(true);
      }).catch(() => {
        setAudioError("Unable to play audio. Click replay to retry.");
        setIsPlaying(false);
      });
    }
  }, [isPlaying]);

  // Replay audio from start
  const replayAudio = useCallback(() => {
    if (!audioRef.current) return;
    audioRef.current.currentTime = 0;
    audioRef.current.play().then(() => {
      setIsPlaying(true);
    }).catch(() => {
      setIsPlaying(false);
    });
  }, []);

  // Change playback speed (0.75x, 1.0x, 1.25x)
  const changeSpeed = useCallback((rate: number) => {
    setPlaybackRate(rate);
    if (audioRef.current) {
      audioRef.current.playbackRate = rate;
    }
  }, []);

  // Global & Keyboard collision handling
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const isMod = e.metaKey || e.ctrlKey;

      // Mod+Space: Play/Pause from anywhere (including textarea)
      if (isMod && e.code === "Space") {
        e.preventDefault();
        togglePlay();
        return;
      }

      // Mod+R: Replay audio from start
      if (isMod && (e.key === "r" || e.key === "R")) {
        e.preventDefault();
        replayAudio();
        return;
      }

      // Speed shortcuts: [ slows down, ] speeds up
      if (e.key === "[") {
        e.preventDefault();
        changeSpeed(0.75);
      } else if (e.key === "]") {
        e.preventDefault();
        changeSpeed(1.25);
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [togglePlay, replayAudio, changeSpeed]);

  // Submit transcription
  const handleSubmit = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!activeExercise || submitting) return;

    setSubmitting(true);
    const durationSeconds = Math.max(1, Math.round((Date.now() - startTime) / 1000));

    try {
      const res = await dictationApi.submitTranscription(activeExercise.contentId, {
        attemptId: currentAttemptId,
        submittedTranscript: transcript,
        durationSeconds,
      });

      setEvalResult(res);

      // Record in current session results
      setSessionRecords((prev) => [
        ...prev,
        {
          title: activeExercise.title,
          cefrLevel: activeExercise.cefrLevel,
          accuracyScore: res.accuracyScore,
          isPassed: res.isPassed,
        },
      ]);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to evaluate transcription";
      alert(msg);
    } finally {
      setSubmitting(false);
    }
  };

  // Next exercise progression
  const handleNextExercise = () => {
    const nextIndex = activeExerciseIndex + 1;
    if (nextIndex < exercises.length) {
      selectExercise(exercises[nextIndex].contentId, nextIndex);
    } else {
      setIsSessionCompleted(true);
    }
  };

  // Retry same sentence with fresh attempt ID
  const handleTryAgain = () => {
    setEvalResult(null);
    setTranscript("");
    setCurrentAttemptId(crypto.randomUUID());
    setStartTime(Date.now());
    replayAudio();
  };

  // Return to exercise list
  const handleExitToDirectory = () => {
    setActiveExercise(null);
    setEvalResult(null);
    loadExercises();
  };

  // ---------------------------------------------------------------------------
  // View 1: Session Completed Summary
  // ---------------------------------------------------------------------------
  if (isSessionCompleted) {
    const totalCount = sessionRecords.length;
    const avgAccuracy = totalCount > 0
      ? Math.round(sessionRecords.reduce((acc, r) => acc + r.accuracyScore, 0) / totalCount)
      : 0;
    const passedCount = sessionRecords.filter((r) => r.isPassed).length;

    return (
      <div className="w-full max-w-xl mx-auto space-y-6" data-testid="dictation-completed-view">
        <Card className="border-border bg-card p-8 text-center space-y-6">
          <div className="inline-flex size-14 items-center justify-center rounded-full bg-foreground text-background mx-auto">
            <CheckCircle2 className="size-7" />
          </div>

          <div className="space-y-2">
            <h1 className="text-2xl font-bold tracking-tight text-foreground">
              Dictation Session Complete
            </h1>
            <p className="text-xs text-muted-foreground font-mono">
              Verified listening actions recorded to your daily learning streak
            </p>
          </div>

          {/* KPI Summary */}
          <div className="grid grid-cols-3 gap-3 border-y border-border py-4">
            <div>
              <div className="text-xs text-muted-foreground font-mono">Completed</div>
              <div className="text-xl font-bold font-mono text-foreground mt-0.5" data-testid="completed-count">
                {totalCount}
              </div>
            </div>
            <div>
              <div className="text-xs text-muted-foreground font-mono">Avg Accuracy</div>
              <div className="text-xl font-bold font-mono text-foreground mt-0.5" data-testid="avg-accuracy">
                {avgAccuracy}%
              </div>
            </div>
            <div>
              <div className="text-xs text-muted-foreground font-mono">Passed (≥80%)</div>
              <div className="text-xl font-bold font-mono text-foreground mt-0.5">
                {passedCount}/{totalCount}
              </div>
            </div>
          </div>

          {/* List of items */}
          <div className="space-y-2 text-left max-h-48 overflow-y-auto divide-y divide-border text-xs">
            {sessionRecords.map((rec, i) => (
              <div key={i} className="pt-2 flex items-center justify-between">
                <div className="flex items-center gap-2 truncate">
                  <Badge variant="outline" className="text-[10px] py-0 px-1.5 font-mono">
                    {rec.cefrLevel}
                  </Badge>
                  <span className="truncate font-medium text-foreground">{rec.title}</span>
                </div>
                <div className="flex items-center gap-2 font-mono">
                  <span className={rec.isPassed ? "text-foreground font-semibold" : "text-muted-foreground"}>
                    {rec.accuracyScore}%
                  </span>
                  {rec.isPassed ? (
                    <CheckCircle2 className="size-3.5 text-foreground" />
                  ) : (
                    <AlertCircle className="size-3.5 text-muted-foreground" />
                  )}
                </div>
              </div>
            ))}
          </div>

          <div className="flex flex-col sm:flex-row gap-3 pt-2">
            <Button
              variant="outline"
              className="flex-1 text-xs"
              onClick={() => {
                setIsSessionCompleted(false);
                setSessionRecords([]);
                handleExitToDirectory();
              }}
              data-testid="return-directory-btn"
            >
              Choose More Exercises
            </Button>
            <Link href="/dashboard" className="flex-1">
              <Button className="w-full text-xs gap-1.5" data-testid="exit-dashboard-btn">
                <span>View Dashboard</span>
                <ArrowRight className="size-3.5" />
              </Button>
            </Link>
          </div>
        </Card>
      </div>
    );
  }

  // ---------------------------------------------------------------------------
  // View 2: Exercise Directory (Picker)
  // ---------------------------------------------------------------------------
  if (!activeExercise) {
    return (
      <div className="w-full max-w-2xl mx-auto space-y-6" data-testid="dictation-directory-view">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border pb-4">
          <div>
            <div className="flex items-center gap-2">
              <Headphones className="size-5 text-foreground" />
              <h1 className="text-xl font-bold tracking-tight text-foreground">
                Daily Dictation
              </h1>
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              Verbatim audio transcription · Real-time difference analysis · Pure focus
            </p>
          </div>
          <Link href="/dashboard">
            <Button variant="outline" size="sm" className="text-xs gap-1.5 h-8">
              <ArrowLeft className="size-3.5" />
              <span>Exit to Dashboard</span>
            </Button>
          </Link>
        </div>

        {/* Loading state */}
        {loading && (
          <div className="py-12 text-center text-xs text-muted-foreground font-mono space-y-2">
            <div className="animate-spin size-5 border-2 border-foreground border-t-transparent rounded-full mx-auto" />
            <p>Loading available listening exercises...</p>
          </div>
        )}

        {/* Error state */}
        {error && (
          <div className="p-4 border border-border bg-muted/20 rounded-md text-xs text-destructive flex items-center justify-between">
            <span>{error}</span>
            <Button variant="outline" size="sm" onClick={loadExercises} className="text-xs h-7">
              Retry
            </Button>
          </div>
        )}

        {/* Exercises list */}
        {!loading && !error && (
          <div className="space-y-3" data-testid="exercise-list">
            <div className="text-xs font-mono text-muted-foreground flex items-center justify-between px-1">
              <span>{exercises.length} Exercises Available</span>
              <span>Select to begin practice</span>
            </div>

            {exercises.map((ex, idx) => (
              <Card
                key={ex.contentId}
                className={`border-border bg-card p-4 transition-colors flex items-center justify-between gap-4 ${
                  ex.isAudioAvailable === false ? "opacity-60 cursor-not-allowed" : "hover:border-foreground/50 cursor-pointer"
                }`}
                onClick={() => {
                  if (ex.isAudioAvailable !== false) {
                    selectExercise(ex.contentId, idx);
                  }
                }}
                data-testid={`exercise-card-${idx}`}
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <Badge variant="outline" className="text-[10px] font-mono py-0 px-1.5 font-bold">
                      {ex.cefrLevel}
                    </Badge>
                    <h3 className="text-sm font-semibold text-foreground">{ex.title}</h3>
                  </div>
                  <div className="flex items-center gap-3 text-xs text-muted-foreground font-mono">
                    <span>{ex.topicTag}</span>
                    <span>·</span>
                    <span>{ex.audioDurationSeconds ? `${ex.audioDurationSeconds}s` : "Audio"}</span>
                  </div>
                </div>

                <div className="flex items-center gap-3">
                  {ex.isCompleted ? (
                    <div className="text-right">
                      <div className="inline-flex items-center gap-1 text-[11px] font-mono text-foreground font-semibold">
                        <CheckCircle2 className="size-3" />
                        <span>{ex.lastAccuracyScore}%</span>
                      </div>
                      <div className="text-[10px] font-mono text-muted-foreground">Completed</div>
                    </div>
                  ) : (
                    <Badge variant="secondary" className="text-[10px] font-mono py-0.5 px-2">
                      New
                    </Badge>
                  )}
                  {ex.isAudioAvailable === false ? (
                    <Badge variant="outline" className="text-[10px] font-mono text-muted-foreground border-dashed">
                      Audio Missing
                    </Badge>
                  ) : (
                    <Button size="sm" variant="outline" className="h-8 text-xs gap-1">
                      <span>Start</span>
                      <ArrowRight className="size-3" />
                    </Button>
                  )}
                </div>
              </Card>
            ))}
          </div>
        )}
      </div>
    );
  }

  // ---------------------------------------------------------------------------
  // View 3: Active Dictation Practice Session
  // ---------------------------------------------------------------------------
  return (
    <div className="w-full max-w-2xl mx-auto space-y-6" data-testid="dictation-focus-view">
      {/* Hidden genuine HTML5 audio player */}
      <audio
        ref={audioRef}
        src={activeExercise.audioUrl}
        preload="auto"
        onTimeUpdate={() => {
          if (audioRef.current) {
            setCurrentTime(audioRef.current.currentTime);
          }
        }}
        onLoadedMetadata={() => {
          if (audioRef.current) {
            setDuration(audioRef.current.duration);
          }
        }}
        onEnded={() => setIsPlaying(false)}
        onError={() => setAudioError("Audio playback encountered an error.")}
      />

      {/* Session Top Bar */}
      <div className="flex items-center justify-between border-b border-border pb-3">
        <Button
          variant="ghost"
          size="sm"
          className="text-xs gap-1 h-8 px-2 text-muted-foreground hover:text-foreground"
          onClick={handleExitToDirectory}
          data-testid="exit-to-directory-btn"
        >
          <ArrowLeft className="size-3.5" />
          <span>All Exercises</span>
        </Button>

        <div className="flex items-center gap-2">
          <Badge variant="outline" className="text-[10px] font-mono font-bold">
            {activeExercise.cefrLevel}
          </Badge>
          <span className="text-xs font-mono text-muted-foreground">
            {activeExerciseIndex + 1} of {exercises.length}
          </span>
        </div>

        {/* Speed Selector Chips */}
        <div className="flex items-center gap-1 bg-muted/40 p-0.5 rounded border border-border" data-testid="speed-selector">
          {[0.75, 1.0, 1.25].map((rate) => (
            <button
              key={rate}
              type="button"
              onClick={() => changeSpeed(rate)}
              className={`text-[11px] font-mono px-2 py-0.5 rounded transition-colors ${
                playbackRate === rate
                  ? "bg-foreground text-background font-semibold"
                  : "text-muted-foreground hover:text-foreground"
              }`}
              data-testid={`speed-${rate}x`}
            >
              {rate}x
            </button>
          ))}
        </div>
      </div>

      {/* Exercise Title Card */}
      <div>
        <h2 className="text-lg font-bold text-foreground" data-testid="exercise-title">
          {activeExercise.title}
        </h2>
        <p className="text-xs text-muted-foreground font-mono">
          Topic: {activeExercise.topicTag} · Listen carefully and transcribe verbatim
        </p>
      </div>

      {/* Audio Player Card */}
      <Card className="border-border bg-card p-6 space-y-4 text-center" data-testid="audio-player-card">
        {audioError ? (
          <div className="text-xs text-destructive py-2">
            <span>{audioError}</span>
          </div>
        ) : null}

        {activeExercise.isAudioAvailable === false ? (
          <div className="text-xs text-muted-foreground bg-muted/40 p-3 rounded border border-border" data-testid="audio-missing-banner">
            Audio recording is not installed on this instance. Run <code className="text-foreground">scripts/setup-dev-audio.sh</code> to provision local audio.
          </div>
        ) : null}

        <div className="flex items-center justify-center gap-4">
          <Button
            type="button"
            variant="outline"
            size="icon"
            className="size-9 rounded-full"
            onClick={replayAudio}
            title="Replay from start (Mod+R)"
            data-testid="replay-audio-btn"
          >
            <RotateCcw className="size-4" />
          </Button>

          <Button
            type="button"
            className="size-14 rounded-full shadow-none"
            onClick={togglePlay}
            data-testid="play-pause-btn"
            title="Play / Pause (Mod+Space)"
          >
            {isPlaying ? <Pause className="size-6" /> : <Play className="size-6 ml-0.5" />}
          </Button>
        </div>

        {/* Waveform / Scrubber Progress Indicator */}
        <div className="space-y-1.5 max-w-md mx-auto">
          <div
            className="h-2 bg-muted rounded-full overflow-hidden cursor-pointer relative"
            onClick={(e) => {
              if (!audioRef.current || !duration) return;
              const rect = e.currentTarget.getBoundingClientRect();
              const pos = (e.clientX - rect.left) / rect.width;
              audioRef.current.currentTime = pos * duration;
            }}
            data-testid="audio-scrubber"
          >
            <div
              className="h-full bg-foreground transition-all duration-75"
              style={{ width: `${duration ? (currentTime / duration) * 100 : 0}%` }}
            />
          </div>
          <div className="flex justify-between text-[10px] font-mono text-muted-foreground">
            <span>{currentTime.toFixed(1)}s</span>
            <span>{duration ? `${duration.toFixed(1)}s` : `${activeExercise.audioDurationSeconds || 0}s`}</span>
          </div>
        </div>

        <div className="text-[11px] font-mono text-muted-foreground">
          Mod+Space: Play/Pause · Mod+R: Replay · [: 0.75x · ]: 1.25x
        </div>
      </Card>

      {/* Transcription Input Card (Before Submission) */}
      {!evalResult ? (
        <form onSubmit={handleSubmit} className="space-y-4" data-testid="transcription-form">
          <div className="space-y-1.5">
            <label htmlFor="transcript-input" className="text-xs font-semibold text-foreground">
              Your Transcription
            </label>
            <textarea
              id="transcript-input"
              rows={4}
              value={transcript}
              onChange={(e) => setTranscript(e.target.value)}
              onKeyDown={(e) => {
                if ((e.metaKey || e.ctrlKey) && e.key === "Enter") {
                  e.preventDefault();
                  handleSubmit();
                }
              }}
              placeholder="Type the sentence you hear... (Mod+Enter to submit)"
              className="w-full rounded-md border border-border bg-background p-3 text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-foreground transition-shadow placeholder:text-muted-foreground"
              autoFocus
              data-testid="transcript-input"
            />
          </div>

          <Button
            type="submit"
            disabled={submitting || transcript.trim().length === 0}
            className="w-full text-xs font-semibold h-10 gap-2"
            data-testid="submit-transcript-btn"
          >
            {submitting ? (
              <>
                <div className="animate-spin size-3.5 border-2 border-background border-t-transparent rounded-full" />
                <span>Evaluating Transcription...</span>
              </>
            ) : (
              <>
                <span>Check Answer (Mod+Enter)</span>
                <ArrowRight className="size-3.5" />
              </>
            )}
          </Button>
        </form>
      ) : (
        /* Evaluation & Diff Results Card (After Submission) */
        <Card className="border-border bg-card p-6 space-y-6" data-testid="evaluation-results-card">
          <div className="flex items-center justify-between border-b border-border pb-4">
            <div>
              <div className="flex items-center gap-2">
                <span className="text-2xl font-bold font-mono text-foreground" data-testid="accuracy-score">
                  {evalResult.accuracyScore}%
                </span>
                <Badge
                  variant={evalResult.isPassed ? "default" : "secondary"}
                  className="text-xs font-mono"
                  data-testid="pass-fail-badge"
                >
                  {evalResult.isPassed ? "Pass (≥80%)" : "Needs Work (<80%)"}
                </Badge>
              </div>
              <p className="text-[11px] font-mono text-muted-foreground mt-0.5">
                {evalResult.wordCount} words · {evalResult.errorCount} error{evalResult.errorCount === 1 ? "" : "s"}
              </p>
            </div>

            <Button
              variant="outline"
              size="sm"
              onClick={handleTryAgain}
              className="text-xs gap-1.5 h-8"
              data-testid="try-again-btn"
            >
              <RotateCcw className="size-3.5" />
              <span>Try Again</span>
            </Button>
          </div>

          {/* Word-Level Diff View */}
          <div className="space-y-2">
            <div className="flex items-center justify-between text-[11px] font-mono text-muted-foreground">
              <span>Word Comparison</span>
              <span>Black: Correct · Strikethrough: Missed · Underline: Extra</span>
            </div>

            <div
              className="p-4 rounded-md border border-border bg-muted/20 text-sm leading-relaxed font-sans"
              data-testid="diff-tokens-viewer"
            >
              {evalResult.diffTokens.map((tok: DiffToken, idx: number) => {
                if (tok.type === "EQUAL") {
                  return (
                    <span key={idx} className="text-foreground font-medium mr-1.5" data-testid="diff-equal">
                      {tok.text}
                    </span>
                  );
                }
                if (tok.type === "MISSING") {
                  return (
                    <span
                      key={idx}
                      className="text-muted-foreground line-through bg-muted/60 px-1 py-0.5 rounded font-mono mr-1.5"
                      title={`Omitted word: ${tok.expectedWord}`}
                      data-testid="diff-missing"
                    >
                      {tok.expectedWord}
                    </span>
                  );
                }
                if (tok.type === "EXTRA") {
                  return (
                    <span
                      key={idx}
                      className="text-foreground underline bg-muted/30 px-1 py-0.5 rounded mr-1.5"
                      title="Extra inserted word"
                      data-testid="diff-extra"
                    >
                      {tok.text}
                    </span>
                  );
                }
                if (tok.type === "SUBSTITUTION") {
                  return (
                    <span
                      key={idx}
                      className="bg-muted px-1.5 py-0.5 rounded mr-1.5 inline-flex items-center gap-1 font-mono text-xs"
                      data-testid="diff-substitution"
                    >
                      <span className="line-through text-muted-foreground">{tok.text}</span>
                      <span className="text-foreground font-bold font-sans">({tok.expectedWord})</span>
                    </span>
                  );
                }
                return null;
              })}
            </div>
          </div>

          {/* Revealed Reference Transcript */}
          <div className="space-y-1.5 border-t border-border pt-4">
            <div className="text-[11px] font-mono text-muted-foreground">Canonical Reference:</div>
            <p className="text-sm font-medium text-foreground italic" data-testid="canonical-reference">
              &ldquo;{evalResult.canonicalTranscript}&rdquo;
            </p>
          </div>

          {/* Action Row */}
          <div className="flex justify-end gap-3 pt-2">
            <Button
              className="text-xs gap-1.5 h-9"
              onClick={handleNextExercise}
              data-testid="next-exercise-btn"
            >
              <span>Next Exercise</span>
              <ArrowRight className="size-3.5" />
            </Button>
          </div>
        </Card>
      )}
    </div>
  );
}
