"use client";

import { useEffect, useState, type ReactNode } from "react";
import { useSearchParams } from "next/navigation";
import { collectionsApi, type CollectionItem, type CollectionPageResponse } from "@/lib/api/collections";
import { useLocale } from "@/lib/i18n/locale-provider";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { PasteImport } from "./paste-import";

interface Props { renderFile: (id: string, blocked: boolean, onBusy: (busy: boolean) => void) => ReactNode }
export function ImportWorkspace(props: Props) {
  const params = useSearchParams();
  const requestedId = params.has("collectionId") ? params.getAll("collectionId").join(",") : undefined;
  return <Workflow key={requestedId ?? "library"} {...props} requestedId={requestedId} initialPaste={params.get("mode") === "paste"} />;
}
function Workflow({ renderFile, requestedId, initialPaste }: Props & { requestedId?: string; initialPaste: boolean }) {
  const { t } = useLocale();
  const [mode, setMode] = useState<"paste" | "file">(initialPaste ? "paste" : "file");
  const [selected, setSelected] = useState(requestedId ?? "");
  const [requestedCheck, setRequestedCheck] = useState(requestedId !== undefined);
  const [known, setKnown] = useState<CollectionItem | null>(null);
  const [data, setData] = useState<CollectionPageResponse | null>(null);
  const [page, setPage] = useState(0);
  const [listFailed, setListFailed] = useState(false);
  const [destinationError, setDestinationError] = useState(false);
  const [checking, setChecking] = useState(!!requestedId);
  const [busy, setBusy] = useState(false);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let cancelled = false; setListFailed(false);
    collectionsApi.list({ page, size: 20 }).then(result => { if (!cancelled) setData(result); })
      .catch(() => { if (!cancelled) setListFailed(true); });
    return () => { cancelled = true; };
  }, [page, reload]);
  useEffect(() => {
    let cancelled = false; setDestinationError(false); setKnown(null);
    if (!selected) { setChecking(false); setDestinationError(requestedCheck); return; }
    setChecking(true);
    collectionsApi.getById(selected).then(result => { if (!cancelled) setKnown(result); })
      .catch(() => { if (!cancelled) setDestinationError(true); })
      .finally(() => { if (!cancelled) setChecking(false); });
    return () => { cancelled = true; };
  }, [selected, requestedCheck, reload]);
  const choose = (id: string) => { setSelected(id); setRequestedCheck(false); };
  return <div className="learning-module space-y-5">
    <section className="space-y-3 rounded-lg border border-border p-4">
      <Label htmlFor="target-collection">{t("learnAssignToCollectionOptional")}</Label>
      <select id="target-collection" data-testid="collection-select" className="w-full rounded border border-border bg-background p-2 text-sm"
        value={selected} onChange={event => choose(event.target.value)} disabled={busy}>
        <option value="">{t("learnNoCollectionLibraryOnly")}</option>
        {selected && !data?.content.some(item => item.id === selected) && <option value={selected}>{known?.title ?? t("pasteRequestedDestination")}</option>}
        {data?.content.map(item => <option key={item.id} value={item.id}>{item.title}</option>)}
      </select>
      {checking && <p role="status" className="text-sm">{t("pasteCheckingDestination")}</p>}
      {(listFailed || destinationError) && <div role="alert" className="space-y-2 text-sm">
        <p>{t(destinationError ? "pasteDestinationUnavailable" : "pasteDestinationsFailed")}</p>
        <Button variant="outline" size="sm" disabled={busy} onClick={() => setReload(value => value + 1)}>{t("learnRetry")}</Button>
        {destinationError && <Button variant="outline" size="sm" disabled={busy} onClick={() => choose("")}>{t("learnNoCollectionLibraryOnly")}</Button>}
      </div>}
      {data && data.totalPages > 1 && <div className="flex flex-wrap gap-2">
        <Button variant="outline" size="sm" disabled={busy || page === 0} onClick={() => setPage(page - 1)}>{t("folderPrevious")}</Button>
        <span>{page + 1} / {data.totalPages}</span>
        <Button variant="outline" size="sm" disabled={busy || page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>{t("folderNext")}</Button>
      </div>}
    </section>
    <div className="flex flex-wrap gap-2" aria-label={t("pasteInputMode")}>
      <Button variant={mode === "paste" ? "default" : "outline"} aria-pressed={mode === "paste"} disabled={busy} onClick={() => setMode("paste")}>{t("pasteMode")}</Button>
      <Button variant={mode === "file" ? "default" : "outline"} aria-pressed={mode === "file"} disabled={busy} onClick={() => setMode("file")}>{t("pasteFileMode")}</Button>
    </div>
    <div hidden={mode !== "paste"}><PasteImport collectionId={selected || undefined} destinationBlocked={checking || destinationError}
      onBusyChange={setBusy} /></div>
    <div hidden={mode !== "file"}>{renderFile(selected, checking || destinationError, setBusy)}</div>
  </div>;
}
