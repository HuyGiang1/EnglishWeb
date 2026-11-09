"use client";

import { useLocale } from "@/lib/i18n/locale-provider";
import type { TranslationKey } from "@/lib/i18n/dictionaries";
import { learningErrorKey } from "@/lib/i18n/learning-errors";

import { useEffect, useState, useRef, useCallback } from "react";
import Link from "next/link";
import {
  Play,
  Pause,
  RotateCcw,
  Mic,
  Square,
  Volume2,
  CheckCircle2,
  ArrowRight,
  AlertCircle,
  Clock,
  Sparkles,
  ChevronRight,
  ShieldCheck,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  shadowingApi,
  ShadowingExerciseSummary,
  ShadowingExerciseDetail,
  ShadowingCompleteResponse,
  SelfRating,
} from "@/lib/api/shadowing";
import { settingsApi } from "@/lib/api/settings";

type PageMode = "DIRECTORY" | "PRACTICE";
type PracticeState = "IDLE" | "RECORDING" | "RECORDED" | "SUBMITTING" | "COMPLETED";

const RATING_OPTIONS: { id: SelfRating; label: TranslationKey; desc: TranslationKey }[] = [
  { id: "NEEDS_WORK", label: "learnNeedsWork", desc: "learnHesitantPaceOrUnclearPronunciation" },
  { id: "ACCEPTABLE", label: "learnAcceptable", desc: "learnUnderstandableWithMinorRhythmGaps" },
  { id: "GOOD", label: "learnSelfGood", desc: "learnClearRhythmCadenceAndIntelligibleFlow" },
  { id: "FLUENT", label: "learnFluent", desc: "learnNaturalPacingConfidentIntonationSeamless" },
];

export default function ShadowingPage() {
  const { t, locale } = useLocale();
  // Navigation & list state
  const [mode, setMode] = useState<PageMode>("DIRECTORY");
  const [exercises, setExercises] = useState<ShadowingExerciseSummary[]>([]);
  const [activeExercise, setActiveExercise] = useState<ShadowingExerciseDetail | null>(null);
  const [isLoadingList, setIsLoadingList] = useState(true);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [listError, setListError] = useState<TranslationKey | null>(null);

  // Practice session state
  const [practiceState, setPracticeState] = useState<PracticeState>("IDLE");
  const [attemptId, setAttemptId] = useState<string>("");
  const [selfRating, setSelfRating] = useState<SelfRating | null>(null);
  const [completionResult, setCompletionResult] = useState<ShadowingCompleteResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<TranslationKey | null>(null);

  // Native Reference Audio Player state
  const referenceAudioRef = useRef<HTMLAudioElement | null>(null);
  const [isReferencePlaying, setIsReferencePlaying] = useState(false);
  const [playbackSpeed, setPlaybackSpeed] = useState<number>(1.0);
  const [referenceCurrentTime, setReferenceCurrentTime] = useState<number>(0);
  const [referenceDuration, setReferenceDuration] = useState<number>(0);

  // User Voice Recording state
  const [mediaRecorderSupported, setMediaRecorderSupported] = useState(true);
  const [micPermissionDenied, setMicPermissionDenied] = useState(false);
  const [recordingSeconds, setRecordingSeconds] = useState<number>(0);
  const [userAudioUrl, setUserAudioUrl] = useState<string | null>(null);
  const [isUserPlaying, setIsUserPlaying] = useState(false);
  const userAudioRef = useRef<HTMLAudioElement | null>(null);

  // Internal media stream & recorder references
  const mediaStreamRef = useRef<MediaStream | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const recordedChunksRef = useRef<Blob[]>([]);
  const recordTimerRef = useRef<NodeJS.Timeout | null>(null);

  // Load exercises list
  const loadExercises = useCallback(async () => {
    setIsLoadingList(true);
    setListError(null);
    try {
      const data = await shadowingApi.listExercises();
      setExercises(data);
    } catch (err) {
      const msg = learningErrorKey(err, "learnFailedToLoadShadowingExercises");
      setListError(msg);
    } finally {
      setIsLoadingList(false);
    }
  }, []);

  useEffect(() => {
    loadExercises();
    // Check MediaRecorder support
    if (typeof window !== "undefined") {
      const supported = !!(navigator.mediaDevices && window.MediaRecorder);
      setMediaRecorderSupported(supported);
    }
    // Load preferred audio speed
    settingsApi.getSettings().then((s) => {
      if (s?.preferredAudioSpeed) {
        setPlaybackSpeed(s.preferredAudioSpeed);
      }
    }).catch(() => {
      // Graceful fallback
    });
  }, [loadExercises]);

  // Teardown tracks & revoked URLs on unmount
  const userAudioUrlRef = useRef<string | null>(null);
  userAudioUrlRef.current = userAudioUrl;

  useEffect(() => {
    return () => {
      if (recordTimerRef.current) clearInterval(recordTimerRef.current);
      if (mediaStreamRef.current) {
        mediaStreamRef.current.getTracks().forEach((t) => t.stop());
      }
      if (userAudioUrlRef.current) {
        URL.revokeObjectURL(userAudioUrlRef.current);
      }
    };
  }, []);

  // Select exercise and enter focus mode
  const handleSelectExercise = async (contentId: string) => {
    setIsLoadingDetail(true);
    setErrorMessage(null);
    try {
      const detail = await shadowingApi.getExercise(contentId);
      setActiveExercise(detail);
      setAttemptId(crypto.randomUUID());
      setPracticeState("IDLE");
      setSelfRating(null);
      setCompletionResult(null);
      setRecordingSeconds(0);
      setMicPermissionDenied(false);

      // Clean up previous user audio if any
      if (userAudioUrl) {
        URL.revokeObjectURL(userAudioUrl);
        setUserAudioUrl(null);
      }

      setMode("PRACTICE");
    } catch (err) {
      const msg = learningErrorKey(err, "learnFailedToLoadExerciseDetails2");
      setErrorMessage(msg);
    } finally {
      setIsLoadingDetail(false);
    }
  };

  // Toggle Reference Audio
  const toggleReferenceAudio = () => {
    if (!referenceAudioRef.current) return;
    if (isReferencePlaying) {
      referenceAudioRef.current.pause();
      setIsReferencePlaying(false);
    } else {
      referenceAudioRef.current.playbackRate = playbackSpeed;
      referenceAudioRef.current.play().catch(() => {
        setErrorMessage("learnUnableToPlayReferenceAudioPleaseCheckAudioSetup");
      });
      setIsReferencePlaying(true);
    }
  };

  const handleSpeedChange = (speed: number) => {
    setPlaybackSpeed(speed);
    if (referenceAudioRef.current) {
      referenceAudioRef.current.playbackRate = speed;
    }
  };

  const restartReferenceAudio = () => {
    if (!referenceAudioRef.current) return;
    referenceAudioRef.current.currentTime = 0;
    referenceAudioRef.current.playbackRate = playbackSpeed;
    referenceAudioRef.current.play().catch(() => {});
    setIsReferencePlaying(true);
  };

  // Stop microphone tracks cleanly
  const stopMicrophoneTracks = () => {
    if (mediaStreamRef.current) {
      mediaStreamRef.current.getTracks().forEach((track) => track.stop());
      mediaStreamRef.current = null;
    }
  };

  // Start Recording
  const startRecording = async () => {
    if (!mediaRecorderSupported) {
      setErrorMessage("learnVoiceRecordingIsNotSupportedInThisBrowser");
      return;
    }

    setErrorMessage(null);
    setMicPermissionDenied(false);

    // Stop reference audio if playing
    if (referenceAudioRef.current && isReferencePlaying) {
      referenceAudioRef.current.pause();
      setIsReferencePlaying(false);
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      mediaStreamRef.current = stream;

      // Select mime type if supported
      let options: MediaRecorderOptions = {};
      if (MediaRecorder.isTypeSupported("audio/webm;codecs=opus")) {
        options = { mimeType: "audio/webm;codecs=opus" };
      } else if (MediaRecorder.isTypeSupported("audio/mp4")) {
        options = { mimeType: "audio/mp4" };
      } else if (MediaRecorder.isTypeSupported("audio/ogg")) {
        options = { mimeType: "audio/ogg" };
      }

      const recorder = new MediaRecorder(stream, options);
      mediaRecorderRef.current = recorder;
      recordedChunksRef.current = [];

      recorder.ondataavailable = (e) => {
        if (e.data && e.data.size > 0) {
          recordedChunksRef.current.push(e.data);
        }
      };

      recorder.onerror = () => {
        stopMicrophoneTracks();
        if (recordTimerRef.current) clearInterval(recordTimerRef.current);
        setErrorMessage("learnAnErrorOccurredDuringAudioRecordingPleaseCheckYourMicrophone");
        setPracticeState("IDLE");
      };

      recorder.onstop = () => {
        stopMicrophoneTracks();
        if (recordTimerRef.current) clearInterval(recordTimerRef.current);

        const mime = recorder.mimeType || "audio/webm";
        const blob = new Blob(recordedChunksRef.current, { type: mime });

        if (blob.size > 0) {
          if (userAudioUrl) URL.revokeObjectURL(userAudioUrl);
          const url = URL.createObjectURL(blob);
          setUserAudioUrl(url);
          setPracticeState("RECORDED");
        } else {
          setErrorMessage("learnNoAudioDataWasRecordedPleaseCheckYourMicrophone");
          setPracticeState("IDLE");
        }
      };

      recorder.start(100);
      setPracticeState("RECORDING");
      setRecordingSeconds(0);

      // Start duration counter (capped at 60s)
      recordTimerRef.current = setInterval(() => {
        setRecordingSeconds((prev) => {
          if (prev >= 60) {
            stopRecording();
            return 60;
          }
          return prev + 1;
        });
      }, 1000);
    } catch (err: unknown) {
      stopMicrophoneTracks();
      const error = err as { name?: string };
      if (error?.name === "NotAllowedError" || error?.name === "PermissionDeniedError") {
        setMicPermissionDenied(true);
      } else {
        setErrorMessage("learnMicrophoneAccessFailedPleaseEnsureAMicrophoneIsConnected");
      }
      setPracticeState("IDLE");
    }
  };

  // Stop Recording safely
  const stopRecording = () => {
    if (recordTimerRef.current) {
      clearInterval(recordTimerRef.current);
      recordTimerRef.current = null;
    }
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
      try {
        mediaRecorderRef.current.stop();
      } catch {
        // Safe catch if browser already closed recorder
      }
    }
  };

  // Record Again (reset recording take)
  const handleRecordAgain = () => {
    stopRecording();
    stopMicrophoneTracks();
    if (userAudioUrl) {
      URL.revokeObjectURL(userAudioUrl);
      setUserAudioUrl(null);
    }
    setPracticeState("IDLE");
    setRecordingSeconds(0);
    setSelfRating(null);
    setErrorMessage(null);
  };

  // Safe Exit Practice (stops recording, tracks, timers and revokes URLs)
  const handleExitPractice = () => {
    stopRecording();
    stopMicrophoneTracks();
    if (userAudioUrl) {
      URL.revokeObjectURL(userAudioUrl);
      setUserAudioUrl(null);
    }
    if (referenceAudioRef.current && isReferencePlaying) {
      referenceAudioRef.current.pause();
      setIsReferencePlaying(false);
    }
    setPracticeState("IDLE");
    setRecordingSeconds(0);
    setSelfRating(null);
    setCompletionResult(null);
    setErrorMessage(null);
    setMode("DIRECTORY");
  };

  // Toggle User Playback
  const toggleUserAudio = () => {
    if (!userAudioRef.current) return;
    if (isUserPlaying) {
      userAudioRef.current.pause();
      setIsUserPlaying(false);
    } else {
      userAudioRef.current.play().catch(() => {});
      setIsUserPlaying(true);
    }
  };

  // Complete Practice Submission
  const handleCompletePractice = async () => {
    if (
      !activeExercise ||
      !selfRating ||
      practiceState === "SUBMITTING" ||
      practiceState !== "RECORDED" ||
      !userAudioUrl ||
      recordingSeconds <= 0
    ) {
      return;
    }

    setPracticeState("SUBMITTING");
    setErrorMessage(null);

    try {
      const response = await shadowingApi.completeTake(activeExercise.contentId, {
        attemptId,
        selfRating,
        durationSeconds: Number(recordingSeconds.toFixed(2)),
      });

      setCompletionResult(response);
      setPracticeState("COMPLETED");
      // Refresh exercise list metadata in background
      loadExercises();
    } catch (err) {
      const msg = learningErrorKey(err, "learnFailedToRecordPracticeCompletion");
      setErrorMessage(msg);
      setPracticeState("RECORDED");
    }
  };

  // Practice Again (same exercise, fresh attemptId)
  const handlePracticeAgain = () => {
    if (userAudioUrl) {
      URL.revokeObjectURL(userAudioUrl);
      setUserAudioUrl(null);
    }
    setAttemptId(crypto.randomUUID());
    setPracticeState("IDLE");
    setSelfRating(null);
    setCompletionResult(null);
    setRecordingSeconds(0);
    setErrorMessage(null);
  };

  // Advance to next exercise
  const handleNextExercise = () => {
    if (!activeExercise || exercises.length === 0) {
      setMode("DIRECTORY");
      return;
    }

    const currentIndex = exercises.findIndex((e) => e.contentId === activeExercise.contentId);
    const nextIndex = (currentIndex + 1) % exercises.length;
    const nextEx = exercises[nextIndex];

    if (nextEx && nextEx.isAudioAvailable) {
      handleSelectExercise(nextEx.contentId);
    } else {
      setMode("DIRECTORY");
    }
  };

  // Keep latest references for keyboard shortcuts
  const latestHandlersRef = useRef({
    mode,
    practiceState,
    selfRating,
    toggleReferenceAudio,
    startRecording,
    stopRecording,
    handleCompletePractice,
  });
  useEffect(() => {
    latestHandlersRef.current = {
      mode,
      practiceState,
      selfRating,
      toggleReferenceAudio,
      startRecording,
      stopRecording,
      handleCompletePractice,
    };
  });

  // Global Keyboard Shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      if (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.tagName === "SELECT" || target.tagName === "BUTTON" || target.isContentEditable) {
        return;
      }

      const h = latestHandlersRef.current;
      if (h.mode === "PRACTICE") {
        // Mod+P or Mod+Space: toggle reference audio
        if ((e.metaKey || e.ctrlKey) && (e.key === "p" || e.key === " " || e.code === "Space")) {
          e.preventDefault();
          h.toggleReferenceAudio();
        }
        // Mod+R: toggle recording
        else if ((e.metaKey || e.ctrlKey) && (e.key === "r" || e.key === "R")) {
          e.preventDefault();
          if (h.practiceState === "RECORDING") {
            h.stopRecording();
          } else if (h.practiceState === "IDLE" || h.practiceState === "RECORDED") {
            h.startRecording();
          }
        }
        // 1..4: select self-rating
        else if (h.practiceState === "RECORDED" && ["1", "2", "3", "4"].includes(e.key)) {
          const ratingMap: Record<string, SelfRating> = {
            "1": "NEEDS_WORK",
            "2": "ACCEPTABLE",
            "3": "GOOD",
            "4": "FLUENT",
          };
          setSelfRating(ratingMap[e.key]);
        }
        // Mod+Enter: submit completed practice
        else if ((e.metaKey || e.ctrlKey) && e.key === "Enter" && h.practiceState === "RECORDED" && h.selfRating) {
          e.preventDefault();
          h.handleCompletePractice();
        }
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  return (
    <div className="min-h-screen bg-[#FAFAFA] text-[#111111] flex flex-col font-sans selection:bg-[#E5E5E5]">
      {/* Top Header */}
      <header className="border-b border-[#E5E5E5] bg-white px-6 py-4 flex items-center justify-between sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <Link
            href="/dashboard"
            onClick={handleExitPractice}
            className="text-sm font-medium text-[#737373] hover:text-[#111111] transition-colors flex items-center gap-1.5 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[#111111] rounded px-1"
          >{t("learnExit")}</Link>
          <span className="text-[#E5E5E5]">|</span>
          <div className="flex items-center gap-2">
            <Mic className="h-4 w-4 text-[#111111]" />
            <h1 className="text-base font-semibold tracking-tight text-[#111111]">{t("learnSpeechShadowing")}</h1>
          </div>
          <Badge variant="outline" className="text-xs uppercase tracking-wider font-mono ml-1 border-[#E5E5E5]">{t("learnFocusMode")}</Badge>
        </div>

        {mode === "PRACTICE" && activeExercise && (
          <div className="flex items-center gap-2">
            <Badge variant="outline" className="border-[#E5E5E5] font-mono text-xs">
              {activeExercise.cefrLevel}
            </Badge>
            <Button
              variant="ghost"
              size="sm"
              onClick={handleExitPractice}
              className="text-xs text-[#737373] hover:text-[#111111]"
            >{t("learnChangeExercise")}</Button>
          </div>
        )}
      </header>

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col items-center justify-center p-6 md:p-10 max-w-4xl w-full mx-auto">
        {mode === "DIRECTORY" ? (
          /* ======================================================================== */
          /* SECTION A: Content Directory                                              */
          /* ======================================================================== */
          <div className="w-full max-w-3xl space-y-6">
            <div className="text-center space-y-2">
              <h2 className="text-2xl font-bold tracking-tight text-[#111111]">{t("learnSelectShadowingExercise")}</h2>
              <p className="text-sm text-[#737373] max-w-lg mx-auto">{t("learnListenToAuthenticNativeSpeechClipsAndPracticeVoiceMimicryWithLocalizedRecordingCompariso")}</p>
            </div>

            {listError && (
              <div role="alert" className="p-4 rounded-md border border-border bg-muted text-foreground text-sm flex items-center gap-3">
                <AlertCircle className="h-4 w-4 text-foreground shrink-0" />
                <span>{t(listError)}</span>
                <Button variant="outline" size="sm" onClick={loadExercises} className="ml-auto text-xs bg-white">{t("learnRetry")}</Button>
              </div>
            )}

            {isLoadingList ? (
              <div className="space-y-3">
                {[1, 2, 3, 4, 5].map((i) => (
                  <div key={i} className="h-20 bg-white border border-[#E5E5E5] rounded-md animate-pulse" />
                ))}
              </div>
            ) : exercises.length === 0 ? (
              <div className="p-12 text-center border border-dashed border-[#E5E5E5] rounded-md bg-white space-y-3">
                <Mic className="h-8 w-8 text-[#A3A3A3] mx-auto" />
                <p className="text-sm text-[#737373]">{t("learnNoShadowingExercisesAvailableYet")}</p>
              </div>
            ) : (
              <div className="space-y-3">
                {exercises.map((item) => (
                  <div
                    key={item.contentId}
                    data-testid={`exercise-card-${item.contentId}`}
                    onClick={() => item.isAudioAvailable && handleSelectExercise(item.contentId)}
                    className={`p-5 rounded-md border bg-white flex items-center justify-between transition-all ${
                      item.isAudioAvailable
                        ? "border-[#E5E5E5] hover:border-[#111111] hover:shadow-sm cursor-pointer"
                        : "border-[#E5E5E5]/60 bg-[#FAFAFA] opacity-75 cursor-not-allowed"
                    }`}
                  >
                    <div className="space-y-1.5 flex-1 pr-4">
                      <div className="flex items-center gap-2">
                        <Badge variant="outline" className="border-[#111111] text-[#111111] font-mono text-xs">
                          {item.cefrLevel}
                        </Badge>
                        <span className="text-xs text-[#737373] uppercase tracking-wider font-mono">
                          {item.topicTag}
                        </span>
                        {!item.isAudioAvailable ? (
                          <span
                            data-testid="audio-missing-badge"
                            className="text-[11px] font-mono px-2 py-0.5 rounded border border-dashed border-[#A3A3A3] text-[#737373]"
                          >{t("learnAudioMissing")}</span>
                        ) : item.audioReadiness === "SYNTHETIC_FIXTURE" || !item.isSpokenSpeech ? (
                          <span
                            data-testid="audio-synthetic-badge"
                            className="text-[11px] font-mono px-2 py-0.5 rounded border border-border bg-muted text-foreground"
                          >{t("learnTestToneFixture")}</span>
                        ) : (
                          <span
                            data-testid="audio-spoken-badge"
                            className="text-[11px] font-mono px-2 py-0.5 rounded border border-neutral-200 bg-neutral-50 text-neutral-600"
                          >{t("learnSpokenAudio")}</span>
                        )}
                        {item.takeCount > 0 && (
                          <span className="text-[11px] font-mono text-[#737373] bg-[#F5F5F5] px-2 py-0.5 rounded">
                            {item.takeCount} {item.takeCount === 1 ? t("learnTake") : t("learnTakes")}
                          </span>
                        )}
                        {item.lastSelfRating && (
                          <Badge variant="outline" className="text-[10px] font-mono border-neutral-300">
                            {item.lastSelfRating}
                          </Badge>
                        )}
                      </div>
                      <h3 className="text-base font-semibold text-[#111111] tracking-tight">{item.title}</h3>
                    </div>

                    <div className="flex items-center gap-3 shrink-0">
                      <div className="flex items-center gap-1 text-xs text-[#737373] font-mono">
                        <Clock className="h-3.5 w-3.5" />
                        <span>{item.audioDurationSeconds}s</span>
                      </div>
                      <Button
                        variant={item.isAudioAvailable ? "outline" : "ghost"}
                        size="sm"
                        disabled={!item.isAudioAvailable || isLoadingDetail}
                        aria-label={`${t("learnStart")}: ${item.title}`}
                        onClick={(event) => { event.stopPropagation(); handleSelectExercise(item.contentId); }}
                        className="text-xs h-8 border-[#E5E5E5]"
                      >{t("learnStart")}{" "}<ChevronRight className="h-3 w-3 ml-1" />
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : (
          /* ======================================================================== */
          /* SECTION B-F: Focus Practice View                                          */
          /* ======================================================================== */
          <div className="w-full max-w-2xl space-y-6">
            {/* Hidden Native Audio Element */}
            {activeExercise && (
              <audio
                ref={referenceAudioRef}
                src={activeExercise.audioUrl}
                preload="auto"
                onTimeUpdate={(e) => setReferenceCurrentTime(e.currentTarget.currentTime)}
                onLoadedMetadata={(e) => setReferenceDuration(e.currentTarget.duration)}
                onEnded={() => setIsReferencePlaying(false)}
                onError={() => {
                  setIsReferencePlaying(false);
                  setErrorMessage("learnFailedToLoadReferenceAudioAsset");
                }}
              />
            )}

            {/* Hidden User Audio Element */}
            {userAudioUrl && (
              <audio
                ref={userAudioRef}
                src={userAudioUrl}
                preload="auto"
                onEnded={() => setIsUserPlaying(false)}
              />
            )}

            {/* Error Banners */}
            {errorMessage && (
              <div role="alert" className="p-4 rounded-md border border-border bg-muted text-foreground text-sm flex items-center gap-3">
                <AlertCircle className="h-4 w-4 text-foreground shrink-0" />
                <span className="flex-1">{t(errorMessage)}</span>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setErrorMessage(null)}
                  className="text-xs text-foreground hover:text-foreground"
                >{t("learnDismiss")}</Button>
              </div>
            )}

            {micPermissionDenied && (
              <div className="p-4 rounded-md border border-border bg-muted text-foreground text-sm space-y-2">
                <div className="flex items-center gap-2 font-medium">
                  <AlertCircle className="h-4 w-4 text-foreground shrink-0" />
                  <span>{t("learnMicrophonePermissionRequired")}</span>
                </div>
                <p className="text-xs text-foreground">{t("learnYourBrowserBlockedMicrophoneAccessToRecordYourShadowingTakePleaseAllowMicrophoneAccessIn")}</p>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={startRecording}
                  className="text-xs border-border bg-white"
                >{t("learnRequestPermissionAgain")}</Button>
              </div>
            )}

            {!mediaRecorderSupported && (
              <div className="p-4 rounded-md border border-[#E5E5E5] bg-white text-[#737373] text-sm">{t("learnVoiceRecordingIsUnavailableInThisEnvironmentYouCanListenToNativeAudioForPassiveShadowing")}</div>
            )}

            {activeExercise?.audioReadiness === "SYNTHETIC_FIXTURE" && (
              <div
                data-testid="synthetic-notice-banner"
                className="p-3.5 rounded-md border border-border bg-muted text-foreground text-xs flex items-center gap-2"
              >
                <AlertCircle className="h-4 w-4 text-foreground shrink-0" />
                <span>
                  <strong>{t("learnTransportTestFixture")}</strong>{" "}{t("learnThisExerciseUsesASyntheticTestAudioToneForMediaTransportVerificationNotAuthenticSpokenEn")}</span>
              </div>
            )}

            {/* Script Display Card */}
            <div className="p-8 rounded-lg border border-[#E5E5E5] bg-white text-center space-y-4 shadow-sm">
              <div className="flex items-center justify-center gap-2">
                <Badge variant="outline" className="border-[#111111] font-mono text-xs">
                  {activeExercise?.cefrLevel}
                </Badge>
                <span className="text-xs uppercase tracking-wider text-[#737373] font-mono">
                  {activeExercise?.topicTag}
                </span>
              </div>

              <blockquote className="text-2xl font-semibold tracking-tight text-[#111111] leading-relaxed">
                &ldquo;{activeExercise?.transcriptText}&rdquo;
              </blockquote>

              <p className="text-xs text-[#737373] font-mono">{t("learnFocusOnMatchingCadenceStressAndNaturalBreathingPauses")}</p>
            </div>

            {/* Dual Audio Player Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Native Speaker Reference Player */}
              <div className="p-5 rounded-lg border border-[#E5E5E5] bg-white space-y-4 shadow-sm">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-mono uppercase tracking-wider text-[#737373] flex items-center gap-1.5">
                    <Volume2 className="h-3.5 w-3.5" />{" "}{t("learnNativeReference")}</span>
                  <div className="flex items-center gap-1 bg-[#F5F5F5] p-0.5 rounded">
                    {[0.75, 1.0, 1.25].map((speed) => (
                      <button
                        key={speed}
                        onClick={() => handleSpeedChange(speed)}
                        className={`text-[11px] font-mono px-2 py-0.5 rounded transition-all ${
                          playbackSpeed === speed
                            ? "bg-white text-[#111111] font-bold shadow-xs"
                            : "text-[#737373] hover:text-[#111111]"
                        }`}
                      >
                        {speed}x
                      </button>
                    ))}
                  </div>
                </div>

                <div className="flex items-center gap-3">
                  <Button
                    variant="outline"
                    size="icon"
                    onClick={toggleReferenceAudio}
                    className="h-12 w-12 rounded-full border-[#111111] text-[#111111] hover:bg-[#F5F5F5] shrink-0"
                    aria-label={isReferencePlaying ? t("learnPauseNativeSpeech") : t("learnPlayNativeSpeech")}
                  >
                    {isReferencePlaying ? <Pause className="h-5 w-5" /> : <Play className="h-5 w-5 ml-0.5" />}
                  </Button>

                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={restartReferenceAudio}
                    className="h-10 w-10 text-[#737373] hover:text-[#111111]"
                    aria-label={t("learnRestartAudioFromBeginning")}
                  >
                    <RotateCcw className="h-4 w-4" />
                  </Button>

                  <div className="flex-1 text-right text-xs font-mono text-[#737373]">
                    {referenceCurrentTime.toFixed(1)}s / {referenceDuration ? referenceDuration.toFixed(1) : (activeExercise?.audioDurationSeconds ?? 0)}s
                  </div>
                </div>
              </div>

              {/* Learner Recording Player */}
              <div className="p-5 rounded-lg border border-[#E5E5E5] bg-white space-y-4 shadow-sm">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-mono uppercase tracking-wider text-[#737373] flex items-center gap-1.5">
                    <Mic className="h-3.5 w-3.5" />{" "}{t("learnYourTake")}</span>
                  {practiceState === "RECORDING" && (
                    <span className="flex items-center gap-1.5 text-xs font-mono text-foreground font-semibold animate-pulse">
                      <span className="h-2 w-2 rounded-full bg-muted" />{t("learnREC")}{" "}{recordingSeconds}s
                    </span>
                  )}
                  {practiceState === "RECORDED" && (
                    <span className="text-xs font-mono text-[#737373]">{recordingSeconds}{t("learnSCaptured")}</span>
                  )}
                </div>

                <div className="flex items-center gap-3">
                  {practiceState === "RECORDING" ? (
                    <Button
                      variant="default"
                      size="icon"
                      onClick={stopRecording}
                      className="h-12 w-12 rounded-full bg-muted hover:bg-muted text-white shrink-0 animate-pulse"
                      aria-label={t("learnStopRecording")}
                    >
                      <Square className="h-4 w-4 fill-current" />
                    </Button>
                  ) : (
                    <Button
                      variant="outline"
                      size="icon"
                      onClick={startRecording}
                      disabled={!mediaRecorderSupported}
                      className="h-12 w-12 rounded-full border-[#111111] text-[#111111] hover:bg-[#F5F5F5] shrink-0"
                      aria-label={t("learnStartRecording")}
                    >
                      <Mic className="h-5 w-5" />
                    </Button>
                  )}

                  {userAudioUrl && practiceState !== "RECORDING" ? (
                    <>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={toggleUserAudio}
                        className="text-xs border-[#E5E5E5] flex items-center gap-1.5"
                      >
                        {isUserPlaying ? <Pause className="h-3.5 w-3.5" /> : <Play className="h-3.5 w-3.5" />}
                        {isUserPlaying ? t("learnPauseTake") : t("learnPlayTake")}
                      </Button>

                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={handleRecordAgain}
                        className="text-xs text-[#737373] hover:text-[#111111]"
                      >{t("learnRecordAgain")}</Button>
                    </>
                  ) : (
                    <span role="status" aria-live="polite" className="text-xs text-[#737373] font-mono">
                      {practiceState === "RECORDING" ? t("learnListeningToYourVoice") : t("learnPressRecordToMimicSpeech")}
                    </span>
                  )}
                </div>
              </div>
            </div>

            {/* Privacy Disclosure */}
            <div className="flex items-center gap-2 px-1 text-xs text-[#737373] font-mono">
              <ShieldCheck className="h-4 w-4 shrink-0 text-neutral-500" />
              <span>{t("learnYourVoiceRecordingIsPlayedBackLocallyInYourBrowserForSelfComparisonAndIsNotStoredOnTheSe")}</span>
            </div>

            {/* Self-Assessment & Completion Section */}
            {practiceState === "COMPLETED" && completionResult ? (
              <div className="p-8 rounded-lg border border-[#111111] bg-white text-center space-y-4 shadow-sm animate-in fade-in duration-200">
                <CheckCircle2 className="h-10 w-10 text-[#111111] mx-auto" />
                <h3 className="text-xl font-bold tracking-tight text-[#111111]">{t("learnPracticeTakeCompleted")}</h3>
                <div className="flex items-center justify-center gap-3">
                  <Badge variant="outline" className="border-[#111111] text-[#111111] font-mono text-sm px-3 py-1">{t("learnSelfRating")}{" "}{t(RATING_OPTIONS.find((option) => option.id === completionResult.selfRating)!.label)}
                  </Badge>
                  <span className="text-xs text-[#737373] font-mono">{t("learnCompletedAt")}{" "}{new Date(completionResult.completedAt).toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" })}
                  </span>
                </div>
                <p className="text-xs text-[#737373] font-mono max-w-md mx-auto">
                  {completionResult.verifiedActivityCreated
                    ? t("learn1SpeechShadowingTakeVerifiedAndRecordedTowardTodaySDailyLearningGoal")
                    : t("learnPracticeTakeCompletedLocallySyntheticTestToneExercisesDoNotCountTowardDailyLearningGoal")}
                </p>

                <div className="flex items-center justify-center gap-3 pt-4">
                  <Button variant="outline" size="sm" onClick={handlePracticeAgain} className="border-[#E5E5E5]">{t("learnPracticeAgain")}</Button>
                  <Button variant="default" size="sm" onClick={handleNextExercise} className="bg-[#111111] text-white">{t("learnNextExercise")}{" "}<ArrowRight className="h-4 w-4 ml-1.5" />
                  </Button>
                </div>
              </div>
            ) : (
              <div className="p-6 rounded-lg border border-[#E5E5E5] bg-white space-y-5 shadow-sm">
                <div>
                  <h4 className="text-sm font-semibold tracking-tight text-[#111111]">{t("learnLearnerSelfAssessment")}</h4>
                  <p className="text-xs text-[#737373] font-mono">{t("learnListenToYourTakeAboveAndReflectOnRhythmCadenceAndClarity")}</p>
                </div>

                <div className="grid grid-cols-2 md:grid-cols-4 gap-2.5">
                  {RATING_OPTIONS.map((opt) => (
                    <button
                      key={opt.id}
                      aria-pressed={selfRating === opt.id}
                      type="button"
                      disabled={practiceState === "RECORDING" || !userAudioUrl}
                      onClick={() => setSelfRating(opt.id)}
                      className={`p-3 rounded-md border text-left transition-all ${
                        selfRating === opt.id
                          ? "border-[#111111] bg-[#F5F5F5] ring-1 ring-[#111111]"
                          : "border-[#E5E5E5] hover:border-[#737373] bg-white"
                      } ${practiceState === "RECORDING" || !userAudioUrl ? "opacity-50 cursor-not-allowed" : "cursor-pointer"}`}
                    >
                      <div className="text-xs font-bold text-[#111111]">{t(opt.label)}</div>
                      <div className="text-[11px] text-[#737373] mt-1 line-clamp-2">{t(opt.desc)}</div>
                    </button>
                  ))}
                </div>

                <div className="flex items-center justify-between pt-2">
                  <span className="text-xs text-[#737373] font-mono">
                    {!userAudioUrl
                      ? t("learnRecordATakeToUnlockSelfAssessment")
                      : !selfRating
                      ? t("learnSelectASelfRatingToFinish")
                      : t("learnReadyToComplete")}
                  </span>

                  <Button
                    variant="default"
                    disabled={!userAudioUrl || !selfRating || practiceState === "SUBMITTING" || recordingSeconds <= 0}
                    onClick={handleCompletePractice}
                    className="bg-[#111111] hover:bg-[#333333] text-white px-6 font-medium"
                  >
                    {practiceState === "SUBMITTING" ? (
                      t("learnRecordingTake")
                    ) : (
                      <>{t("learnCompletePractice")}{" "}<Sparkles className="h-4 w-4 ml-1.5" />
                      </>
                    )}
                  </Button>
                </div>
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  );
}
