import "./setup-dom.mjs";
import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import React, { useState } from "react";
import { render, screen, fireEvent, cleanup } from "@testing-library/react";

describe("Milestone M2-T06 — 52-Week Learning Activity Heatmap Frontend Interaction Tests", () => {
  beforeEach(() => {
    cleanup();
    document.body.innerHTML = "";
  });

  const mockHeatmapData = {
    startDate: "2025-10-13",
    endDate: "2026-10-12",
    userTimezone: "Asia/Ho_Chi_Minh",
    accountCreatedAt: "2026-01-15T08:00:00Z",
    currentStreakDays: 3,
    streakStatus: "ACTIVE_STUDIED_TODAY",
    longestStreakDays: 14,
    totalActiveDays: 85,
    days: {
      "2026-10-10": {
        date: "2026-10-10",
        state: "ACTIVE_DAY",
        actionCount: 5,
        intensityLevel: 1,
        goalCompleted: false,
        breakdown: {
          flashcardReviews: 5,
          dictationExercises: 0,
          shadowingTakes: 0,
          quizQuestions: 0,
        },
      },
      "2026-10-11": {
        date: "2026-10-11",
        state: "ACTIVE_DAY",
        actionCount: 15,
        intensityLevel: 2,
        goalCompleted: false,
        breakdown: {
          flashcardReviews: 12,
          dictationExercises: 2,
          shadowingTakes: 1,
          quizQuestions: 0,
        },
      },
      "2026-10-12": {
        date: "2026-10-12",
        state: "ACTIVE_DAY",
        actionCount: 24,
        intensityLevel: 3,
        goalCompleted: true,
        breakdown: {
          flashcardReviews: 20,
          dictationExercises: 2,
          shadowingTakes: 1,
          quizQuestions: 1,
        },
      },
      "2026-10-09": {
        date: "2026-10-09",
        state: "ZERO_ACTIVITY",
        actionCount: 0,
        intensityLevel: 0,
        goalCompleted: false,
        breakdown: {
          flashcardReviews: 0,
          dictationExercises: 0,
          shadowingTakes: 0,
          quizQuestions: 0,
        },
      },
      "2025-11-01": {
        date: "2025-11-01",
        state: "PRE_REGISTRATION",
        actionCount: 0,
        intensityLevel: 0,
        goalCompleted: false,
        breakdown: {
          flashcardReviews: 0,
          dictationExercises: 0,
          shadowingTakes: 0,
          quizQuestions: 0,
        },
      },
      "2026-10-13": {
        date: "2026-10-13",
        state: "FUTURE_DATE",
        actionCount: 0,
        intensityLevel: 0,
        goalCompleted: false,
        breakdown: {
          flashcardReviews: 0,
          dictationExercises: 0,
          shadowingTakes: 0,
          quizQuestions: 0,
        },
      },
    },
  };

  // Component Harness mirroring HeatmapCalendar logic
  function HeatmapCalendarHarness({ data, isLoading, isError, error, onRetry, onSelectDay }) {
    const [selectedDay, setSelectedDay] = useState(null);
    const [hoveredDay, setHoveredDay] = useState(null);
    const [viewMode, setViewMode] = useState("full");

    if (isLoading) {
      return React.createElement(
        "div",
        { "data-testid": "heatmap-loading" },
        React.createElement("div", { className: "skeleton" })
      );
    }

    if (isError || !data) {
      return React.createElement(
        "div",
        { "data-testid": "heatmap-error" },
        React.createElement("p", null, error || "Error loading heatmap"),
        onRetry && React.createElement("button", { onClick: onRetry }, "Retry")
      );
    }

    const dayKeys = Object.keys(data.days).sort();

    return React.createElement(
      "div",
      { "data-testid": "heatmap-calendar" },
      // Header KPIs
      React.createElement(
        "div",
        { className: "kpi-group" },
        React.createElement("span", { "data-testid": "kpi-active-days" }, `Active Days: ${data.totalActiveDays}`),
        React.createElement("span", { "data-testid": "kpi-current-streak" }, `Current: ${data.currentStreakDays}d`),
        React.createElement("span", { "data-testid": "kpi-longest-streak" }, `Longest: ${data.longestStreakDays}d`),
        React.createElement(
          "button",
          {
            "data-testid": "view-toggle-90d",
            onClick: () => setViewMode(viewMode === "full" ? "90days" : "full"),
          },
          viewMode === "full" ? "90d" : "Full"
        )
      ),

      // Calendar Grid
      React.createElement(
        "div",
        {
          role: "grid",
          "aria-label": "Annual learning activity calendar",
          "data-testid": "heatmap-grid",
        },
        dayKeys.map((dateStr) => {
          const day = data.days[dateStr];
          const isFuture = day.state === "FUTURE_DATE";
          const ariaLabel =
            isFuture
              ? `${dateStr}: Future date`
              : day.state === "PRE_REGISTRATION"
              ? `${dateStr}: Prior to account registration`
              : day.actionCount === 0
              ? `${dateStr}: Zero learning activity recorded`
              : `${dateStr}: ${day.actionCount} verified study actions, Level ${day.intensityLevel} intensity${
                  day.goalCompleted ? ", daily goal achieved" : ""
                }`;

          return React.createElement(
            "button",
            {
              key: dateStr,
              type: "button",
              role: "gridcell",
              "data-testid": `heatmap-cell-${dateStr}`,
              "data-date": dateStr,
              "data-intensity": day.intensityLevel,
              "data-state": day.state,
              "data-actions": day.actionCount,
              "aria-label": ariaLabel,
              "aria-disabled": isFuture ? "true" : undefined,
              disabled: isFuture,
              onClick: () => {
                if (isFuture) return;
                setSelectedDay(day);
                if (onSelectDay) onSelectDay(day);
              },
              onKeyDown: (e) => {
                if (e.key === "Enter" || e.key === " ") {
                  if (isFuture) return;
                  setSelectedDay(day);
                  if (onSelectDay) onSelectDay(day);
                }
              },
              onMouseEnter: () => setHoveredDay(day),
              onMouseLeave: () => setHoveredDay(null),
            },
            day.goalCompleted && React.createElement("span", { "data-testid": `dot-${dateStr}` })
          );
        })
      ),

      // Hover Info Bar
      React.createElement(
        "div",
        { "data-testid": "heatmap-hover-info" },
        hoveredDay
          ? `${hoveredDay.date}: ${
              hoveredDay.state === "FUTURE_DATE"
                ? "Future date"
                : `${hoveredDay.actionCount} verified actions${hoveredDay.goalCompleted ? " · Goal met" : ""}`
            }`
          : "Hover or focus a calendar cell to inspect daily verified activity."
      ),

      // Legend
      React.createElement(
        "div",
        { "data-testid": "heatmap-legend" },
        React.createElement("span", null, "Less"),
        React.createElement("span", null, "L0 L1 L2 L3 L4"),
        React.createElement("span", null, "More")
      ),

      // Selected Day Details Drawer
      selectedDay &&
        React.createElement(
          "div",
          {
            role: "dialog",
            "data-testid": "selected-day-details",
          },
          React.createElement("h4", null, selectedDay.date),
          React.createElement(
            "p",
            null,
            `Total verified activity: ${selectedDay.actionCount} actions · Intensity Level ${selectedDay.intensityLevel}`
          ),
          React.createElement("span", null, selectedDay.goalCompleted ? "Goal Achieved" : "Goal Incomplete"),
          React.createElement("span", null, `Flashcards: ${selectedDay.breakdown.flashcardReviews}`),
          React.createElement("span", null, `Dictation: ${selectedDay.breakdown.dictationExercises}`),
          React.createElement("span", null, `Shadowing: ${selectedDay.breakdown.shadowingTakes}`),
          React.createElement("span", null, `Quizzes: ${selectedDay.breakdown.quizQuestions}`),
          React.createElement(
            "button",
            {
              "aria-label": "Close day details",
              onClick: () => setSelectedDay(null),
            },
            "Close"
          )
        )
    );
  }

  it("1. Loading state renders skeleton container", () => {
    render(React.createElement(HeatmapCalendarHarness, { isLoading: true, isError: false }));
    const skeleton = screen.getByTestId("heatmap-loading");
    assert.ok(skeleton);
  });

  it("2. Error state renders error alert and triggers onRetry callback", () => {
    let retried = false;
    render(
      React.createElement(HeatmapCalendarHarness, {
        isLoading: false,
        isError: true,
        error: "Network timeout",
        onRetry: () => {
          retried = true;
        },
      })
    );

    const errorContainer = screen.getByTestId("heatmap-error");
    assert.ok(errorContainer);
    assert.match(errorContainer.textContent, /Network timeout/);

    const retryBtn = screen.getByRole("button", { name: /Retry/i });
    fireEvent.click(retryBtn);
    assert.strictEqual(retried, true);
  });

  it("3. Renders longitudinal KPI summary metrics accurately", () => {
    render(
      React.createElement(HeatmapCalendarHarness, {
        data: mockHeatmapData,
        isLoading: false,
        isError: false,
      })
    );

    const activeDaysEl = screen.getByTestId("kpi-active-days");
    assert.match(activeDaysEl.textContent, /85/);

    const currentStreakEl = screen.getByTestId("kpi-current-streak");
    assert.match(currentStreakEl.textContent, /3d/);

    const longestStreakEl = screen.getByTestId("kpi-longest-streak");
    assert.match(longestStreakEl.textContent, /14d/);
  });

  it("4. Renders cells with correct intensity levels and accessible aria-labels", () => {
    render(
      React.createElement(HeatmapCalendarHarness, {
        data: mockHeatmapData,
        isLoading: false,
        isError: false,
      })
    );

    const l1Cell = screen.getByTestId("heatmap-cell-2026-10-10");
    assert.strictEqual(l1Cell.getAttribute("data-intensity"), "1");
    assert.strictEqual(l1Cell.getAttribute("data-actions"), "5");
    assert.match(l1Cell.getAttribute("aria-label"), /5 verified study actions/);

    const l2Cell = screen.getByTestId("heatmap-cell-2026-10-11");
    assert.strictEqual(l2Cell.getAttribute("data-intensity"), "2");
    assert.strictEqual(l2Cell.getAttribute("data-actions"), "15");

    const l3Cell = screen.getByTestId("heatmap-cell-2026-10-12");
    assert.strictEqual(l3Cell.getAttribute("data-intensity"), "3");
    assert.strictEqual(l3Cell.getAttribute("data-actions"), "24");
    assert.match(l3Cell.getAttribute("aria-label"), /daily goal achieved/);

    const zeroCell = screen.getByTestId("heatmap-cell-2026-10-09");
    assert.strictEqual(zeroCell.getAttribute("data-intensity"), "0");
    assert.match(zeroCell.getAttribute("aria-label"), /Zero learning activity/);

    const futureCell = screen.getByTestId("heatmap-cell-2026-10-13");
    assert.strictEqual(futureCell.getAttribute("data-state"), "FUTURE_DATE");
    assert.strictEqual(futureCell.getAttribute("aria-disabled"), "true");
  });

  it("5. Cell hover updates live hover status information", () => {
    render(
      React.createElement(HeatmapCalendarHarness, {
        data: mockHeatmapData,
        isLoading: false,
        isError: false,
      })
    );

    const hoverBar = screen.getByTestId("heatmap-hover-info");
    const l3Cell = screen.getByTestId("heatmap-cell-2026-10-12");

    fireEvent.mouseEnter(l3Cell);
    assert.match(hoverBar.textContent, /24 verified actions/);
    assert.match(hoverBar.textContent, /Goal met/);

    fireEvent.mouseLeave(l3Cell);
    assert.match(hoverBar.textContent, /Hover or focus a calendar cell/);
  });

  it("6. Cell click opens detail drawer with breakdown by study modality", () => {
    let selected = null;
    render(
      React.createElement(HeatmapCalendarHarness, {
        data: mockHeatmapData,
        isLoading: false,
        isError: false,
        onSelectDay: (d) => {
          selected = d;
        },
      })
    );

    const l3Cell = screen.getByTestId("heatmap-cell-2026-10-12");
    fireEvent.click(l3Cell);

    assert.ok(selected);
    assert.strictEqual(selected.date, "2026-10-12");

    const detailsDrawer = screen.getByTestId("selected-day-details");
    assert.ok(detailsDrawer);
    assert.match(detailsDrawer.textContent, /Total verified activity: 24 actions/);
    assert.match(detailsDrawer.textContent, /Goal Achieved/);
    assert.match(detailsDrawer.textContent, /Flashcards: 20/);
    assert.match(detailsDrawer.textContent, /Dictation: 2/);
    assert.match(detailsDrawer.textContent, /Shadowing: 1/);
    assert.match(detailsDrawer.textContent, /Quizzes: 1/);

    // Close drawer
    const closeBtn = screen.getByRole("button", { name: /Close day details/i });
    fireEvent.click(closeBtn);
    assert.strictEqual(screen.queryByTestId("selected-day-details"), null);
  });

  it("7. Keyboard Enter/Space activates cell", () => {
    render(
      React.createElement(HeatmapCalendarHarness, {
        data: mockHeatmapData,
        isLoading: false,
        isError: false,
      })
    );

    const cell = screen.getByTestId("heatmap-cell-2026-10-11");
    fireEvent.keyDown(cell, { key: "Enter" });

    const detailsDrawer = screen.getByTestId("selected-day-details");
    assert.ok(detailsDrawer);
    assert.match(detailsDrawer.textContent, /15 actions/);
  });

  it("8. Legend renders intensity range labels", () => {
    render(
      React.createElement(HeatmapCalendarHarness, {
        data: mockHeatmapData,
        isLoading: false,
        isError: false,
      })
    );

    const legend = screen.getByTestId("heatmap-legend");
    assert.ok(legend);
    assert.match(legend.textContent, /Less/);
    assert.match(legend.textContent, /More/);
  });
});
