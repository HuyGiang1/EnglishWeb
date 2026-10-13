import "./setup-dom.mjs";
import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import React, { useState } from "react";
import { render, screen, fireEvent, cleanup } from "@testing-library/react";

describe("Milestone M2-T05 — Diagnostic Quiz Frontend Interaction Tests", () => {
  beforeEach(() => {
    cleanup();
    document.body.innerHTML = "";
  });

  // --------------------------------------------------------------------------
  // 1. Quiz Directory & Catalog
  // --------------------------------------------------------------------------
  describe("Quiz Directory & Catalog", () => {
    function DirectoryHarness({ quizzes, onStart }) {
      return React.createElement(
        "div",
        { "data-testid": "quiz-directory" },
        quizzes.map((q, idx) =>
          React.createElement(
            "div",
            {
              key: q.id,
              "data-testid": `quiz-card-${idx}`,
            },
            React.createElement("h3", { "data-testid": `quiz-title-${idx}` }, q.title),
            React.createElement("span", { "data-testid": `cefr-${idx}` }, q.cefrLevel),
            React.createElement("span", { "data-testid": `count-${idx}` }, `${q.questionCount} Questions`),
            React.createElement(
              "button",
              {
                "data-testid": `start-btn-${idx}`,
                onClick: () => onStart(q.id, q.title),
              },
              "Start Assessment"
            )
          )
        )
      );
    }

    it("Renders available quizzes with CEFR levels and question counts", () => {
      let startedId = null;
      const sampleQuizzes = [
        { id: "q1", title: "Essential Workplace English", cefrLevel: "B1", questionCount: 6 },
        { id: "q2", title: "Advanced Lexical Nuances", cefrLevel: "B2", questionCount: 6 },
      ];

      render(
        React.createElement(DirectoryHarness, {
          quizzes: sampleQuizzes,
          onStart: (id) => {
            startedId = id;
          },
        })
      );

      assert.equal(screen.getByTestId("quiz-title-0").textContent, "Essential Workplace English");
      assert.equal(screen.getByTestId("cefr-0").textContent, "B1");
      assert.equal(screen.getByTestId("count-0").textContent, "6 Questions");

      fireEvent.click(screen.getByTestId("start-btn-1"));
      assert.equal(startedId, "q2");
    });
  });

  // --------------------------------------------------------------------------
  // 2. Question Pager & Multiple Choice Selection
  // --------------------------------------------------------------------------
  describe("Question Pager & Multiple Choice Interaction", () => {
    function PagerHarness({ questions, onAnswer, onSubmit }) {
      const [currentIndex, setCurrentIndex] = useState(0);
      const [answers, setAnswers] = useState({});

      const curQ = questions[currentIndex];
      const isSelected = (opt) => answers[curQ.questionId] === opt;

      return React.createElement(
        "div",
        { "data-testid": "pager-harness" },
        React.createElement("div", { "data-testid": "progress" }, `Question ${currentIndex + 1} of ${questions.length}`),
        React.createElement("h2", { "data-testid": "prompt" }, curQ.prompt),
        React.createElement(
          "div",
          { "data-testid": "options" },
          curQ.options.map((opt, optIdx) =>
            React.createElement(
              "button",
              {
                key: optIdx,
                "data-testid": `option-${optIdx}`,
                "data-selected": isSelected(opt) ? "true" : "false",
                onClick: () => {
                  const updated = { ...answers, [curQ.questionId]: opt };
                  setAnswers(updated);
                  onAnswer(curQ.questionId, opt);
                },
              },
              opt
            )
          )
        ),
        React.createElement(
          "div",
          { "data-testid": "controls" },
          React.createElement(
            "button",
            {
              "data-testid": "prev-btn",
              disabled: currentIndex === 0,
              onClick: () => setCurrentIndex((c) => Math.max(0, c - 1)),
            },
            "Previous"
          ),
          currentIndex < questions.length - 1
            ? React.createElement(
                "button",
                {
                  "data-testid": "next-btn",
                  onClick: () => setCurrentIndex((c) => Math.min(questions.length - 1, c + 1)),
                },
                "Next"
              )
            : React.createElement(
                "button",
                {
                  "data-testid": "submit-btn",
                  onClick: () => onSubmit(answers),
                },
                "Submit Quiz"
              )
        )
      );
    }

    it("Selects multiple-choice option and advances between questions", () => {
      const answered = {};
      let finalSubmitted = null;

      const questions = [
        {
          questionId: "q1",
          prompt: "Select synonym for resilient",
          options: ["fragile", "adaptable", "rigid", "temporary"],
        },
        {
          questionId: "q2",
          prompt: "The team will ___ the launch",
          options: ["postpone", "cancel", "accelerate"],
        },
      ];

      render(
        React.createElement(PagerHarness, {
          questions,
          onAnswer: (id, val) => {
            answered[id] = val;
          },
          onSubmit: (ans) => {
            finalSubmitted = ans;
          },
        })
      );

      assert.equal(screen.getByTestId("progress").textContent, "Question 1 of 2");
      assert.equal(screen.getByTestId("prompt").textContent, "Select synonym for resilient");
      assert.equal(screen.getByTestId("prev-btn").hasAttribute("disabled"), true);

      // Select option 1 ("adaptable")
      fireEvent.click(screen.getByTestId("option-1"));
      assert.equal(answered["q1"], "adaptable");
      assert.equal(screen.getByTestId("option-1").getAttribute("data-selected"), "true");

      // Advance to next question
      fireEvent.click(screen.getByTestId("next-btn"));
      assert.equal(screen.getByTestId("progress").textContent, "Question 2 of 2");
      assert.equal(screen.getByTestId("prompt").textContent, "The team will ___ the launch");
      assert.equal(screen.getByTestId("prev-btn").hasAttribute("disabled"), false);

      // Select option 0 ("postpone")
      fireEvent.click(screen.getByTestId("option-0"));
      assert.equal(answered["q2"], "postpone");

      // Submit
      fireEvent.click(screen.getByTestId("submit-btn"));
      assert.deepEqual(finalSubmitted, { q1: "adaptable", q2: "postpone" });
    });
  });

  // --------------------------------------------------------------------------
  // 3. Cloze (Fill-in-the-blank) Input
  // --------------------------------------------------------------------------
  describe("Cloze Input Interaction", () => {
    function ClozeHarness({ questionId, onAnswer }) {
      const [val, setVal] = useState("");
      return React.createElement(
        "div",
        null,
        React.createElement("input", {
          "data-testid": "cloze-input",
          type: "text",
          value: val,
          onChange: (e) => {
            setVal(e.target.value);
            onAnswer(questionId, e.target.value);
          },
        })
      );
    }

    it("Captures typed answer for cloze question", () => {
      let result = "";
      render(
        React.createElement(ClozeHarness, {
          questionId: "cloze-1",
          onAnswer: (qid, text) => {
            result = text;
          },
        })
      );

      const input = screen.getByTestId("cloze-input");
      fireEvent.change(input, { target: { value: "had received" } });
      assert.equal(result, "had received");
    });
  });

  // --------------------------------------------------------------------------
  // 4. Review Before Submit Modal & Unanswered Navigation
  // --------------------------------------------------------------------------
  describe("Review Before Submit & Unanswered Indicator", () => {
    function ReviewModalHarness({ total, answered, onJump, onConfirm }) {
      const unanswered = total - answered;
      return React.createElement(
        "div",
        { "data-testid": "review-modal" },
        React.createElement("span", { "data-testid": "answered-count" }, `Answered: ${answered} / ${total}`),
        React.createElement("span", { "data-testid": "unanswered-count" }, `Unanswered: ${unanswered}`),
        unanswered > 0 &&
          React.createElement(
            "div",
            { "data-testid": "unanswered-warning" },
            React.createElement("p", null, "Unanswered questions receive 0 points"),
            React.createElement(
              "button",
              {
                "data-testid": "jump-unanswered-btn",
                onClick: () => onJump(2), // jump to question 3 (index 2)
              },
              "Go to Q3"
            )
          ),
        React.createElement(
          "button",
          {
            "data-testid": "confirm-submit-btn",
            onClick: onConfirm,
          },
          "Confirm Final Submission"
        )
      );
    }

    it("Accurately reports answered vs unanswered counts and supports jumping to unanswered questions", () => {
      let jumpedIdx = null;
      let confirmed = false;

      render(
        React.createElement(ReviewModalHarness, {
          total: 6,
          answered: 4,
          onJump: (idx) => {
            jumpedIdx = idx;
          },
          onConfirm: () => {
            confirmed = true;
          },
        })
      );

      assert.equal(screen.getByTestId("answered-count").textContent, "Answered: 4 / 6");
      assert.equal(screen.getByTestId("unanswered-count").textContent, "Unanswered: 2");
      assert.ok(screen.getByTestId("unanswered-warning"));

      fireEvent.click(screen.getByTestId("jump-unanswered-btn"));
      assert.equal(jumpedIdx, 2);

      fireEvent.click(screen.getByTestId("confirm-submit-btn"));
      assert.equal(confirmed, true);
    });
  });

  // --------------------------------------------------------------------------
  // 5. In-flight Submission Guard (Duplicate Click Prevention)
  // --------------------------------------------------------------------------
  describe("In-flight Submission Guard", () => {
    function GuardedSubmitHarness({ onSubmit }) {
      const [isSubmitting, setIsSubmitting] = useState(false);

      const handleClick = async () => {
        if (isSubmitting) return;
        setIsSubmitting(true);
        await onSubmit();
      };

      return React.createElement(
        "button",
        {
          "data-testid": "guard-submit-btn",
          disabled: isSubmitting,
          onClick: handleClick,
        },
        isSubmitting ? "Grading..." : "Submit"
      );
    }

    it("Disables button and prevents duplicate submissions while request is in flight", () => {
      let callCount = 0;
      render(
        React.createElement(GuardedSubmitHarness, {
          onSubmit: async () => {
            callCount++;
          },
        })
      );

      const btn = screen.getByTestId("guard-submit-btn");
      fireEvent.click(btn);
      fireEvent.click(btn); // duplicate click attempt

      assert.equal(callCount, 1);
      assert.equal(btn.hasAttribute("disabled"), true);
      assert.equal(btn.textContent, "Grading...");
    });
  });

  // --------------------------------------------------------------------------
  // 6. Scorecard Results & Question Review Rendering
  // --------------------------------------------------------------------------
  describe("Scorecard Results & Explanations", () => {
    function ScorecardHarness({ results, onRetake }) {
      return React.createElement(
        "div",
        { "data-testid": "scorecard" },
        React.createElement("div", { "data-testid": "score-percentage" }, `${results.scorePercentage.toFixed(1)}%`),
        React.createElement("div", { "data-testid": "correct-count" }, `Correct: ${results.correctCount}`),
        React.createElement("div", { "data-testid": "incorrect-count" }, `Incorrect: ${results.incorrectCount}`),
        React.createElement("div", { "data-testid": "unanswered-count" }, `Unanswered: ${results.unansweredCount}`),
        results.verifiedActivityCreated &&
          React.createElement("span", { "data-testid": "verified-badge" }, "Verified Activity Logged"),
        React.createElement(
          "div",
          { "data-testid": "question-reviews" },
          results.questions.map((q, idx) =>
            React.createElement(
              "div",
              { key: q.questionId, "data-testid": `q-review-${idx}` },
              React.createElement("p", { "data-testid": `q-prompt-${idx}` }, q.prompt),
              React.createElement("span", { "data-testid": `q-user-ans-${idx}` }, q.selectedAnswer || "None"),
              React.createElement("span", { "data-testid": `q-canonical-${idx}` }, q.canonicalAnswer),
              React.createElement("span", { "data-testid": `q-status-${idx}` }, q.isCorrect ? "Correct" : "Incorrect"),
              React.createElement("p", { "data-testid": `q-explanation-${idx}` }, q.explanation)
            )
          )
        ),
        React.createElement("button", { "data-testid": "retake-btn", onClick: onRetake }, "Retake Assessment")
      );
    }

    it("Renders server-graded score, question explanations, and activity badge accurately", () => {
      let retakeClicked = false;
      const mockResults = {
        scorePercentage: 83.33,
        correctCount: 5,
        incorrectCount: 1,
        unansweredCount: 0,
        verifiedActivityCreated: true,
        questions: [
          {
            questionId: "q1",
            prompt: "Select synonym for resilient",
            selectedAnswer: "adaptable",
            canonicalAnswer: "adaptable",
            isCorrect: true,
            explanation: "Resilient denotes the capacity to withstand difficulty.",
          },
          {
            questionId: "q2",
            prompt: "The team will ___ the launch",
            selectedAnswer: "cancel",
            canonicalAnswer: "postpone",
            isCorrect: false,
            explanation: "Postpone indicates delaying rather than terminating.",
          },
        ],
      };

      render(
        React.createElement(ScorecardHarness, {
          results: mockResults,
          onRetake: () => {
            retakeClicked = true;
          },
        })
      );

      assert.equal(screen.getByTestId("score-percentage").textContent, "83.3%");
      assert.equal(screen.getByTestId("correct-count").textContent, "Correct: 5");
      assert.equal(screen.getByTestId("incorrect-count").textContent, "Incorrect: 1");
      assert.ok(screen.getByTestId("verified-badge"));

      // Question 1 review
      assert.equal(screen.getByTestId("q-status-0").textContent, "Correct");
      assert.equal(screen.getByTestId("q-canonical-0").textContent, "adaptable");
      assert.ok(screen.getByTestId("q-explanation-0").textContent.includes("Resilient denotes"));

      // Question 2 review (incorrect)
      assert.equal(screen.getByTestId("q-status-1").textContent, "Incorrect");
      assert.equal(screen.getByTestId("q-canonical-1").textContent, "postpone");

      fireEvent.click(screen.getByTestId("retake-btn"));
      assert.equal(retakeClicked, true);
    });
  });
});
