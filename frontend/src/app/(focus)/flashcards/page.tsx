"use client";

import { Suspense, useCallback, useEffect, useRef, useState, type KeyboardEvent } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { collectionsApi, type CollectionItem } from "@/lib/api/collections";
import type { VocabularyPageResponse } from "@/lib/api/vocabulary";
import { useLocale } from "@/lib/i18n/locale-provider";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

const PAGE_SIZE = 50;
export default function FlashcardsPage() {
  return <Suspense fallback={null}><FlashcardRoute /></Suspense>;
}
function FlashcardRoute() {
  const params = useSearchParams();
  const { t } = useLocale();
  const id = params.getAll("collectionId").join(",");
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id))
    return <section className="space-y-4"><h1 className="text-xl font-semibold">{t("studyChooseSet")}</h1>
      <Link className="underline" href="/collections">{t("navCollections")}</Link></section>;
  return <FlashcardSession key={id} collectionId={id} />;
}
function FlashcardSession({ collectionId }: { collectionId: string }) {
  const { t } = useLocale();
  const [collection, setCollection] = useState<CollectionItem | null>(null);
  const [data, setData] = useState<VocabularyPageResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [page, setPage] = useState(0);
  const [index, setIndex] = useState(0);
  const [revealed, setRevealed] = useState(false);
  const [complete, setComplete] = useState(false);
  const generation = useRef(0);
  const completion = useRef<HTMLHeadingElement>(null);
  const load = useCallback(async (requestedPage: number) => {
    const current = ++generation.current;
    setPage(requestedPage); setLoading(true); setFailed(false);
    try {
      const [set, words] = await Promise.all([
        collectionsApi.getById(collectionId),
        collectionsApi.listVocabularies(collectionId, { page: requestedPage, size: PAGE_SIZE }),
      ]);
      if (generation.current !== current) return;
      setCollection(set); setData(words); setIndex(0); setRevealed(false); setComplete(false);
    } catch {
      if (generation.current === current) { setFailed(true); setData(null); setCollection(null); }
    } finally { if (generation.current === current) setLoading(false); }
  }, [collectionId]);
  const invalidate = useCallback(() => { generation.current++; }, []);
  useEffect(() => { load(0); return invalidate; }, [load, invalidate]);
  useEffect(() => { if (complete) completion.current?.focus(); }, [complete]);
  const rows = data?.content ?? [];
  const card = rows[index];
  const restart = () => { setIndex(0); setRevealed(false); setComplete(false); };
  const previous = () => { setIndex(old => Math.max(0, old - 1)); setRevealed(false); };
  const next = () => {
    if (index + 1 < rows.length) { setIndex(index + 1); setRevealed(false); }
    else setComplete(true);
  };
  const keyboard = (event: KeyboardEvent<HTMLDivElement>) => {
    // Local canvas shortcuts only. Native inputs, selects, buttons and editable descendants own their events.
    if (event.target !== event.currentTarget || event.currentTarget.isContentEditable ||
      event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey || event.repeat) return;
    if (["Space", "Enter"].includes(event.code)) { event.preventDefault(); setRevealed(old => !old); }
    if (event.code === "ArrowLeft") { event.preventDefault(); previous(); }
    if (event.code === "ArrowRight") { event.preventDefault(); next(); }
  };
  return <section className="w-full min-w-0 space-y-5" aria-labelledby="flashcard-title">
    <nav className="flex flex-wrap gap-3 text-sm" aria-label={t("learnReviewContext")}>
      <Link className="underline" href={"/collections/" + encodeURIComponent(collectionId)}>{t("learnBackToStudySet")}</Link>
      <Link className="underline" href={"/review?collectionId=" + encodeURIComponent(collectionId)}>{t("learnReviewStudySet")}</Link>
    </nav>
    <h1 id="flashcard-title" className="text-2xl font-semibold break-words">{t("studyFlashcards")}</h1>
    {collection && <p className="break-words text-muted-foreground">{collection.title}</p>}
    <p className="text-sm text-muted-foreground">{t("studyPracticeHelp")}</p>
    {loading ? <p role="status" data-testid="flashcards-loading">{t("studyLoading")}</p>
      : failed ? <div className="space-y-3"><p role="alert">{t("studyUnavailable")}</p><Button onClick={() => load(page)}>{t("learnRetry")}</Button></div>
      : !rows.length ? <div className="space-y-3" data-testid="flashcards-empty"><p>{t("studyEmpty")}</p>
        </div>
      : complete ? <Card className="space-y-4 p-5" data-testid="flashcards-complete">
        <h2 ref={completion} tabIndex={-1} className="text-xl font-semibold focus-visible:outline-2">{t("studyComplete")}</h2>
        <p>{t("studyCompleteHelp", { count: rows.length })}</p>
        <div className="flex flex-wrap gap-3">
          <Button onClick={restart}>{t("studyRestart")}</Button>
          {data && page + 1 < data.totalPages && <Button variant="outline" onClick={() => load(page + 1)}>{t("studyNextBatch")}</Button>}
          <Link className={buttonVariants({ variant: "outline" })} href={"/collections/" + encodeURIComponent(collectionId)}>{t("learnBackToStudySet")}</Link>
        </div>
      </Card> : card && <>
        <p className="text-sm tabular-nums" role="status" data-testid="flashcards-position">
          {t("studyPosition", { position: index + 1, count: rows.length, batch: page + 1, total: data?.totalElements ?? rows.length })}
        </p>
        <div role="group" aria-label={t("studyCanvas")} tabIndex={0} onKeyDown={keyboard}
          className="rounded-lg border border-border p-5 sm:p-10 space-y-5 text-center break-words focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ring"
          data-testid="flashcards-canvas">
          <p className="text-xs text-muted-foreground">{card.partOfSpeech}</p>
          <h2 className="text-3xl sm:text-4xl font-semibold" data-testid="flashcards-term">{card.term}</h2>
          {card.phoneticIpa && <p className="font-mono text-sm">{card.phoneticIpa}</p>}
          {revealed && <div id="practice-answer" className="space-y-3" data-testid="flashcards-answer">
            <p className="text-xl font-medium">{card.vietnameseMeaning}</p>
            {card.englishDefinition && <p>{card.englishDefinition}</p>}
            {card.exampleSentence && <p className="text-sm">{card.exampleSentence}</p>}
            {card.exampleTranslation && <p className="text-sm text-muted-foreground">{card.exampleTranslation}</p>}
          </div>}
          <Button variant="outline" aria-expanded={revealed} aria-controls="practice-answer" onClick={() => setRevealed(old => !old)}>
            {t(revealed ? "studyHide" : "learnRevealAnswer")}
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">{t("studyKeyboard")}</p>
        <div className="flex flex-wrap gap-3">
          <Button variant="outline" disabled={index === 0} onClick={previous}>{t("studyPrevious")}</Button>
          <Button onClick={next}>{t(index + 1 === rows.length ? "studyFinish" : "studyNext")}</Button>
          <Button variant="ghost" onClick={restart}>{t("studyRestart")}</Button>
        </div>
      </>}
    {!loading && <div className="space-y-2 border-t border-border pt-4">
      <p className="text-xs text-muted-foreground">{t("studySnapshot")}</p>
      <Button size="sm" variant="outline" onClick={() => load(0)}>{t("studyRefresh")}</Button>
    </div>}
  </section>;
}
