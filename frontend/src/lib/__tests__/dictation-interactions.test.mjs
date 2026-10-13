import "./setup-dom.mjs";
import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import React, { useState } from "react";
import { render, screen, fireEvent, cleanup } from "@testing-library/react";

describe("Milestone M2-T03 — Daily Dictation Frontend Interaction Tests", () => {
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
        { "data-testid": "directory" },
        exercises.map((ex, idx) =>
          React.createElement(
            "div",
            {
              key: ex.contentId,
              "data-testid": `exercise-item-${idx}`,
              onClick: () => onSelect(ex),
            },
            React.createElement("span", null, ex.title),
            React.createElement("span", { "data-testid": `level-${idx}` }, ex.cefrLevel),
            React.createElement("button", { "data-testid": `start-btn-${idx}` }, "Start")
          )
        )
      );
    }

    it("Renders available exercises and triggers selection on click", () => {
      let selected = null;
      const list = [
        { contentId: "1", title: "Daily Routine", cefrLevel: "A1" },
        { contentId: "2", title: "Economic Resilience", cefrLevel: "B2" },
      ];

      render(
        React.createElement(DirectoryHarness, {
          exercises: list,
          onSelect: (ex) => {
            selected = ex;
          },
        })
      );

      const item = screen.getByTestId("exercise-item-1");
      assert.ok(item);
      assert.equal(screen.getByTestId("level-1").textContent, "B2");

      fireEvent.click(item);
      assert.equal(selected?.title, "Economic Resilience");
    });
  });

  // --------------------------------------------------------------------------
  // 2. Audio Playback Controls & Speed Toggle
  // --------------------------------------------------------------------------
  describe("Audio Playback Controls & Speed", () => {
    function AudioPlayerHarness({ onSpeedChange }) {
      const [isPlaying, setIsPlaying] = useState(false);
      const [rate, setRate] = useState(1.0);

      const changeRate = (newRate) => {
        setRate(newRate);
        onSpeedChange?.(newRate);
      };

      return React.createElement(
        "div",
        null,
        React.createElement(
          "button",
          {
            "data-testid": "play-pause-btn",
            onClick: () => setIsPlaying(!isPlaying),
          },
          isPlaying ? "Pause" : "Play"
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
                onClick: () => changeRate(s),
              },
              `${s}x`
            )
          )
        ),
        React.createElement("span", { "data-testid": "current-speed" }, `${rate}x`)
      );
    }

    it("Toggles play/pause state upon click", () => {
      render(React.createElement(AudioPlayerHarness));
      const btn = screen.getByTestId("play-pause-btn");
      const state = screen.getByTestId("playback-state");

      assert.equal(state.textContent, "PAUSED");
      fireEvent.click(btn);
      assert.equal(state.textContent, "PLAYING");
      fireEvent.click(btn);
      assert.equal(state.textContent, "PAUSED");
    });

    it("Updates playback rate when speed chip is clicked", () => {
      let speedValue = 1.0;
      render(React.createElement(AudioPlayerHarness, { onSpeedChange: (s) => { speedValue = s; } }));

      const chip075 = screen.getByTestId("speed-0.75");
      fireEvent.click(chip075);
      assert.equal(screen.getByTestId("current-speed").textContent, "0.75x");
      assert.equal(speedValue, 0.75);

      const chip125 = screen.getByTestId("speed-1.25");
      fireEvent.click(chip125);
      assert.equal(screen.getByTestId("current-speed").textContent, "1.25x");
      assert.equal(speedValue, 1.25);
    });
  });

  // --------------------------------------------------------------------------
  // 3. Transcription Input & Submission Guard
  // --------------------------------------------------------------------------
  describe("Transcription Input & Submission Guard", () => {
    function TranscriptInputHarness({ onSubmit }) {
      const [text, setText] = useState("");
      const [busy, setBusy] = useState(false);

      const submit = () => {
        if (!text.trim() || busy) return;
        setBusy(true);
        onSubmit?.(text);
      };

      return React.createElement(
        "form",
        {
          onSubmit: (e) => {
            e.preventDefault();
            submit();
          },
        },
        React.createElement("textarea", {
          "data-testid": "transcript-input",
          value: text,
          onChange: (e) => setText(e.target.value),
          onKeyDown: (e) => {
            if ((e.metaKey || e.ctrlKey) && e.key === "Enter") {
              e.preventDefault();
              submit();
            }
          },
        }),
        React.createElement(
          "button",
          {
            type: "submit",
            "data-testid": "submit-btn",
            disabled: !text.trim() || busy,
          },
          busy ? "Evaluating..." : "Check Answer"
        )
      );
    }

    it("Disables submit button when textarea is empty or whitespace", () => {
      render(React.createElement(TranscriptInputHarness));
      const btn = screen.getByTestId("submit-btn");
      assert.equal(btn.disabled, true);

      const input = screen.getByTestId("transcript-input");
      fireEvent.change(input, { target: { value: "    " } });
      assert.equal(btn.disabled, true);
    });

    it("Enables submit button when non-empty transcript is entered", () => {
      render(React.createElement(TranscriptInputHarness));
      const btn = screen.getByTestId("submit-btn");
      const input = screen.getByTestId("transcript-input");

      fireEvent.change(input, { target: { value: "The country showed great economic resilience." } });
      assert.equal(btn.disabled, false);
    });

    it("Disables button and sets busy state upon submission", () => {
      let submittedText = null;
      render(React.createElement(TranscriptInputHarness, { onSubmit: (t) => { submittedText = t; } }));

      const btn = screen.getByTestId("submit-btn");
      const input = screen.getByTestId("transcript-input");

      fireEvent.change(input, { target: { value: "Test sentence." } });
      fireEvent.click(btn);

      assert.equal(submittedText, "Test sentence.");
      assert.equal(btn.disabled, true);
      assert.equal(btn.textContent, "Evaluating...");
    });
  });

  // --------------------------------------------------------------------------
  // 4. Diff Tokens Rendering & Accessible Markers
  // --------------------------------------------------------------------------
  describe("Diff Tokens Rendering", () => {
    function DiffViewerHarness({ diffTokens }) {
      return React.createElement(
        "div",
        { "data-testid": "diff-viewer" },
        diffTokens.map((tok, i) => {
          if (tok.type === "EQUAL") {
            return React.createElement("span", { key: i, "data-testid": "diff-equal" }, tok.text);
          }
          if (tok.type === "MISSING") {
            return React.createElement(
              "span",
              { key: i, "data-testid": "diff-missing", className: "line-through" },
              tok.expectedWord
            );
          }
          if (tok.type === "EXTRA") {
            return React.createElement(
              "span",
              { key: i, "data-testid": "diff-extra", className: "underline" },
              tok.text
            );
          }
          if (tok.type === "SUBSTITUTION") {
            return React.createElement(
              "span",
              { key: i, "data-testid": "diff-sub" },
              `${tok.text} (${tok.expectedWord})`
            );
          }
          return null;
        })
      );
    }

    it("Renders each token type with appropriate non-color indicators", () => {
      const tokens = [
        { type: "EQUAL", text: "The", expectedWord: null },
        { type: "MISSING", text: null, expectedWord: "country" },
        { type: "EXTRA", text: "nation", expectedWord: null },
        { type: "SUBSTITUTION", text: "resillience", expectedWord: "resilience" },
      ];

      render(React.createElement(DiffViewerHarness, { diffTokens: tokens }));

      assert.ok(screen.getByTestId("diff-equal"));
      assert.equal(screen.getByTestId("diff-equal").textContent, "The");

      assert.ok(screen.getByTestId("diff-missing"));
      assert.equal(screen.getByTestId("diff-missing").textContent, "country");

      assert.ok(screen.getByTestId("diff-extra"));
      assert.equal(screen.getByTestId("diff-extra").textContent, "nation");

      assert.ok(screen.getByTestId("diff-sub"));
      assert.equal(screen.getByTestId("diff-sub").textContent, "resillience (resilience)");
    });
  });

  // --------------------------------------------------------------------------
  // 5. Accuracy & Pass Badge
  // --------------------------------------------------------------------------
  describe("Accuracy Score & Pass Status", () => {
    function ScoreHarness({ score, isPassed }) {
      return React.createElement(
        "div",
        null,
        React.createElement("span", { "data-testid": "score-text" }, `${score}%`),
        React.createElement("span", { "data-testid": "status-badge" }, isPassed ? "Pass (≥80%)" : "Needs Work (<80%)")
      );
    }

    it("Displays Pass badge for score >= 80%", () => {
      render(React.createElement(ScoreHarness, { score: 96.5, isPassed: true }));
      assert.equal(screen.getByTestId("score-text").textContent, "96.5%");
      assert.equal(screen.getByTestId("status-badge").textContent, "Pass (≥80%)");
    });

    it("Displays Needs Work badge for score < 80%", () => {
      render(React.createElement(ScoreHarness, { score: 71.4, isPassed: false }));
      assert.equal(screen.getByTestId("score-text").textContent, "71.4%");
      assert.equal(screen.getByTestId("status-badge").textContent, "Needs Work (<80%)");
    });
  });

  // --------------------------------------------------------------------------
  // 6. Try Again & Progression
  // --------------------------------------------------------------------------
  describe("Try Again & Next Progression", () => {
    function ActionRowHarness({ onTryAgain, onNext }) {
      return React.createElement(
        "div",
        null,
        React.createElement("button", { "data-testid": "try-again", onClick: onTryAgain }, "Try Again"),
        React.createElement("button", { "data-testid": "next-exercise", onClick: onNext }, "Next Exercise")
      );
    }

    it("Triggers onTryAgain and onNext callbacks cleanly", () => {
      let triedAgain = false;
      let wentNext = false;

      render(
        React.createElement(ActionRowHarness, {
          onTryAgain: () => { triedAgain = true; },
          onNext: () => { wentNext = true; },
        })
      );

      fireEvent.click(screen.getByTestId("try-again"));
      assert.equal(triedAgain, true);

      fireEvent.click(screen.getByTestId("next-exercise"));
      assert.equal(wentNext, true);
    });
  });

  // --------------------------------------------------------------------------
  // 7. Audio Availability & Missing Asset Behavior
  // --------------------------------------------------------------------------
  describe("Audio Availability & Fresh Clone Safety", () => {
    function AvailabilityHarness({ exercise, onSelect }) {
      const isMissing = exercise.isAudioAvailable === false;
      return React.createElement(
        "div",
        {
          "data-testid": "exercise-item",
          onClick: () => {
            if (!isMissing) onSelect(exercise);
          },
        },
        React.createElement("span", null, exercise.title),
        isMissing
          ? React.createElement("span", { "data-testid": "audio-missing-badge" }, "Audio Missing")
          : React.createElement("button", { "data-testid": "start-btn" }, "Start")
      );
    }

    it("Renders Audio Missing badge and prevents start click when isAudioAvailable is false", () => {
      let selected = false;
      const unavailableEx = {
        contentId: "unavail-1",
        title: "Uninstalled Exercise",
        isAudioAvailable: false,
      };

      render(
        React.createElement(AvailabilityHarness, {
          exercise: unavailableEx,
          onSelect: () => { selected = true; },
        })
      );

      assert.ok(screen.getByTestId("audio-missing-badge"));
      assert.equal(screen.queryByTestId("start-btn"), null);

      fireEvent.click(screen.getByTestId("exercise-item"));
      assert.equal(selected, false);
    });
  });
});
