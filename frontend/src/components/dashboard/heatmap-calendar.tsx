"use client";

import React, { useState, useMemo, useRef, useEffect, useCallback } from "react";
import {
  Flame,
  Calendar as CalendarIcon,
  CheckCircle2,
  AlertCircle,
  RotateCcw,
  X,
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import type { HeatmapResponse, DayActivity } from "@/lib/api/analytics";

export interface HeatmapCalendarProps {
  data?: HeatmapResponse | null;
  isLoading: boolean;
  isError: boolean;
  error?: string | null;
  onRetry?: () => void;
  onSelectDay?: (day: DayActivity) => void;
  className?: string;
}

const WEEKDAY_LABELS = ["Mon", "", "Wed", "", "Fri", "", ""];
const MONTH_NAMES = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

interface CalendarColumn {
  weekIndex: number;
  monthLabel?: string;
  days: (DayActivity | null)[];
}

export function HeatmapCalendar({
  data,
  isLoading,
  isError,
  error,
  onRetry,
  onSelectDay,
  className = "",
}: HeatmapCalendarProps) {
  const [selectedDay, setSelectedDay] = useState<DayActivity | null>(null);
  const [hoveredDay, setHoveredDay] = useState<DayActivity | null>(null);
  const [activeDateKey, setActiveDateKey] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<"full" | "90days">("full");
  const gridRef = useRef<HTMLDivElement>(null);

  // Derive today from data or fallback
  const todayKey = data?.endDate || new Date().toISOString().split("T")[0];

  // Set default active keyboard focus key to today
  useEffect(() => {
    if (data && !activeDateKey) {
      setActiveDateKey(todayKey);
    }
  }, [data, activeDateKey, todayKey]);

  // Transform data.days into 52 columns of 7 days (Monday..Sunday)
  const columns: CalendarColumn[] = useMemo(() => {
    if (!data?.days) return [];

    const sortedDates = Object.keys(data.days).sort();
    if (sortedDates.length === 0) return [];

    const cols: CalendarColumn[] = [];
    let currentWeek: (DayActivity | null)[] = [];
    let weekIdx = 0;
    let lastMonth = -1;

    for (let i = 0; i < sortedDates.length; i++) {
      const dateStr = sortedDates[i];
      const day = data.days[dateStr];
      const dayOfWeek = new Date(dateStr + "T00:00:00").getDay();
      // JavaScript getDay(): 0 is Sunday, 1 is Monday ... 6 is Saturday
      // ISO-8601 row index: 0 is Monday ... 6 is Sunday
      const isoRow = dayOfWeek === 0 ? 6 : dayOfWeek - 1;

      // When starting a new week column (row 0: Monday)
      if (isoRow === 0 && currentWeek.length > 0) {
        cols.push({
          weekIndex: weekIdx++,
          days: currentWeek,
        });
        currentWeek = [];
      }

      // Pad current week until isoRow
      while (currentWeek.length < isoRow) {
        currentWeek.push(null);
      }
      currentWeek.push(day);
    }

    if (currentWeek.length > 0) {
      while (currentWeek.length < 7) {
        currentWeek.push(null);
      }
      cols.push({
        weekIndex: weekIdx,
        days: currentWeek,
      });
    }

    // Assign month headers to columns where month changes on Monday/first day
    for (let c = 0; c < cols.length; c++) {
      const firstValidDay = cols[c].days.find((d) => d !== null);
      if (firstValidDay) {
        const month = new Date(firstValidDay.date + "T00:00:00").getMonth();
        if (month !== lastMonth) {
          cols[c].monthLabel = MONTH_NAMES[month];
          lastMonth = month;
        }
      }
    }

    // If mobile 90-day mode is selected, slice to the last 14 weeks (~98 days)
    if (viewMode === "90days" && cols.length > 14) {
      return cols.slice(cols.length - 14);
    }

    return cols;
  }, [data, viewMode]);


  const handleCellClick = useCallback(
    (day: DayActivity) => {
      if (day.state === "FUTURE_DATE") return;
      setSelectedDay(day);
      setActiveDateKey(day.date);
      if (onSelectDay) onSelectDay(day);
    },
    [onSelectDay]
  );

  // Keyboard navigation handler for ARIA grid
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent, currentDay: DayActivity) => {
      if (!data?.days) return;
      const sortedKeys = Object.keys(data.days).sort();
      const currentIndex = sortedKeys.indexOf(currentDay.date);
      if (currentIndex === -1) return;

      let nextIndex = currentIndex;

      switch (e.key) {
        case "ArrowRight":
          // Next week (+7 days)
          nextIndex = Math.min(sortedKeys.length - 1, currentIndex + 7);
          e.preventDefault();
          break;
        case "ArrowLeft":
          // Previous week (-7 days)
          nextIndex = Math.max(0, currentIndex - 7);
          e.preventDefault();
          break;
        case "ArrowDown":
          // Next day (+1 day)
          nextIndex = Math.min(sortedKeys.length - 1, currentIndex + 1);
          e.preventDefault();
          break;
        case "ArrowUp":
          // Previous day (-1 day)
          nextIndex = Math.max(0, currentIndex - 1);
          e.preventDefault();
          break;
        case "Home":
          // First day of calendar
          nextIndex = 0;
          e.preventDefault();
          break;
        case "End":
          // Jump to today
          const todayIdx = sortedKeys.indexOf(todayKey);
          nextIndex = todayIdx !== -1 ? todayIdx : sortedKeys.length - 1;
          e.preventDefault();
          break;
        case "Enter":
        case " ":
          handleCellClick(currentDay);
          e.preventDefault();
          return;
        default:
          return;
      }

      const nextDay = data.days[sortedKeys[nextIndex]];
      if (nextDay && nextDay.state !== "FUTURE_DATE") {
        setActiveDateKey(nextDay.date);
        const cellEl = gridRef.current?.querySelector<HTMLElement>(`[data-date="${nextDay.date}"]`);
        cellEl?.focus();
      }
    },
    [data, todayKey, handleCellClick]
  );

  // Helper to get visual cell class by intensity and state
  const getCellClassName = (day: DayActivity | null) => {
    if (!day) return "invisible";

    const isToday = day.date === todayKey;
    const base = "size-3 sm:size-3.5 rounded-[2px] transition-colors relative focus:outline-none focus:ring-1 focus:ring-foreground focus:z-10";
    const todayRing = isToday ? "ring-1 ring-foreground/80 font-bold" : "";

    switch (day.state) {
      case "FUTURE_DATE":
        return `${base} bg-background border border-dotted border-border/40 opacity-40 cursor-not-allowed`;
      case "PRE_REGISTRATION":
        return `${base} bg-[#fafafa] dark:bg-zinc-900 border border-dashed border-border/60 cursor-pointer hover:border-foreground/40`;
      case "ZERO_ACTIVITY":
        return `${base} ${todayRing} bg-[#f5f5f5] dark:bg-zinc-800 border border-border/60 cursor-pointer hover:border-foreground/50`;
      case "ACTIVE_DAY":
        switch (day.intensityLevel) {
          case 1:
            return `${base} ${todayRing} bg-[#d4d4d4] dark:bg-zinc-600 border border-neutral-300 dark:border-zinc-500 cursor-pointer hover:brightness-95`;
          case 2:
            return `${base} ${todayRing} bg-[#a3a3a3] dark:bg-zinc-500 border border-neutral-400 dark:border-zinc-400 cursor-pointer hover:brightness-95`;
          case 3:
            return `${base} ${todayRing} bg-[#525252] dark:bg-zinc-300 border border-neutral-600 dark:border-zinc-200 cursor-pointer hover:brightness-110`;
          case 4:
          default:
            return `${base} ${todayRing} bg-[#171717] dark:bg-white border border-neutral-900 dark:border-white cursor-pointer hover:brightness-125`;
        }
    }
  };

  const getCellAriaLabel = (day: DayActivity | null) => {
    if (!day) return "";
    const formatted = new Date(day.date + "T00:00:00").toLocaleDateString(undefined, {
      month: "long",
      day: "numeric",
      year: "numeric",
    });

    if (day.state === "FUTURE_DATE") {
      return `${formatted}: Future date`;
    }
    if (day.state === "PRE_REGISTRATION") {
      return `${formatted}: Prior to account registration`;
    }
    if (day.actionCount === 0) {
      return `${formatted}: Zero learning activity recorded`;
    }

    const goalStatus = day.goalCompleted ? "daily goal achieved" : "daily goal not achieved";
    return `${formatted}: ${day.actionCount} verified study actions, Level ${day.intensityLevel} intensity, ${goalStatus}.`;
  };

  // Loading skeleton state
  if (isLoading) {
    return (
      <Card className={`border-border bg-card p-6 space-y-4 animate-pulse ${className}`} data-testid="heatmap-loading">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div className="space-y-1.5">
            <div className="h-4 w-52 bg-muted rounded" />
            <div className="h-3 w-80 bg-muted/60 rounded" />
          </div>
          <div className="flex gap-2">
            <div className="h-7 w-20 bg-muted rounded" />
            <div className="h-7 w-20 bg-muted rounded" />
          </div>
        </div>
        <div className="h-28 w-full bg-muted/30 rounded border border-border/50" />
      </Card>
    );
  }

  // Error recovery state
  if (isError || !data) {
    return (
      <Card className={`border-border bg-card p-6 text-center space-y-3 ${className}`} data-testid="heatmap-error">
        <div className="flex items-center justify-center gap-2 text-destructive">
          <AlertCircle className="size-4" />
          <h3 className="text-sm font-semibold text-foreground">Failed to Load Learning Heatmap</h3>
        </div>
        <p className="text-xs text-muted-foreground">{error || "Unable to retrieve longitudinal study telemetry."}</p>
        {onRetry && (
          <Button onClick={onRetry} size="sm" variant="outline" className="gap-2 text-xs mx-auto">
            <RotateCcw className="size-3.5" />
            <span>Retry</span>
          </Button>
        )}
      </Card>
    );
  }

  return (
    <Card className={`border-border bg-card p-6 space-y-5 ${className}`} data-testid="heatmap-calendar">
      {/* 1. Header & Summary KPIs */}
      <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <CalendarIcon className="size-4 text-foreground" />
            <h3 className="text-base font-semibold text-foreground tracking-tight">
              52-Week Learning Activity Heatmap
            </h3>
          </div>
          <p className="text-xs text-muted-foreground mt-1 leading-relaxed">
            Verified study telemetry logged in your local timezone ({data.userTimezone}). Page views and bulk imports award 0 units.
          </p>
        </div>

        {/* Longitudinal KPI Summary Pill Group */}
        <div className="flex items-center flex-wrap gap-2 text-xs font-mono">
          <div className="px-2.5 py-1 rounded border border-border bg-muted/30 flex items-center gap-1.5" data-testid="kpi-active-days">
            <span className="text-muted-foreground">Active Days:</span>
            <span className="font-semibold text-foreground">{data.totalActiveDays}</span>
          </div>

          <div className="px-2.5 py-1 rounded border border-border bg-muted/30 flex items-center gap-1.5" data-testid="kpi-current-streak">
            <Flame className="size-3.5 text-foreground" />
            <span className="text-muted-foreground">Current:</span>
            <span className="font-semibold text-foreground">{data.currentStreakDays}d</span>
          </div>

          <div className="px-2.5 py-1 rounded border border-border bg-muted/30 flex items-center gap-1.5" data-testid="kpi-longest-streak">
            <span className="text-muted-foreground">Longest:</span>
            <span className="font-semibold text-foreground">{data.longestStreakDays}d</span>
          </div>

          {/* Mobile view toggle */}
          <div className="sm:hidden flex items-center border border-border rounded overflow-hidden">
            <button
              onClick={() => setViewMode("full")}
              className={`px-2 py-0.5 text-[10px] ${viewMode === "full" ? "bg-foreground text-background" : "bg-card text-muted-foreground"}`}
            >
              Full
            </button>
            <button
              onClick={() => setViewMode("90days")}
              className={`px-2 py-0.5 text-[10px] ${viewMode === "90days" ? "bg-foreground text-background" : "bg-card text-muted-foreground"}`}
            >
              90d
            </button>
          </div>
        </div>
      </div>

      {/* 2. Heatmap Grid Container */}
      <div className="overflow-x-auto pb-2 focus:outline-none" tabIndex={0} aria-label="Heatmap calendar scroll container">
        <div
          ref={gridRef}
          role="grid"
          aria-label="Annual learning activity calendar"
          aria-readonly="true"
          className="inline-flex flex-col gap-1 min-w-[720px] select-none"
        >
          {/* Month Label Row */}
          <div className="flex text-[10px] font-mono text-muted-foreground mb-0.5 pl-7">
            {columns.map((col, idx) => (
              <div key={idx} className="w-3.5 sm:w-4 text-left overflow-visible whitespace-nowrap">
                {col.monthLabel || ""}
              </div>
            ))}
          </div>

          {/* 7 Weekday Rows (Monday to Sunday) */}
          <div className="flex flex-col gap-1">
            {[0, 1, 2, 3, 4, 5, 6].map((rowIndex) => (
              <div key={rowIndex} role="row" className="flex items-center gap-1">
                {/* Weekday indicator on left */}
                <div className="w-6 text-[10px] font-mono text-muted-foreground text-right pr-1 select-none">
                  {WEEKDAY_LABELS[rowIndex]}
                </div>

                {/* Day Cells across columns */}
                {columns.map((col) => {
                  const day = col.days[rowIndex];
                  if (!day) {
                    return <div key={`${col.weekIndex}-${rowIndex}`} className="size-3 sm:size-3.5 invisible" />;
                  }

                  const isFocused = activeDateKey === day.date;
                  const isFuture = day.state === "FUTURE_DATE";

                  return (
                    <button
                      key={day.date}
                      type="button"
                      role="gridcell"
                      data-date={day.date}
                      data-intensity={day.intensityLevel}
                      data-state={day.state}
                      data-actions={day.actionCount}
                      data-testid={`heatmap-cell-${day.date}`}
                      aria-label={getCellAriaLabel(day)}
                      aria-disabled={isFuture}
                      disabled={isFuture}
                      tabIndex={isFocused ? 0 : -1}
                      onClick={() => handleCellClick(day)}
                      onKeyDown={(e) => handleKeyDown(e, day)}
                      onMouseEnter={() => setHoveredDay(day)}
                      onMouseLeave={() => setHoveredDay(null)}
                      className={getCellClassName(day)}
                    >
                      {/* Inner goal achievement indicator */}
                      {day.goalCompleted && day.actionCount > 0 && (
                        <span className="absolute inset-0 m-auto size-1 rounded-full bg-background dark:bg-zinc-950 pointer-events-none" />
                      )}
                    </button>
                  );
                })}
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* 3. Footer Bar: Live Hover Tooltip & Legend */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 pt-2 border-t border-border text-xs">
        {/* Dynamic Hover / Active Date Status */}
        <div className="text-muted-foreground font-mono text-[11px] min-h-[1.5rem] flex items-center gap-1.5" data-testid="heatmap-hover-info">
          {hoveredDay ? (
            <>
              <span className="font-semibold text-foreground">
                {new Date(hoveredDay.date + "T00:00:00").toLocaleDateString(undefined, {
                  weekday: "short",
                  month: "short",
                  day: "numeric",
                  year: "numeric",
                })}
              </span>
              <span>·</span>
              <span>
                {hoveredDay.state === "FUTURE_DATE"
                  ? "Future date"
                  : hoveredDay.state === "PRE_REGISTRATION"
                  ? "Before registration"
                  : `${hoveredDay.actionCount} verified action${hoveredDay.actionCount === 1 ? "" : "s"}`}
              </span>
              {hoveredDay.goalCompleted && (
                <span className="inline-flex items-center gap-1 text-foreground font-medium pl-1">
                  <CheckCircle2 className="size-3 text-foreground" />
                  Goal met
                </span>
              )}
            </>
          ) : (
            <span className="text-muted-foreground/80">
              Hover or focus a calendar cell to inspect daily verified activity.
            </span>
          )}
        </div>

        {/* Grayscale Ramp Legend */}
        <div className="flex items-center gap-2 font-mono text-[11px] text-muted-foreground select-none" data-testid="heatmap-legend">
          <span>Less</span>
          <div className="flex items-center gap-1">
            <span className="size-3 rounded-[2px] bg-[#f5f5f5] dark:bg-zinc-800 border border-border/60" title="Level 0: 0 actions" />
            <span className="size-3 rounded-[2px] bg-[#d4d4d4] dark:bg-zinc-600 border border-neutral-300 dark:border-zinc-500" title="Level 1: 1–9 actions" />
            <span className="size-3 rounded-[2px] bg-[#a3a3a3] dark:bg-zinc-500 border border-neutral-400 dark:border-zinc-400" title="Level 2: 10–19 actions" />
            <span className="size-3 rounded-[2px] bg-[#525252] dark:bg-zinc-300 border border-neutral-600 dark:border-zinc-200" title="Level 3: 20–39 actions" />
            <span className="size-3 rounded-[2px] bg-[#171717] dark:bg-white border border-neutral-900 dark:border-white" title="Level 4: 40+ actions" />
          </div>
          <span>More</span>
        </div>
      </div>

      {/* 4. Cell Details Inspection Drawer / Popover Modal */}
      {selectedDay && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="day-details-title"
          className="p-4 rounded-md border border-border bg-card/95 shadow-sm space-y-3 mt-2"
          data-testid="selected-day-details"
        >
          <div className="flex items-center justify-between">
            <div>
              <h4 id="day-details-title" className="text-sm font-bold text-foreground">
                {new Date(selectedDay.date + "T00:00:00").toLocaleDateString(undefined, {
                  weekday: "long",
                  year: "numeric",
                  month: "long",
                  day: "numeric",
                })}
              </h4>
              <p className="text-[11px] font-mono text-muted-foreground mt-0.5">
                Total verified activity: {selectedDay.actionCount} action{selectedDay.actionCount === 1 ? "" : "s"} · Intensity Level {selectedDay.intensityLevel}
              </p>
            </div>
            <div className="flex items-center gap-2">
              {selectedDay.goalCompleted ? (
                <Badge variant="outline" className="gap-1 text-[10px] font-mono border-border bg-muted/50 text-foreground">
                  <CheckCircle2 className="size-3 text-foreground" />
                  Goal Achieved
                </Badge>
              ) : (
                <Badge variant="outline" className="text-[10px] font-mono border-border text-muted-foreground">
                  Goal Incomplete
                </Badge>
              )}
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setSelectedDay(null)}
                className="size-7 p-0 text-muted-foreground hover:text-foreground"
                aria-label="Close day details"
              >
                <X className="size-3.5" />
              </Button>
            </div>
          </div>

          {/* Breakdown by Modality */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-2 border-t border-border text-xs font-mono">
            <div className="p-2 rounded border border-border bg-muted/20 space-y-0.5">
              <span className="text-[10px] text-muted-foreground">Flashcards</span>
              <p className="text-sm font-semibold text-foreground">{selectedDay.breakdown.flashcardReviews}</p>
            </div>
            <div className="p-2 rounded border border-border bg-muted/20 space-y-0.5">
              <span className="text-[10px] text-muted-foreground">Dictation</span>
              <p className="text-sm font-semibold text-foreground">{selectedDay.breakdown.dictationExercises}</p>
            </div>
            <div className="p-2 rounded border border-border bg-muted/20 space-y-0.5">
              <span className="text-[10px] text-muted-foreground">Shadowing</span>
              <p className="text-sm font-semibold text-foreground">{selectedDay.breakdown.shadowingTakes}</p>
            </div>
            <div className="p-2 rounded border border-border bg-muted/20 space-y-0.5">
              <span className="text-[10px] text-muted-foreground">Quizzes</span>
              <p className="text-sm font-semibold text-foreground">{selectedDay.breakdown.quizzesCompleted ?? selectedDay.breakdown.quizQuestions}</p>
            </div>
          </div>
        </div>
      )}
    </Card>
  );
}
