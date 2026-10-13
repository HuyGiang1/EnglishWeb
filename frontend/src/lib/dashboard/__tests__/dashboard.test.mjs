import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  calculateGoalPercentage,
  isGoalReached,
  resolveStreakDisplay,
  resolveNextAction,
} from "../dashboard-utils.mjs";

describe("Dashboard Analytics Utilities & Business Rule Tests", () => {
  describe("Daily Goal Progress Percentage Calculations", () => {
    it("Calculates 0% when completed count is 0", () => {
      assert.equal(calculateGoalPercentage(0, 20), 0);
    });

    it("Calculates proportional percentage correctly (5 / 20 = 25%)", () => {
      assert.equal(calculateGoalPercentage(5, 20), 25);
    });

    it("Rounds to nearest whole percentage (7 / 20 = 35%)", () => {
      assert.equal(calculateGoalPercentage(7, 20), 35);
    });

    it("Caps progress percentage at 100% when completed exceeds target", () => {
      assert.equal(calculateGoalPercentage(25, 20), 100);
      assert.equal(calculateGoalPercentage(50, 10), 100);
    });

    it("Safely handles target of 0 or negative by falling back to minimum 1", () => {
      assert.equal(calculateGoalPercentage(0, 0), 0);
      assert.equal(calculateGoalPercentage(1, 0), 100);
    });

    it("Correctly identifies when daily goal is reached", () => {
      assert.equal(isGoalReached(0, 20), false);
      assert.equal(isGoalReached(19, 20), false);
      assert.equal(isGoalReached(20, 20), true);
      assert.equal(isGoalReached(25, 20), true);
    });
  });

  describe("Streak Display & Status Transitions", () => {
    it("Formats ACTIVE_STUDIED_TODAY correctly with 1 day streak", () => {
      const display = resolveStreakDisplay(1, "ACTIVE_STUDIED_TODAY");
      assert.equal(display.label, "1 day (Active)");
      assert.equal(display.badgeVariant, "active");
    });

    it("Formats ACTIVE_STUDIED_TODAY correctly with multi-day streak", () => {
      const display = resolveStreakDisplay(5, "ACTIVE_STUDIED_TODAY");
      assert.equal(display.label, "5 days (Active)");
      assert.equal(display.badgeVariant, "active");
    });

    it("Formats ACTIVE_PENDING_TODAY correctly (yesterday preserved)", () => {
      const display = resolveStreakDisplay(3, "ACTIVE_PENDING_TODAY");
      assert.equal(display.label, "3 days (Pending)");
      assert.equal(display.badgeVariant, "pending");
      assert.match(display.description, /Study today to maintain your streak/i);
    });

    it("Formats BROKEN_RESET when streak has broken or user is new", () => {
      const display = resolveStreakDisplay(0, "BROKEN_RESET");
      assert.equal(display.label, "0 days");
      assert.equal(display.badgeVariant, "inactive");
    });
  });

  describe("Next Study Action Routing", () => {
    it("Suggests ADD_VOCABULARY when library is completely empty", () => {
      const action = resolveNextAction(0, 0);
      assert.equal(action.type, "ADD_VOCABULARY");
      assert.equal(action.href, "/vocabulary");
      assert.match(action.label, /Add Your First Word/i);
    });

    it("Directs to /review when flashcards are due", () => {
      const action = resolveNextAction(5, 20);
      assert.equal(action.type, "START_REVIEW");
      assert.equal(action.href, "/review");
      assert.equal(action.label, "Review 5 Due Cards");
    });

    it("Handles singular card due label correctly", () => {
      const action = resolveNextAction(1, 10);
      assert.equal(action.type, "START_REVIEW");
      assert.equal(action.label, "Review 1 Due Card");
    });

    it("Indicates ALL_CAUGHT_UP when user has vocabulary but 0 cards due", () => {
      const action = resolveNextAction(0, 15);
      assert.equal(action.type, "ALL_CAUGHT_UP");
      assert.equal(action.href, "/vocabulary");
      assert.equal(action.label, "Explore Vocabulary");
    });
  });
});
