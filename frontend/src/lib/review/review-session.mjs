/**
 * Pure review session state-machine functions and interaction rules.
 */

export function initReviewSession(cards, initialAttemptId) {
  return {
    cards,
    currentIndex: 0,
    isRevealed: false,
    isSubmitting: false,
    submitError: null,
    currentAttemptId: initialAttemptId,
    lastGradeAttempted: null,
    results: [],
  };
}

export function revealCard(state) {
  if (state.isRevealed || state.cards.length === 0 || state.currentIndex >= state.cards.length) {
    return state;
  }
  return {
    ...state,
    isRevealed: true,
  };
}

export function isShortcutAllowed(targetTagName, isContentEditable) {
  if (!targetTagName) return true;
  const tag = targetTagName.toUpperCase();
  if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") {
    return false;
  }
  return !isContentEditable;
}

export function resolveKeyboardAction(key, isRevealed, targetTagName, isContentEditable) {
  if (!isShortcutAllowed(targetTagName, isContentEditable)) {
    return { action: "IGNORE" };
  }

  if (!isRevealed) {
    if (key === " " || key === "Space" || key === "Enter") {
      return { action: "REVEAL" };
    }
    return { action: "IGNORE" };
  }

  switch (key) {
    case "1":
      return { action: "GRADE", grade: "AGAIN" };
    case "2":
      return { action: "GRADE", grade: "HARD" };
    case "3":
      return { action: "GRADE", grade: "GOOD" };
    case "4":
      return { action: "GRADE", grade: "EASY" };
    default:
      return { action: "IGNORE" };
  }
}

export function startGradeSubmission(state, grade, elapsedSeconds) {
  if (state.isSubmitting) {
    return { newState: state, payload: null };
  }

  const currentCard = state.cards[state.currentIndex];
  if (!currentCard) {
    return { newState: state, payload: null };
  }

  const payload = {
    srsItemId: currentCard.srsItemId,
    attemptId: state.currentAttemptId,
    grade,
    durationSeconds: Math.max(1, elapsedSeconds),
  };

  const newState = {
    ...state,
    isSubmitting: true,
    submitError: null,
    lastGradeAttempted: grade,
  };

  return { newState, payload };
}

export function completeGradeSubmission(state, response, freshAttemptId) {
  const currentCard = state.cards[state.currentIndex];
  const term = currentCard ? currentCard.term : "unknown";

  const updatedResults = [
    ...state.results,
    {
      term,
      grade: state.lastGradeAttempted || "GOOD",
      newIntervalDays: response.newIntervalDays,
    },
  ];

  return {
    ...state,
    currentIndex: state.currentIndex + 1,
    isRevealed: false,
    isSubmitting: false,
    submitError: null,
    currentAttemptId: freshAttemptId,
    lastGradeAttempted: null,
    results: updatedResults,
  };
}

export function failGradeSubmission(state, errorMessage) {
  return {
    ...state,
    isSubmitting: false,
    submitError: errorMessage,
  };
}

export function prepareRetrySubmission(state) {
  if (!state.lastGradeAttempted || state.isSubmitting) {
    return { newState: state, payload: null };
  }

  return startGradeSubmission(state, state.lastGradeAttempted, 1);
}
