import type { ReviewCard, ReviewRating, ReviewSubmissionPayload, ReviewSubmissionResponse } from "../api/review";

export interface SessionResultItem {
  term: string;
  grade: ReviewRating;
  newIntervalDays: number;
}

export interface ReviewSessionState {
  cards: ReviewCard[];
  currentIndex: number;
  isRevealed: boolean;
  isSubmitting: boolean;
  submitError: string | null;
  currentAttemptId: string;
  lastGradeAttempted: ReviewRating | null;
  results: SessionResultItem[];
}

export function initReviewSession(cards: ReviewCard[], initialAttemptId: string): ReviewSessionState {
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

export function revealCard(state: ReviewSessionState): ReviewSessionState {
  if (state.isRevealed || state.cards.length === 0 || state.currentIndex >= state.cards.length) {
    return state;
  }
  return {
    ...state,
    isRevealed: true,
  };
}

export function isShortcutAllowed(targetTagName?: string, isContentEditable?: boolean): boolean {
  if (!targetTagName) return true;
  const tag = targetTagName.toUpperCase();
  if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") {
    return false;
  }
  return !isContentEditable;
}

export function resolveKeyboardAction(
  key: string,
  isRevealed: boolean,
  targetTagName?: string,
  isContentEditable?: boolean
): { action: "REVEAL" | "GRADE" | "IGNORE"; grade?: ReviewRating } {
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

export function startGradeSubmission(
  state: ReviewSessionState,
  grade: ReviewRating,
  elapsedSeconds: number
): { newState: ReviewSessionState; payload: ReviewSubmissionPayload | null } {
  if (state.isSubmitting) {
    // Duplicate click prevented
    return { newState: state, payload: null };
  }

  const currentCard = state.cards[state.currentIndex];
  if (!currentCard) {
    return { newState: state, payload: null };
  }

  const payload: ReviewSubmissionPayload = {
    srsItemId: currentCard.srsItemId,
    attemptId: state.currentAttemptId,
    grade,
    durationSeconds: Math.max(1, elapsedSeconds),
  };

  const newState: ReviewSessionState = {
    ...state,
    isSubmitting: true,
    submitError: null,
    lastGradeAttempted: grade,
  };

  return { newState, payload };
}

export function completeGradeSubmission(
  state: ReviewSessionState,
  response: ReviewSubmissionResponse,
  freshAttemptId: string
): ReviewSessionState {
  const currentCard = state.cards[state.currentIndex];
  const term = currentCard ? currentCard.term : "unknown";

  const updatedResults: SessionResultItem[] = [
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

export function failGradeSubmission(
  state: ReviewSessionState,
  errorMessage: string
): ReviewSessionState {
  // Preserves currentAttemptId and lastGradeAttempted so retry uses the exact same attemptId
  return {
    ...state,
    isSubmitting: false,
    submitError: errorMessage,
  };
}

export function prepareRetrySubmission(
  state: ReviewSessionState
): { newState: ReviewSessionState; payload: ReviewSubmissionPayload | null } {
  if (!state.lastGradeAttempted || state.isSubmitting) {
    return { newState: state, payload: null };
  }

  return startGradeSubmission(state, state.lastGradeAttempted, 1);
}
