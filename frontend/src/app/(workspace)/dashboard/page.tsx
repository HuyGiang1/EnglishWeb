"use client";

import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import {
  Sparkles,
  BookOpen,
  ArrowRight,
  Target,
  Flame,
  CheckCircle2,
  AlertCircle,
  RotateCcw,
  Calendar,
  Layers,
  History,
} from "lucide-react";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { dashboardApi, type DashboardSummary } from "@/lib/api/dashboard";
import { HeatmapCalendar } from "@/components/dashboard/heatmap-calendar";
import type { HeatmapResponse } from "@/lib/api/analytics";

export default function DashboardPage() {
  const [data, setData] = useState<DashboardSummary | null>(null);
  const [heatmapData, setHeatmapData] = useState<HeatmapResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [heatmapLoading, setHeatmapLoading] = useState<boolean>(true);
  const [heatmapError, setHeatmapError] = useState<string | null>(null);

  const fetchSummary = useCallback(async () => {
    setLoading(true);
    setError(null);
    setHeatmapLoading(true);
    setHeatmapError(null);
    try {
      const [summary, heatmap] = await Promise.all([
        dashboardApi.getSummary(),
        dashboardApi.getHeatmap().catch((err: unknown) => {
          setHeatmapError(err instanceof Error ? err.message : "Failed to load heatmap");
          return null;
        }),
      ]);
      setData(summary);
      if (heatmap) {
        setHeatmapData(heatmap);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to load dashboard metrics";
      setError(msg);
    } finally {
      setLoading(false);
      setHeatmapLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSummary();
  }, [fetchSummary]);

  // Loading Skeleton State
  if (loading) {
    return (
      <div className="space-y-8 animate-pulse" data-testid="dashboard-loading">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div className="space-y-2">
            <div className="h-7 w-56 bg-muted rounded" />
            <div className="h-4 w-72 bg-muted/60 rounded" />
          </div>
          <div className="h-8 w-32 bg-muted rounded" />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <Card key={i} className="border-border bg-card p-5 space-y-3">
              <div className="h-3 w-24 bg-muted rounded" />
              <div className="h-8 w-16 bg-muted rounded" />
              <div className="h-3 w-32 bg-muted/60 rounded" />
            </Card>
          ))}
        </div>

        <Card className="border-border bg-card p-6 space-y-4">
          <div className="h-4 w-40 bg-muted rounded" />
          <div className="h-2 w-full bg-muted rounded" />
        </Card>
      </div>
    );
  }

  // Error State with Retry
  if (error || !data) {
    return (
      <Card className="max-w-lg mx-auto p-8 border-border bg-card text-center flex flex-col items-center gap-4 my-8" data-testid="dashboard-error">
        <div className="size-10 rounded-full bg-destructive/10 text-destructive flex items-center justify-center">
          <AlertCircle className="size-5" />
        </div>
        <div>
          <h2 className="text-lg font-semibold text-foreground">Failed to Load Dashboard</h2>
          <p className="text-xs sm:text-sm text-muted-foreground mt-1">{error || "Unable to reach server"}</p>
        </div>
        <div className="flex items-center gap-3 mt-2">
          <Button onClick={fetchSummary} size="sm" variant="outline" className="gap-2 text-xs">
            <RotateCcw className="size-3.5" />
            <span>Try Again</span>
          </Button>
          <Link href="/vocabulary">
            <Button size="sm" variant="ghost" className="text-xs">
              Open Vocabulary
            </Button>
          </Link>
        </div>
      </Card>
    );
  }

  // Formatted Local Date string
  const formattedDate = new Date(data.currentDate + "T00:00:00").toLocaleDateString(undefined, {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  });

  const isGoalReached = data.dailyGoalCompletedCount >= data.dailyGoalTarget;

  return (
    <div className="space-y-8" data-testid="dashboard-content">
      {/* A. Welcome Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground" data-testid="dashboard-greeting">
            {data.greeting}
          </h1>
          <div className="flex items-center gap-2 mt-1 text-xs text-muted-foreground font-mono">
            <Calendar className="size-3.5 text-muted-foreground" />
            <span data-testid="dashboard-date">{formattedDate}</span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {data.dueReviewCount > 0 ? (
            <Link href="/review">
              <Button size="sm" className="text-xs gap-1.5 h-8 font-medium" data-testid="header-review-btn">
                <Sparkles className="size-3.5" />
                <span>Review Due Cards ({data.dueReviewCount})</span>
                <ArrowRight className="size-3" />
              </Button>
            </Link>
          ) : (
            <Link href="/vocabulary">
              <Button size="sm" variant="outline" className="text-xs gap-1.5 h-8" data-testid="header-vocab-btn">
                <BookOpen className="size-3.5" />
                <span>Vocabulary Library</span>
              </Button>
            </Link>
          )}
        </div>
      </div>

      {/* B. KPI Overview Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* 1. Saved Vocabulary */}
        <Card className="border-border bg-card" data-testid="kpi-vocabulary">
          <CardHeader className="pb-2">
            <CardDescription className="text-[11px] font-mono uppercase tracking-wider text-muted-foreground flex items-center justify-between">
              <span>Saved Vocabulary</span>
              <BookOpen className="size-3.5 text-muted-foreground" />
            </CardDescription>
            <CardTitle className="text-3xl font-bold text-foreground font-mono" data-testid="kpi-vocab-count">
              {data.vocabularyTotal}
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-0">
            <Link
              href="/vocabulary"
              className="text-xs text-muted-foreground hover:text-foreground transition-colors inline-flex items-center gap-1"
            >
              <span>{data.vocabularyTotal === 0 ? "Add your first words" : "View word library"}</span>
              <ArrowRight className="size-3" />
            </Link>
          </CardContent>
        </Card>

        {/* 2. Due For Review */}
        <Card className="border-border bg-card" data-testid="kpi-due">
          <CardHeader className="pb-2">
            <CardDescription className="text-[11px] font-mono uppercase tracking-wider text-muted-foreground flex items-center justify-between">
              <span>Due For Review</span>
              <Sparkles className="size-3.5 text-muted-foreground" />
            </CardDescription>
            <CardTitle className="text-3xl font-bold text-foreground font-mono" data-testid="kpi-due-count">
              {data.dueReviewCount}
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-0">
            {data.dueReviewCount > 0 ? (
              <span className="inline-flex items-center gap-1 text-[11px] font-medium text-foreground">
                <span className="size-1.5 rounded-full bg-foreground" />
                Cards scheduled today
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground">
                <CheckCircle2 className="size-3 text-muted-foreground" />
                All reviews completed
              </span>
            )}
          </CardContent>
        </Card>

        {/* 3. Completed Actions Today */}
        <Card className="border-border bg-card" data-testid="kpi-actions">
          <CardHeader className="pb-2">
            <CardDescription className="text-[11px] font-mono uppercase tracking-wider text-muted-foreground flex items-center justify-between">
              <span>Completed Today</span>
              <Target className="size-3.5 text-muted-foreground" />
            </CardDescription>
            <CardTitle className="text-3xl font-bold text-foreground font-mono" data-testid="kpi-actions-count">
              {data.dailyGoalCompletedCount}
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-0">
            <span className="text-[11px] text-muted-foreground font-mono">
              Target: {data.dailyGoalTarget} actions
            </span>
          </CardContent>
        </Card>

        {/* 4. Learning Streak */}
        <Card className="border-border bg-card" data-testid="kpi-streak">
          <CardHeader className="pb-2">
            <CardDescription className="text-[11px] font-mono uppercase tracking-wider text-muted-foreground flex items-center justify-between">
              <span>Learning Streak</span>
              <Flame className="size-3.5 text-muted-foreground" />
            </CardDescription>
            <CardTitle className="text-3xl font-bold text-foreground font-mono" data-testid="kpi-streak-count">
              {data.currentStreakDays} <span className="text-sm font-normal text-muted-foreground">days</span>
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-0">
            {data.streakStatus === "ACTIVE_STUDIED_TODAY" && (
              <span className="inline-flex items-center gap-1 text-[11px] font-medium text-foreground">
                <span className="size-1.5 rounded-full bg-foreground" />
                Active · Studied today
              </span>
            )}
            {data.streakStatus === "ACTIVE_PENDING_TODAY" && (
              <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground">
                <span className="size-1.5 rounded-full border border-border bg-muted" />
                Preserved · Pending study
              </span>
            )}
            {data.streakStatus === "BROKEN_RESET" && (
              <span className="text-[11px] text-muted-foreground">
                Start a session to build streak
              </span>
            )}
          </CardContent>
        </Card>
      </div>

      {/* C. Daily Goal Progress Section */}
      <Card className="border-border bg-card p-6" data-testid="daily-goal-section">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-mono uppercase tracking-wider text-muted-foreground">Daily Habit Target</span>
              {isGoalReached && (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-mono font-medium border border-border bg-muted/60 text-foreground" data-testid="goal-achieved-badge">
                  <CheckCircle2 className="size-3 text-foreground" />
                  Goal Achieved
                </span>
              )}
            </div>
            <h2 className="text-lg font-bold text-foreground tracking-tight mt-0.5">
              {data.dailyGoalCompletedCount} of {data.dailyGoalTarget} actions completed
            </h2>
          </div>
          <span className="text-2xl font-bold font-mono text-foreground tracking-tight sm:text-right" data-testid="goal-percent">
            {data.goalProgressPercentage}%
          </span>
        </div>

        {/* Progress Bar */}
        <div className="w-full bg-muted rounded-full h-2 overflow-hidden" role="progressbar" aria-valuenow={data.goalProgressPercentage} aria-valuemin={0} aria-valuemax={100}>
          <div
            className="bg-foreground h-full transition-all duration-300 ease-out"
            style={{ width: `${Math.min(100, data.goalProgressPercentage)}%` }}
          />
        </div>

        <p className="text-xs text-muted-foreground mt-3 leading-relaxed">
          {isGoalReached
            ? "Your daily retention quota is met. Any additional practice reinforces memory stability without point penalties."
            : `Complete ${Math.max(0, data.dailyGoalTarget - data.dailyGoalCompletedCount)} more verified learning actions today to reach your target.`}
        </p>
      </Card>

      {/* D. Next Study Action Callout */}
      <Card className="border-border bg-card p-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4" data-testid="next-action-callout">
        <div className="space-y-1">
          <div className="flex items-center gap-1.5 text-xs font-mono text-muted-foreground">
            <Layers className="size-3.5 text-foreground" />
            <span>Recommended Modality</span>
          </div>
          <h3 className="text-base font-semibold text-foreground">
            {data.dueReviewCount > 0
              ? `Spaced Repetition Review (${data.dueReviewCount} cards due)`
              : data.vocabularyTotal === 0
              ? "Start Capturing Vocabulary"
              : "Review Session Complete"}
          </h3>
          <p className="text-xs text-muted-foreground max-w-lg leading-relaxed">
            {data.dueReviewCount > 0
              ? "Active recall flashcards are due for review. Grade each word (Again / Hard / Good / Easy) to optimize interval spacing."
              : data.vocabularyTotal === 0
              ? "Your reference library is currently empty. Add words with IPA phonetics and definitions to begin spaced repetition scheduling."
              : "All scheduled cards have been reviewed for today. Add new words or explore saved entries in your library."}
          </p>
        </div>
        <div className="shrink-0">
          {data.dueReviewCount > 0 ? (
            <Link href="/review">
              <Button size="sm" className="gap-2 text-xs" data-testid="cta-review-btn">
                <Sparkles className="size-3.5" />
                <span>Start Review</span>
              </Button>
            </Link>
          ) : (
            <Link href="/vocabulary">
              <Button size="sm" variant="outline" className="gap-2 text-xs" data-testid="cta-vocab-btn">
                <BookOpen className="size-3.5" />
                <span>{data.vocabularyTotal === 0 ? "Add Words" : "View Library"}</span>
              </Button>
            </Link>
          )}
        </div>
      </Card>

      {/* E. Recent Activity Section */}
      <Card className="border-border bg-card p-6 space-y-4" data-testid="recent-activity-section">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <History className="size-4 text-muted-foreground" />
            <h3 className="text-sm font-semibold text-foreground">Recent Study Modalities</h3>
          </div>
          <span className="text-[11px] font-mono text-muted-foreground">Verified Activity</span>
        </div>

        {data.recentSessions.length > 0 ? (
          <div className="divide-y divide-border border-y border-border">
            {data.recentSessions.map((session, idx) => (
              <div key={idx} className="py-2.5 flex items-center justify-between text-xs">
                <div className="flex items-center gap-2">
                  <span className="font-mono text-[11px] font-medium text-foreground">
                    {session.type === "FLASHCARD_REVIEW" ? "Flashcard Reviews" : session.type}
                  </span>
                  <span className="text-muted-foreground">·</span>
                  <span className="text-muted-foreground font-mono">{session.count} action{session.count === 1 ? "" : "s"}</span>
                </div>
                <span className="text-[11px] font-mono text-muted-foreground">
                  {new Date(session.completedAt).toLocaleTimeString(undefined, {
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <div className="p-4 rounded-md border border-border bg-muted/20 text-center text-xs text-muted-foreground">
            Zero study actions recorded yet today. Complete reviews to generate telemetry.
          </div>
        )}
      </Card>

      {/* F. 52-Week Learning Activity Heatmap */}
      <HeatmapCalendar
        data={heatmapData}
        isLoading={heatmapLoading}
        isError={!!heatmapError}
        error={heatmapError}
        onRetry={fetchSummary}
      />
    </div>
  );
}
