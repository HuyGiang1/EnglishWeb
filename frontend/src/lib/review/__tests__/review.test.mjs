import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  initReviewSession,
  revealCard,
  isShortcutAllowed,
  resolveKeyboardAction,
  startGradeSubmission,
  completeGradeSubmission,
  failGradeSubmission,
  prepareRetrySubmission,
} from "../review-session.mjs";

const sampleCards = [
  {
    srsItemId: "srs-1",
    vocabularyId: "vocab-1",
    term: "resilience",
    partOfSpeech: "noun",
    phoneticIpa: "/rɪˈzɪl.jəns/",
    vietnameseMeaning: "sự kiên cường",
    englishDefinition: "The capacity to recover quickly.",
    exampleSentence: "Her resilience was inspiring.",
    intervalDays: 1,
    easeFactor: 2.5,
  },
  {
    srsItemId: "srs-2",
    vocabularyId: "vocab-2",
    term: "ephemeral",
    partOfSpeech: "adjective",
    phoneticIpa: "/ɪˈfem.ər.əl/",
    vietnameseMeaning: "phù du",
    englishDefinition: "Lasting for a very short time.",
    exampleSentence: "Fame can be ephemeral.",
    intervalDays: 3,
    easeFactor: 2.4,
  },
];

describe("Flashcard Review Session State Machine & Interaction Tests", () => {
  it("Initializes review session with cards hidden and a stable initial attempt_id", () => {
    const attemptId = "attempt-initial-001";
    const session = initReviewSession(sampleCards, attemptId);

    assert.equal(session.cards.length, 2);
    assert.equal(session.currentIndex, 0);
    assert.equal(session.isRevealed, false);
    assert.equal(session.isSubmitting, false);
    assert.equal(session.submitError, null);
    assert.equal(session.currentAttemptId, attemptId);
    assert.equal(session.results.length, 0);
  });

  it("Reveals answer when revealCard is called", () => {
    const session = initReviewSession(sampleCards, "attempt-001");
    assert.equal(session.isRevealed, false);

    const revealed = revealCard(session);
    assert.equal(revealed.isRevealed, true);
    assert.equal(revealed.currentIndex, 0);
  });

  it("Input guard: prevents keyboard shortcuts when typing in INPUT or TEXTAREA", () => {
    assert.equal(isShortcutAllowed("INPUT"), false);
    assert.equal(isShortcutAllowed("input"), false);
    assert.equal(isShortcutAllowed("TEXTAREA"), false);
    assert.equal(isShortcutAllowed("SELECT"), false);
    assert.equal(isShortcutAllowed("DIV", true), false); // contentEditable
    assert.equal(isShortcutAllowed("DIV", false), true);
    assert.equal(isShortcutAllowed("BODY"), true);
  });

  it("Keyboard shortcut resolution: Space/Enter reveals when hidden; 1..4 grades when revealed", () => {
    // When hidden:
    assert.deepEqual(resolveKeyboardAction(" ", false, "BODY"), { action: "REVEAL" });
    assert.deepEqual(resolveKeyboardAction("Enter", false, "BODY"), { action: "REVEAL" });
    assert.deepEqual(resolveKeyboardAction("1", false, "BODY"), { action: "IGNORE" });

    // When inside an INPUT:
    assert.deepEqual(resolveKeyboardAction(" ", false, "INPUT"), { action: "IGNORE" });
    assert.deepEqual(resolveKeyboardAction("1", true, "TEXTAREA"), { action: "IGNORE" });

    // When revealed:
    assert.deepEqual(resolveKeyboardAction("1", true, "BODY"), { action: "GRADE", grade: "AGAIN" });
    assert.deepEqual(resolveKeyboardAction("2", true, "BODY"), { action: "GRADE", grade: "HARD" });
    assert.deepEqual(resolveKeyboardAction("3", true, "BODY"), { action: "GRADE", grade: "GOOD" });
    assert.deepEqual(resolveKeyboardAction("4", true, "BODY"), { action: "GRADE", grade: "EASY" });
    assert.deepEqual(resolveKeyboardAction("x", true, "BODY"), { action: "IGNORE" });
  });

  it("Duplicate-click prevention: rejects secondary grade action while in flight", () => {
    const session = revealCard(initReviewSession(sampleCards, "attempt-dup-1"));
    const { newState, payload } = startGradeSubmission(session, "GOOD", 10);

    assert.equal(newState.isSubmitting, true);
    assert.ok(payload);
    assert.equal(payload.attemptId, "attempt-dup-1");
    assert.equal(payload.grade, "GOOD");

    // Second click while isSubmitting is true
    const secondClick = startGradeSubmission(newState, "GOOD", 10);
    assert.equal(secondClick.payload, null, "Payload should be null to prevent duplicate submission");
  });

  it("Retry reusing the EXACT same attempt_id after network failure", () => {
    const originalAttemptId = "attempt-network-fail-1";
    const session = revealCard(initReviewSession(sampleCards, originalAttemptId));

    // Submit starts
    const { newState: inFlightState } = startGradeSubmission(session, "HARD", 5);

    // Network fails
    const failedState = failGradeSubmission(inFlightState, "Network connection timed out");
    assert.equal(failedState.isSubmitting, false);
    assert.equal(failedState.submitError, "Network connection timed out");
    assert.equal(failedState.currentAttemptId, originalAttemptId);
    assert.equal(failedState.lastGradeAttempted, "HARD");

    // Prepare retry
    const retry = prepareRetrySubmission(failedState);
    assert.ok(retry.payload);
    assert.equal(retry.payload.attemptId, originalAttemptId, "Retry MUST use the original attempt_id");
    assert.equal(retry.payload.grade, "HARD");
    assert.equal(retry.newState.isSubmitting, true);
  });

  it("Advancing after success generates a fresh distinct attempt_id for the next card", () => {
    const firstAttemptId = "attempt-card-1";
    const secondAttemptId = "attempt-card-2";

    const session = revealCard(initReviewSession(sampleCards, firstAttemptId));
    const { newState: inFlightState } = startGradeSubmission(session, "GOOD", 8);

    const mockResponse = {
      srsItemId: "srs-1",
      attemptId: firstAttemptId,
      newIntervalDays: 6,
      newEaseFactor: 2.5,
      dueDate: "2026-10-18",
      nextReviewLabel: "6 days",
    };

    const completedState = completeGradeSubmission(inFlightState, mockResponse, secondAttemptId);

    assert.equal(completedState.currentIndex, 1);
    assert.equal(completedState.isRevealed, false);
    assert.equal(completedState.isSubmitting, false);
    assert.equal(completedState.currentAttemptId, secondAttemptId, "New card must have fresh attempt_id");
    assert.notEqual(completedState.currentAttemptId, firstAttemptId);

    // Verified outcome recorded in session summary
    assert.equal(completedState.results.length, 1);
    assert.equal(completedState.results[0].term, "resilience");
    assert.equal(completedState.results[0].grade, "GOOD");
    assert.equal(completedState.results[0].newIntervalDays, 6);
  });

  it("Session completion state: recognizes when all cards in queue have been reviewed", () => {
    let state = initReviewSession(sampleCards, "att-1");

    // Review card 1
    state = revealCard(state);
    state = startGradeSubmission(state, "GOOD", 5).newState;
    state = completeGradeSubmission(state, { srsItemId: "srs-1", newIntervalDays: 6 }, "att-2");

    // Review card 2
    state = revealCard(state);
    state = startGradeSubmission(state, "EASY", 3).newState;
    state = completeGradeSubmission(state, { srsItemId: "srs-2", newIntervalDays: 8 }, "att-3");

    // All 2 cards reviewed
    assert.equal(state.currentIndex, 2);
    assert.equal(state.currentIndex >= state.cards.length, true, "Session should be complete");
    assert.equal(state.results.length, 2);
  });
});
