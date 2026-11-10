"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useLocale } from "@/lib/i18n/locale-provider";
import { reviewApi } from "@/lib/api/review";
import { Button, buttonVariants } from "@/components/ui/button";

export function StudySetLearning({ collectionId, wordCount, version }: {
  collectionId: string; wordCount: number; version: string;
}) {
  const { t, locale } = useLocale();
  const [due, setDue] = useState<number | null>(null);
  const [failed, setFailed] = useState(false);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let cancelled = false;
    setDue(null); setFailed(false);
    reviewApi.getDueCount(collectionId).then(result => { if (!cancelled) setDue(result.totalDue); })
      .catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; };
  }, [collectionId, wordCount, version, reload]);
  const count = (value: number) => new Intl.NumberFormat(locale).format(value);
  return <section className="space-y-4 rounded-lg border border-border p-4" aria-labelledby="study-learning-title">
    <h2 id="study-learning-title" className="text-lg font-semibold">{t("studyLearning")}</h2>
    <dl className="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <div><dt className="text-sm text-muted-foreground">{t("studyTotal")}</dt><dd className="text-2xl font-semibold tabular-nums" data-testid="study-total">{count(wordCount)}</dd></div>
      <div><dt className="text-sm text-muted-foreground">{t("studyDue")}</dt><dd className="text-2xl font-semibold tabular-nums" data-testid="study-due" aria-live="polite">{due === null ? "—" : count(due)}</dd></div>
    </dl>
    <p className="text-sm text-muted-foreground">{t("studyMetricsHelp")}</p>
    {failed && <div className="space-y-2"><p role="alert" className="text-sm">{t("studyDueFailed")}</p>
      <Button size="sm" variant="outline" onClick={() => setReload(value => value + 1)}>{t("learnRetry")}</Button></div>}
    <div className="flex flex-wrap gap-3">
      <Link className={buttonVariants()} href={"/flashcards?collectionId=" + encodeURIComponent(collectionId)}>{t("studyFlashcards")}</Link>
      <Link className={buttonVariants({ variant: "outline" })} href={"/review?collectionId=" + encodeURIComponent(collectionId)}>{t("learnReviewStudySet")}</Link>
    </div>
    <p className="text-xs text-muted-foreground">{t("studyPracticeHelp")}</p>
  </section>;
}
