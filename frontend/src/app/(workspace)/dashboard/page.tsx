"use client";

import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button, buttonVariants } from "@/components/ui/button";
import { dashboardApi, type DashboardSummary } from "@/lib/api/dashboard";
import { HeatmapCalendar } from "@/components/dashboard/heatmap-calendar";
import type { HeatmapResponse } from "@/lib/api/analytics";
import { useLocale } from "@/lib/i18n/locale-provider";
import type { TranslationKey } from "@/lib/i18n/dictionaries";
import { shellErrorKey } from "@/lib/i18n/shell-errors";
import { useAuth } from "@/lib/auth/auth-context";

const activityKeys: Record<string, TranslationKey> = {
  FLASHCARD_REVIEW: "navReview", DICTATION_SENTENCE: "navDictation",
  SHADOWING_TAKE: "navShadowing", QUIZ_QUESTION: "navQuiz", QUIZ_COMPLETION: "navQuiz",
};

export default function DashboardPage() {
  const { locale, t } = useLocale();
  const { user } = useAuth();
  const [data, setData] = useState<DashboardSummary | null>(null);
  const [heatmapData, setHeatmapData] = useState<HeatmapResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<TranslationKey | null>(null);
  const [heatmapError, setHeatmapError] = useState<TranslationKey | null>(null);
  const fetchSummary = useCallback(async () => {
    setLoading(true); setError(null); setHeatmapError(null);
    try {
      const [summary, heatmap] = await Promise.all([
        dashboardApi.getSummary(),
        dashboardApi.getHeatmap().catch((error: unknown) => { setHeatmapError(shellErrorKey(error, "heatmapLoadFailed")); return null; }),
      ]);
      setData(summary); setHeatmapData(heatmap);
    } catch (error: unknown) { setError(shellErrorKey(error, "dashboardLoadFailed")); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { fetchSummary(); }, [fetchSummary]);
  const n = (value: number) => new Intl.NumberFormat(locale).format(value);

  if (loading) return <div role="status" className="space-y-4" data-testid="dashboard-loading">
    <span className="sr-only">{t("dashboardLoading")}</span>
    <div className="h-28 rounded-md bg-muted animate-pulse" />
    <div className="h-48 rounded-md bg-muted/60 animate-pulse" />
  </div>;
  if (error || !data) return <Card role="alert" className="max-w-lg mx-auto p-6 space-y-4" data-testid="dashboard-error">
    <h1 className="text-lg font-semibold">{t("dashboardLoadFailed")}</h1>
    <p className="text-sm text-muted-foreground">{t(error ?? "dashboardLoadFailed")}</p>
    <div className="flex flex-wrap gap-3">
      <Button onClick={fetchSummary} variant="outline">{t("retry")}</Button>
      <Link href="/vocabulary" className={buttonVariants({ variant: "ghost" })}>{t("navVocabulary")}</Link>
    </div>
  </Card>;

  const due = data.dueReviewCount > 0;
  const empty = data.vocabularyTotal === 0;
  const isGoalReached = data.dailyGoalCompletedCount >= data.dailyGoalTarget;
  const actionTitle = due ? t("reviewDue", { count: n(data.dueReviewCount) }) : t(empty ? "addFirstWords" : "allCaughtUp");
  return <div className="space-y-6" data-testid="dashboard-content">
    <div>
      <h1 className="text-xl sm:text-2xl font-bold tracking-tight" data-testid="dashboard-greeting">{t("dashboardGreeting", { name: user?.displayName ?? "" })}</h1>
      <p className="mt-1 text-sm text-muted-foreground" data-testid="dashboard-date">{new Date(data.currentDate + "T00:00:00").toLocaleDateString(locale, { weekday: "long", year: "numeric", month: "long", day: "numeric" })}</p>
    </div>

    <Card className="border-border p-5 sm:p-6 space-y-3" data-testid="next-action-callout">
      <p className="text-xs font-semibold text-muted-foreground">{t("dashboardToday")}</p>
      <h2 className="text-lg sm:text-xl font-semibold">{actionTitle}</h2>
      <p className="text-sm text-muted-foreground">{t(due ? "reviewDescription" : empty ? "addFirstDescription" : "caughtUpDescription")}</p>
      <Link href={due ? "/review" : "/vocabulary"} data-testid={due ? "cta-review-btn" : "cta-vocab-btn"}
        className={`${buttonVariants()} max-w-full whitespace-normal h-auto min-h-9 py-2`}>
        {t(due ? "startReview" : empty ? "addFirstWords" : "exploreWords")}<ArrowRight className="size-4" aria-hidden="true" />
      </Link>
    </Card>

    <section aria-labelledby="practice-heading">
      <h2 id="practice-heading" className="text-sm font-semibold mb-3">{t("secondaryActivities")}</h2>
      <div className="flex flex-wrap gap-2">
        {([['/vocabulary', 'navVocabulary'], ['/dictation', 'navDictation'], ['/shadowing', 'navShadowing'], ['/quiz', 'navQuiz']] as const).map(([href, key]) =>
          <Link key={href} href={href} className={buttonVariants({ variant: "outline", size: "sm" })}>{t(key)}</Link>)}
      </div>
    </section>

    <Card className="p-5 space-y-3" data-testid="daily-goal-section">
      <div className="flex flex-wrap items-center justify-between gap-2"><h2 className="font-semibold">{t("dailyGoal")}</h2>{isGoalReached && <span className="text-xs" data-testid="goal-achieved-badge">{t("goalComplete")}</span>}</div>
      <div className="flex flex-wrap items-center justify-between gap-2"><p className="text-sm">{t("goalSummary", { done: n(data.dailyGoalCompletedCount), target: n(data.dailyGoalTarget) })}</p><span className="font-semibold" data-testid="goal-percent">{n(data.goalProgressPercentage)}%</span></div>
      <div className="w-full bg-muted rounded-full h-2 overflow-hidden" role="progressbar" aria-label={t("dailyGoal")} aria-valuenow={data.goalProgressPercentage} aria-valuemin={0} aria-valuemax={100}>
        <div className="bg-foreground h-full" style={{ width: `${Math.min(100, data.goalProgressPercentage)}%` }} />
      </div>
      <p className="text-xs text-muted-foreground">{isGoalReached ? t("goalEncouragement") : t("goalRemaining", { count: n(Math.max(0, data.dailyGoalTarget - data.dailyGoalCompletedCount)) })}</p>
    </Card>

    <dl className="grid grid-cols-2 lg:grid-cols-4 gap-3">
      {([
        ['vocab', 'vocabularyTotal', n(data.vocabularyTotal)], ['due', 'dueReviews', n(data.dueReviewCount)],
        ['actions', 'completedToday', n(data.dailyGoalCompletedCount)], ['streak', 'learningStreak', t('daysCount', { count: n(data.currentStreakDays) })],
      ] as const).map(([id, key, value]) => <div key={id} className="min-w-0 rounded-md border border-border p-4" data-testid={`kpi-${id}`}>
        <dt className="text-xs text-muted-foreground">{t(key)}</dt><dd className="mt-1 text-xl font-semibold" data-testid={`kpi-${id}-count`}>{value}</dd>
        {id === 'streak' && <p className="mt-1 text-xs text-muted-foreground">{t(data.streakStatus === 'ACTIVE_STUDIED_TODAY' ? 'streakToday' : data.streakStatus === 'ACTIVE_PENDING_TODAY' ? 'streakPending' : 'streakStart')}</p>}
      </div>)}
    </dl>

    <HeatmapCalendar data={heatmapData} isLoading={false} isError={!!heatmapError} errorKey={heatmapError} onRetry={fetchSummary} />

    <section className="space-y-3" aria-labelledby="recent-heading" data-testid="recent-activity-section">
      <h2 id="recent-heading" className="text-sm font-semibold">{t("recentActivity")}</h2>
      {data.recentSessions.length ? <ul className="divide-y divide-border border-y border-border">{data.recentSessions.map((session, index) => <li key={index} className="py-3 flex flex-wrap justify-between gap-2 text-xs">
        <span>{t(activityKeys[session.type] ?? "activityOther")} · {t("actionsCount", { count: n(session.count) })}</span>
        <time dateTime={session.completedAt} className="text-muted-foreground">{new Date(session.completedAt).toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit", timeZone: heatmapData?.userTimezone ?? user?.timezone ?? 'UTC' })}</time>
      </li>)}</ul> : <p className="rounded-md border border-border p-4 text-sm text-muted-foreground">{t("recentEmpty")}</p>}
    </section>
  </div>;
}
