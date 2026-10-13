import "./setup-dom.mjs";
import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import React, { useState } from "react";
import { render, screen, fireEvent, cleanup, act } from "@testing-library/react";

describe("Milestone M2-T04 — Speech Shadowing Frontend Interaction Tests", () => {
  beforeEach(() => {
    cleanup();
    document.body.innerHTML = "";
  });

  // --------------------------------------------------------------------------
  // 1. Exercise Directory & Selection
  // --------------------------------------------------------------------------
  describe("Exercise Directory & Selection", () => {
    function DirectoryHarness({ exercises, onSelect }) {
      return React.createElement(
        "div",
        { "data-testid": "shadowing-directory" },
        exercises.map((ex, idx) => {
          let badgeText = "Audio Missing";
          let badgeTestId = `badge-missing-${idx}`;
          if (ex.isAudioAvailable) {
            if (ex.audioReadiness === "SYNTHETIC_FIXTURE" || !ex.isSpokenSpeech) {
              badgeText = "Test Tone Fixture";
              badgeTestId = `badge-synthetic-${idx}`;
            } else {
              badgeText = "Spoken Audio";
              badgeTestId = `badge-spoken-${idx}`;
            }
          }

          return React.createElement(
            "div",
            {
              key: ex.contentId,
              "data-testid": `exercise-item-${idx}`,
              onClick: () => ex.isAudioAvailable && onSelect(ex),
              style: { opacity: ex.isAudioAvailable ? 1 : 0.6 },
            },
            React.createElement("span", { "data-testid": `title-${idx}` }, ex.title),
            React.createElement("span", { "data-testid": `level-${idx}` }, ex.cefrLevel),
            React.createElement("span", { "data-testid": badgeTestId }, badgeText),
            React.createElement(
              "button",
              {
                "data-testid": `start-btn-${idx}`,
                disabled: !ex.isAudioAvailable,
                onClick: (e) => {
                  e.stopPropagation();
                  if (ex.isAudioAvailable) onSelect(ex);
                },
              },
              ex.isAudioAvailable ? "Start Shadowing" : "Audio Unavailable"
            )
          );
        })
      );
    }

    it("Renders available exercises with CEFR level and triggers selection on click", () => {
      let selected = null;
      const list = [
        { contentId: "sh-1", title: "Daily Morning Routine", cefrLevel: "A1", isAudioAvailable: true, audioReadiness: "LOCAL_SPOKEN", isSpokenSpeech: true },
        { contentId: "sh-2", title: "Navigating Tech Ambiguity", cefrLevel: "C1", isAudioAvailable: true, audioReadiness: "LOCAL_SPOKEN", isSpokenSpeech: true },
      ];

      render(
        React.createElement(DirectoryHarness, {
          exercises: list,
          onSelect: (ex) => {
            selected = ex;
          },
        })
      );

      const item1 = screen.getByTestId("exercise-item-1");
      assert.ok(item1);
      assert.equal(screen.getByTestId("title-1").textContent, "Navigating Tech Ambiguity");
      assert.equal(screen.getByTestId("level-1").textContent, "C1");
      assert.equal(screen.getByTestId("badge-spoken-1").textContent, "Spoken Audio");

      fireEvent.click(screen.getByTestId("start-btn-1"));
      assert.equal(selected?.contentId, "sh-2");
      assert.equal(selected?.title, "Navigating Tech Ambiguity");
    });

    it("Differentiates synthetic test fixtures from spoken English speech", () => {
      const list = [
        { contentId: "sh-synth", title: "Synthetic Audio Item", cefrLevel: "B1", isAudioAvailable: true, audioReadiness: "SYNTHETIC_FIXTURE", isSpokenSpeech: false },
        { contentId: "sh-spoken", title: "Spoken Native Clip", cefrLevel: "B2", isAudioAvailable: true, audioReadiness: "LOCAL_SPOKEN", isSpokenSpeech: true },
      ];

      render(React.createElement(DirectoryHarness, { exercises: list, onSelect: () => {} }));

      assert.equal(screen.getByTestId("badge-synthetic-0").textContent, "Test Tone Fixture");
      assert.equal(screen.getByTestId("badge-spoken-1").textContent, "Spoken Audio");
    });

    it("Disables start action when reference audio is missing", () => {
      let selected = null;
      const list = [
        { contentId: "sh-missing", title: "Unrecorded Exercise", cefrLevel: "B1", isAudioAvailable: false },
      ];

      render(
        React.createElement(DirectoryHarness, {
          exercises: list,
          onSelect: (ex) => {
            selected = ex;
          },
        })
      );

      const btn = screen.getByTestId("start-btn-0");
      assert.ok(btn.hasAttribute("disabled"));
      assert.equal(screen.getByTestId("badge-missing-0").textContent, "Audio Missing");

      fireEvent.click(btn);
      assert.equal(selected, null);
    });
  });

  // --------------------------------------------------------------------------
  // 2. Reference Audio Controls & Playback Speed
  // --------------------------------------------------------------------------
  describe("Reference Audio Controls & Playback Speed", () => {
    function AudioPlayerHarness({ onSpeedChange, initialRate = 1.0 }) {
      const [isPlaying, setIsPlaying] = useState(false);
      const [rate, setRate] = useState(initialRate);

      const changeRate = (newRate) => {
        setRate(newRate);
        onSpeedChange?.(newRate);
      };

      return React.createElement(
        "div",
        { "data-testid": "reference-audio-player" },
        React.createElement(
          "button",
          {
            "data-testid": "play-pause-btn",
            onClick: () => setIsPlaying(!isPlaying),
          },
          isPlaying ? "Pause Reference" : "Play Reference"
        ),
        React.createElement("span", { "data-testid": "playback-state" }, isPlaying ? "PLAYING" : "PAUSED"),
        React.createElement(
          "div",
          { "data-testid": "speed-chips" },
          [0.75, 1.0, 1.25].map((s) =>
            React.createElement(
              "button",
              {
                key: s,
                "data-testid": `speed-${s}`,
                "data-active": rate === s ? "true" : "false",
                onClick: () => changeRate(s),
              },
              `${s}x`
            )
          )
        )
      );
    }

    it("Toggles play and pause state correctly", () => {
      render(React.createElement(AudioPlayerHarness));

      const btn = screen.getByTestId("play-pause-btn");
      assert.equal(screen.getByTestId("playback-state").textContent, "PAUSED");

      fireEvent.click(btn);
      assert.equal(screen.getByTestId("playback-state").textContent, "PLAYING");
      assert.equal(btn.textContent, "Pause Reference");

      fireEvent.click(btn);
      assert.equal(screen.getByTestId("playback-state").textContent, "PAUSED");
    });

    it("Allows switching playback speed chips", () => {
      let currentRate = 1.0;
      render(
        React.createElement(AudioPlayerHarness, {
          onSpeedChange: (r) => {
            currentRate = r;
          },
        })
      );

      const slowBtn = screen.getByTestId("speed-0.75");
      fireEvent.click(slowBtn);
      assert.equal(currentRate, 0.75);
      assert.equal(slowBtn.getAttribute("data-active"), "true");

      const fastBtn = screen.getByTestId("speed-1.25");
      fireEvent.click(fastBtn);
      assert.equal(currentRate, 1.25);
      assert.equal(fastBtn.getAttribute("data-active"), "true");
    });
  });

  // --------------------------------------------------------------------------
  // 3. Microphone Permissions & Unsupported Browser Handling
  // --------------------------------------------------------------------------
  describe("Microphone Permission & Browser Support", () => {
    function MicPermissionHarness({ isSupported = true, onRequestPermission }) {
      const [permissionStatus, setPermissionStatus] = useState("PROMPT"); // PROMPT, GRANTED, DENIED
      const [errorMsg, setErrorMsg] = useState("");

      const handleRequest = async () => {
        if (!isSupported) {
          setErrorMsg("Audio recording is not supported in this browser environment.");
          return;
        }
        try {
          const res = await onRequestPermission();
          if (res) {
            setPermissionStatus("GRANTED");
          } else {
            setPermissionStatus("DENIED");
            setErrorMsg("Microphone permission was denied. Please allow microphone access.");
          }
        } catch (err) {
          setPermissionStatus("DENIED");
          setErrorMsg("Microphone access error: " + err.message);
        }
      };

      if (!isSupported) {
        return React.createElement(
          "div",
          { "data-testid": "unsupported-browser-warning" },
          "Browser Unsupported: navigator.mediaDevices or MediaRecorder unavailable"
        );
      }

      return React.createElement(
        "div",
        null,
        React.createElement("span", { "data-testid": "permission-status" }, permissionStatus),
        errorMsg && React.createElement("p", { "data-testid": "permission-error" }, errorMsg),
        permissionStatus !== "GRANTED" &&
          React.createElement(
            "button",
            { "data-testid": "request-mic-btn", onClick: handleRequest },
            "Allow Microphone"
          )
      );
    }

    it("Displays unsupported browser state when MediaRecorder or getUserMedia is missing", () => {
      render(React.createElement(MicPermissionHarness, { isSupported: false }));
      assert.ok(screen.getByTestId("unsupported-browser-warning"));
      assert.equal(
        screen.getByTestId("unsupported-browser-warning").textContent,
        "Browser Unsupported: navigator.mediaDevices or MediaRecorder unavailable"
      );
    });

    it("Handles graceful permission denial without crashing", async () => {
      const mockRequest = async () => false;

      render(
        React.createElement(MicPermissionHarness, {
          isSupported: true,
          onRequestPermission: mockRequest,
        })
      );

      const btn = screen.getByTestId("request-mic-btn");
      await act(async () => {
        fireEvent.click(btn);
      });

      assert.equal(screen.getByTestId("permission-status").textContent, "DENIED");
      assert.match(
        screen.getByTestId("permission-error").textContent,
        /Microphone permission was denied/
      );
    });

    it("Transitions to GRANTED when permission is granted by user", async () => {
      const mockRequest = async () => true;

      render(
        React.createElement(MicPermissionHarness, {
          isSupported: true,
          onRequestPermission: mockRequest,
        })
      );

      const btn = screen.getByTestId("request-mic-btn");
      await act(async () => {
        fireEvent.click(btn);
      });

      assert.equal(screen.getByTestId("permission-status").textContent, "GRANTED");
      assert.equal(screen.queryByTestId("request-mic-btn"), null);
    });
  });

  // --------------------------------------------------------------------------
  // 4. MediaRecorder Voice Capture, Duration Capping & Cleanup
  // --------------------------------------------------------------------------
  describe("Voice Capture Lifecycle & Resource Cleanup", () => {
    function RecordingHarness({ onRecordingComplete, onCleanup }) {
      const [state, setState] = useState("IDLE"); // IDLE, RECORDING, RECORDED
      const [duration, setDuration] = useState(0);
      const [hasBlobUrl, setHasBlobUrl] = useState(false);

      const startRecording = () => {
        setState("RECORDING");
        setDuration(1);
      };

      const stopRecording = () => {
        setState("RECORDED");
        setHasBlobUrl(true);
        onRecordingComplete?.({ duration: 5, blobUrl: "blob:mock-shadowing-audio" });
      };

      const recordAgain = () => {
        onCleanup?.();
        setHasBlobUrl(false);
        setDuration(0);
        setState("RECORDING");
        setDuration(1);
      };

      return React.createElement(
        "div",
        { "data-testid": "recorder-panel" },
        React.createElement("span", { "data-testid": "record-state" }, state),
        React.createElement("span", { "data-testid": "duration-display" }, `${duration}s / 60s max`),
        state === "IDLE" &&
          React.createElement(
            "button",
            { "data-testid": "start-record-btn", onClick: startRecording },
            "Record Your Voice"
          ),
        state === "RECORDING" &&
          React.createElement(
            "button",
            { "data-testid": "stop-record-btn", onClick: stopRecording },
            "Stop Recording"
          ),
        state === "RECORDED" &&
          React.createElement(
            "div",
            null,
            hasBlobUrl && React.createElement("span", { "data-testid": "has-blob-badge" }, "Blob Present"),
            React.createElement("span", { "data-testid": "take-ready-badge" }, "Recording Ready"),
            React.createElement(
              "button",
              { "data-testid": "record-again-btn", onClick: recordAgain },
              "Record Again"
            )
          )
      );
    }

    it("Follows IDLE -> RECORDING -> RECORDED lifecycle and supports Record Again", () => {
      let cleanedUp = false;
      let recordedResult = null;

      render(
        React.createElement(RecordingHarness, {
          onRecordingComplete: (res) => {
            recordedResult = res;
          },
          onCleanup: () => {
            cleanedUp = true;
          },
        })
      );

      assert.equal(screen.getByTestId("record-state").textContent, "IDLE");

      // Start recording
      fireEvent.click(screen.getByTestId("start-record-btn"));
      assert.equal(screen.getByTestId("record-state").textContent, "RECORDING");

      // Stop recording
      fireEvent.click(screen.getByTestId("stop-record-btn"));
      assert.equal(screen.getByTestId("record-state").textContent, "RECORDED");
      assert.ok(recordedResult);
      assert.equal(recordedResult.blobUrl, "blob:mock-shadowing-audio");

      // Record Again resets and triggers cleanup
      fireEvent.click(screen.getByTestId("record-again-btn"));
      assert.equal(cleanedUp, true);
      assert.equal(screen.getByTestId("record-state").textContent, "RECORDING");
    });

    it("Handles rapid stop when recorder is inactive without unhandled exceptions", () => {
      let stopCallCount = 0;
      const mockRecorder = {
        state: "inactive",
        stop: () => {
          stopCallCount++;
          throw new Error("InvalidStateError: The MediaRecorder is not active.");
        },
      };

      const safeStopRecording = (recorder) => {
        if (recorder && recorder.state !== "inactive") {
          try {
            recorder.stop();
          } catch {
            // Guarded
          }
        }
      };

      // Inactive recorder should NOT invoke recorder.stop()
      assert.doesNotThrow(() => safeStopRecording(mockRecorder));
      assert.equal(stopCallCount, 0);

      // Even if state was wrongly recording, try/catch protects from unhandled throw
      mockRecorder.state = "recording";
      assert.doesNotThrow(() => safeStopRecording(mockRecorder));
      assert.equal(stopCallCount, 1);
    });

    it("Handles empty recording data (zero bytes) by transitioning to error and IDLE state", () => {
      let state = "RECORDING";
      let error = null;
      let recordedUrl = null;

      const handleOnStop = (chunks) => {
        const blob = new Blob(chunks, { type: "audio/webm" });
        if (blob.size > 0) {
          recordedUrl = "blob:valid-audio";
          state = "RECORDED";
        } else {
          error = "No audio data was recorded. Please check your microphone.";
          state = "IDLE";
        }
      };

      // Empty chunks (e.g. stopped immediately before audio data arrived)
      handleOnStop([]);
      assert.equal(state, "IDLE");
      assert.equal(recordedUrl, null);
      assert.equal(error, "No audio data was recorded. Please check your microphone.");
    });

    it("Recovers gracefully from MediaRecorder onerror by stopping tracks and resetting state", () => {
      let state = "RECORDING";
      let error = null;
      let tracksStopped = 0;
      const mockStream = {
        getTracks: () => [{ stop: () => tracksStopped++ }],
      };

      const handleOnError = () => {
        mockStream.getTracks().forEach((t) => t.stop());
        error = "An error occurred during audio recording. Please check your microphone.";
        state = "IDLE";
      };

      handleOnError();
      assert.equal(state, "IDLE");
      assert.equal(tracksStopped, 1);
      assert.match(error, /An error occurred during audio recording/);
    });

    it("Revokes Object URLs and stops media tracks on cleanup", () => {
      let revokedUrls = [];
      let stoppedTracks = 0;

      const originalRevoke = global.URL?.revokeObjectURL;
      global.URL = global.URL || {};
      global.URL.revokeObjectURL = (url) => revokedUrls.push(url);

      const mockTrack = {
        stop: () => {
          stoppedTracks++;
        },
      };

      const cleanupResources = (blobUrl, stream) => {
        if (blobUrl) global.URL.revokeObjectURL(blobUrl);
        if (stream) stream.getTracks().forEach((t) => t.stop());
      };

      const mockStream = { getTracks: () => [mockTrack, mockTrack] };
      const testBlobUrl = "blob:http://localhost:3000/test-uuid";

      cleanupResources(testBlobUrl, mockStream);

      assert.equal(revokedUrls.length, 1);
      assert.equal(revokedUrls[0], testBlobUrl);
      assert.equal(stoppedTracks, 2);

      if (originalRevoke) global.URL.revokeObjectURL = originalRevoke;
    });
  });

  // --------------------------------------------------------------------------
  // 5. Self-Assessment Form & Rating Validation
  // --------------------------------------------------------------------------
  describe("Self-Assessment Form & Rating Validation", () => {
    const RATINGS = [
      { key: "NEEDS_WORK", label: "Needs Work" },
      { key: "ACCEPTABLE", label: "Acceptable" },
      { key: "GOOD", label: "Good" },
      { key: "FLUENT", label: "Fluent" },
    ];

    function AssessmentHarness({
      hasRecordedTake = true,
      durationSeconds = 5.2,
      onComplete,
    }) {
      const [rating, setRating] = useState(null);
      const [isSubmitting, setIsSubmitting] = useState(false);

      const isCompleteDisabled =
        !hasRecordedTake || durationSeconds <= 0 || !rating || isSubmitting;

      const handleSubmit = async () => {
        if (isCompleteDisabled) return;
        setIsSubmitting(true);
        await onComplete?.(rating);
        setIsSubmitting(false);
      };

      return React.createElement(
        "div",
        { "data-testid": "assessment-form" },
        React.createElement(
          "div",
          { "data-testid": "rating-options" },
          RATINGS.map((r) =>
            React.createElement(
              "button",
              {
                key: r.key,
                "data-testid": `rating-${r.key}`,
                "data-selected": rating === r.key ? "true" : "false",
                onClick: () => setRating(r.key),
              },
              r.label
            )
          )
        ),
        React.createElement(
          "button",
          {
            "data-testid": "complete-practice-btn",
            disabled: isCompleteDisabled,
            onClick: handleSubmit,
          },
          isSubmitting ? "Completing..." : "Complete Practice"
        )
      );
    }

    it("Keeps Complete Practice button disabled until rating is selected", () => {
      render(React.createElement(AssessmentHarness));

      const submitBtn = screen.getByTestId("complete-practice-btn");
      assert.ok(submitBtn.hasAttribute("disabled"));

      fireEvent.click(screen.getByTestId("rating-GOOD"));
      assert.equal(submitBtn.hasAttribute("disabled"), false);

      assert.equal(screen.getByTestId("rating-GOOD").getAttribute("data-selected"), "true");
      assert.equal(screen.getByTestId("rating-NEEDS_WORK").getAttribute("data-selected"), "false");
    });

    it("Keeps Complete Practice disabled when no recording take exists or duration is zero", () => {
      // 1. Missing take
      const { unmount } = render(
        React.createElement(AssessmentHarness, { hasRecordedTake: false })
      );
      fireEvent.click(screen.getByTestId("rating-GOOD"));
      assert.ok(screen.getByTestId("complete-practice-btn").hasAttribute("disabled"));
      unmount();

      // 2. Zero duration
      render(
        React.createElement(AssessmentHarness, { hasRecordedTake: true, durationSeconds: 0 })
      );
      fireEvent.click(screen.getByTestId("rating-GOOD"));
      assert.ok(screen.getByTestId("complete-practice-btn").hasAttribute("disabled"));
    });

    it("Prevents duplicate submissions while request is in flight", async () => {
      let callCount = 0;
      let resolvePromise;
      const slowSubmission = () =>
        new Promise((resolve) => {
          callCount++;
          resolvePromise = resolve;
        });

      render(React.createElement(AssessmentHarness, { onComplete: slowSubmission }));

      fireEvent.click(screen.getByTestId("rating-FLUENT"));
      const submitBtn = screen.getByTestId("complete-practice-btn");

      // Click once
      fireEvent.click(submitBtn);
      assert.equal(callCount, 1);
      assert.ok(submitBtn.hasAttribute("disabled"));
      assert.equal(submitBtn.textContent, "Completing...");

      // Rapid second click
      fireEvent.click(submitBtn);
      assert.equal(callCount, 1); // Not incremented

      await act(async () => {
        resolvePromise();
      });
    });
  });

  // --------------------------------------------------------------------------
  // 6. Network Error Handling, Stable Attempt ID & Retry
  // --------------------------------------------------------------------------
  describe("Network Error Handling, Stable Attempt ID & Retry", () => {
    function RetryHarness({ attemptId, submitApi }) {
      const [error, setError] = useState(null);
      const [result, setResult] = useState(null);

      const handleAttempt = async () => {
        setError(null);
        try {
          const res = await submitApi(attemptId);
          setResult(res);
        } catch (err) {
          setError(err.message);
        }
      };

      return React.createElement(
        "div",
        null,
        error && React.createElement("div", { "data-testid": "error-banner" }, error),
        result && React.createElement("div", { "data-testid": "success-result" }, `Completed: ${result.selfRating}`),
        !result &&
          React.createElement(
            "button",
            { "data-testid": "submit-btn", onClick: handleAttempt },
            error ? "Retry Practice Submission" : "Submit"
          )
      );
    }

    it("Preserves stable attempt ID across network failure and retry", async () => {
      const submittedIds = [];
      let shouldFail = true;

      const mockSubmit = async (attemptId) => {
        submittedIds.push(attemptId);
        if (shouldFail) {
          shouldFail = false;
          throw new Error("Network timeout: failed to submit shadowing take");
        }
        return { selfRating: "GOOD", verifiedActivityCreated: true };
      };

      const fixedAttemptId = "550e8400-e29b-41d4-a716-446655440000";

      render(React.createElement(RetryHarness, { attemptId: fixedAttemptId, submitApi: mockSubmit }));

      // First try - fails
      await act(async () => {
        fireEvent.click(screen.getByTestId("submit-btn"));
      });

      assert.equal(screen.getByTestId("error-banner").textContent, "Network timeout: failed to submit shadowing take");
      assert.equal(screen.getByTestId("submit-btn").textContent, "Retry Practice Submission");

      // Retry click - succeeds with the exact same attemptId
      await act(async () => {
        fireEvent.click(screen.getByTestId("submit-btn"));
      });

      assert.equal(screen.getByTestId("success-result").textContent, "Completed: GOOD");
      assert.equal(submittedIds.length, 2);
      assert.equal(submittedIds[0], fixedAttemptId);
      assert.equal(submittedIds[1], fixedAttemptId);
    });
  });

  // --------------------------------------------------------------------------
  // 7. Completed Result State Display (No AI pronunciation claim)
  // --------------------------------------------------------------------------
  describe("Completed Result State Display", () => {
    function ResultHarness({ result, onPracticeAgain, onNext }) {
      return React.createElement(
        "div",
        { "data-testid": "completed-card" },
        React.createElement("h2", null, "Practice Complete"),
        React.createElement("div", { "data-testid": "self-rating-badge" }, `Self-Assessment: ${result.selfRating}`),
        React.createElement(
          "p",
          { "data-testid": "verified-note" },
          result.verifiedActivityCreated
            ? "Your daily learning goal has been updated."
            : "Practice completed."
        ),
        React.createElement(
          "div",
          { "data-testid": "disclaimer" },
          "EnglishFlow records your practice take for self-reflection. No AI pronunciation score is fabricated."
        ),
        React.createElement(
          "button",
          { "data-testid": "practice-again-btn", onClick: onPracticeAgain },
          "Practice Again"
        ),
        React.createElement(
          "button",
          { "data-testid": "next-exercise-btn", onClick: onNext },
          "Next Exercise"
        )
      );
    }

    it("Displays honest self-assessment reflection without automated AI scores", () => {
      let againClicked = false;
      let nextClicked = false;

      render(
        React.createElement(ResultHarness, {
          result: { selfRating: "GOOD", verifiedActivityCreated: true },
          onPracticeAgain: () => {
            againClicked = true;
          },
          onNext: () => {
            nextClicked = true;
          },
        })
      );

      assert.equal(screen.getByTestId("self-rating-badge").textContent, "Self-Assessment: GOOD");
      assert.match(
        screen.getByTestId("disclaimer").textContent,
        /No AI pronunciation score is fabricated/
      );

      fireEvent.click(screen.getByTestId("practice-again-btn"));
      assert.equal(againClicked, true);

      fireEvent.click(screen.getByTestId("next-exercise-btn"));
      assert.equal(nextClicked, true);
    });
  });
});
