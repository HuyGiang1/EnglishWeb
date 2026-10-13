/**
 * EnglishFlow Dashboard Analytics & UI Calculation Helpers.
 * Pure deterministic functions for dashboard KPI display and logic.
 */

/**
 * Calculates goal progress percentage bounded between 0 and 100.
 *
 * @param {number} completed - Total verified learning actions today.
 * @param {number} target - Daily goal target items (minimum 1).
 * @returns {number} Percentage between 0 and 100.
 */
export function calculateGoalPercentage(completed, target) {
  const safeTarget = Math.max(1, Number(target) || 1);
  const safeCompleted = Math.max(0, Number(completed) || 0);
  return Math.min(100, Math.round((safeCompleted * 100) / safeTarget));
}

/**
 * Returns whether the daily goal is considered completed.
 *
 * @param {number} completed
 * @param {number} target
 * @returns {boolean}
 */
export function isGoalReached(completed, target) {
  const safeTarget = Math.max(1, Number(target) || 1);
  const safeCompleted = Math.max(0, Number(completed) || 0);
  return safeCompleted >= safeTarget;
}

/**
 * Resolves user-friendly streak label, status descriptor, and visual badge style.
 *
 * @param {number} currentStreakDays - Current consecutive learning streak days.
 * @param {"ACTIVE_STUDIED_TODAY" | "ACTIVE_PENDING_TODAY" | "BROKEN_RESET"} streakStatus - Authoritative server streak status.
 * @returns {{ label: string, description: string, badgeVariant: "active" | "pending" | "inactive" }}
 */
export function resolveStreakDisplay(currentStreakDays, streakStatus) {
  const streak = Math.max(0, Number(currentStreakDays) || 0);

  switch (streakStatus) {
    case "ACTIVE_STUDIED_TODAY":
      return {
        label: streak === 1 ? "1 day (Active)" : `${streak} days (Active)`,
        description: "Goal completed or practiced today",
        badgeVariant: "active",
      };
    case "ACTIVE_PENDING_TODAY":
      return {
        label: streak === 1 ? "1 day (Pending)" : `${streak} days (Pending)`,
        description: "Study today to maintain your streak",
        badgeVariant: "pending",
      };
    case "BROKEN_RESET":
    default:
      return {
        label: "0 days",
        description: "Complete a study session today to start",
        badgeVariant: "inactive",
      };
  }
}

/**
 * Determines primary study CTA and route based on queue status and vocabulary size.
 *
 * @param {number} dueReviewCount - Number of flashcards due for review.
 * @param {number} vocabularyTotal - Total active vocabulary items.
 * @returns {{ type: "START_REVIEW" | "ALL_CAUGHT_UP" | "ADD_VOCABULARY", href: string, label: string, description: string }}
 */
export function resolveNextAction(dueReviewCount, vocabularyTotal) {
  const due = Math.max(0, Number(dueReviewCount) || 0);
  const total = Math.max(0, Number(vocabularyTotal) || 0);

  if (total === 0) {
    return {
      type: "ADD_VOCABULARY",
      href: "/vocabulary",
      label: "Add Your First Word",
      description: "Build your personal library to start spaced repetition",
    };
  }

  if (due > 0) {
    return {
      type: "START_REVIEW",
      href: "/review",
      label: `Review ${due} Due ${due === 1 ? "Card" : "Cards"}`,
      description: "Spaced repetition practice is ready for review",
    };
  }

  return {
    type: "ALL_CAUGHT_UP",
    href: "/vocabulary",
    label: "Explore Vocabulary",
    description: "All due flashcards are caught up for today",
  };
}
