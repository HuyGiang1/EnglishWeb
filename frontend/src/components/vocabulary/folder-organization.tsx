"use client";

import { useCallback, useEffect, useState } from "react";
import { foldersApi, type FolderItem, type FolderPage } from "@/lib/api/folders";
import { collectionsApi, type CollectionItem } from "@/lib/api/collections";
import { useLocale } from "@/lib/i18n/locale-provider";
import type { TranslationKey } from "@/lib/i18n/dictionaries";
import { learningErrorKey } from "@/lib/i18n/learning-errors";
import { ApiError } from "@/lib/api/client";
import { LanguageSwitcher } from "@/components/auth/language-switcher";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "@/components/ui/dialog";

function folderError(error: unknown): TranslationKey {
  if (error instanceof ApiError && error.code === "FOLDER_NAME_EXISTS") return "folderNameConflict";
  if (error instanceof ApiError && error.code === "NOT_FOUND") return "folderUnavailable";
  return learningErrorKey(error, "folderRequestFailed");
}

export function FolderPanel({ scope, onSelect, version, onChanged }: {
  scope: string; onSelect: (value: string) => void; version: number; onChanged: () => void;
}) {
  const { t } = useLocale();
  const [page, setPage] = useState(0);
  const [data, setData] = useState<FolderPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<TranslationKey | null>(null);
  const [reload, setReload] = useState(0);
  const [dialog, setDialog] = useState<{ mode: "create" | "edit" | "delete"; folder?: FolderItem } | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<TranslationKey | null>(null);
  const [notice, setNotice] = useState<TranslationKey | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true); setLoadError(null);
    foldersApi.list(page).then(result => {
      if (cancelled) return;
      if (!result.content.length && page > 0) { setPage(page - 1); return; }
      setData(result);
    }).catch(err => { if (!cancelled) setLoadError(folderError(err)); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [page, version, reload]);

  const open = (mode: "create" | "edit" | "delete", folder?: FolderItem) => {
    setDialog({ mode, folder }); setName(folder?.name ?? "");
    setDescription(folder?.description ?? ""); setError(null); setNotice(null);
  };
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); if (!dialog || busy) return;
    const normalized = name.replace(/[\t\n\v\f\r ]+/g, " ").replace(/^ +| +$/g, "");
    if (dialog.mode !== "delete" && (!normalized.trim() || normalized.length > 100)) {
      setError("folderInvalidName"); return;
    }
    setBusy(true); setError(null);
    try {
      if (dialog.mode === "delete") {
        await foldersApi.delete(dialog.folder!.id);
        onSelect("unfiled");
      } else if (dialog.mode === "edit") {
        await foldersApi.update(dialog.folder!.id, { name: normalized, description: description || null });
      } else await foldersApi.create({ name: normalized, description: description || null });
      setDialog(null); setNotice("folderSaved"); setReload(value => value + 1); onChanged();
    } catch (err) { setError(folderError(err)); } finally { setBusy(false); }
  };
  return (
    <section aria-labelledby="folder-heading" className="space-y-3 rounded-lg border border-border p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="folder-heading" className="font-semibold">{t("folderHeading")}</h2>
        <Button size="sm" variant="outline" onClick={() => open("create")}>{t("folderCreate")}</Button>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button size="sm" variant={scope === "all" ? "default" : "outline"} aria-pressed={scope === "all"} onClick={() => onSelect("all")}>{t("folderAllStudySets")}</Button>
        <Button size="sm" variant={scope === "unfiled" ? "default" : "outline"} aria-pressed={scope === "unfiled"} onClick={() => onSelect("unfiled")}>{t("folderUnfiled")}</Button>
      </div>
      {notice && <p role="status" className="text-sm">{t(notice)}</p>}
      {loading && <p role="status" className="text-sm text-muted-foreground">{t("folderLoading")}</p>}
      {loadError && <div role="alert" className="text-sm space-y-2"><p>{t(loadError)}</p><Button size="sm" variant="outline" onClick={() => setReload(value => value + 1)}>{t("learnRetry")}</Button></div>}
      {!loading && !loadError && data?.content.length === 0 && <p className="text-sm text-muted-foreground">{t("folderNone")}</p>}
      {!loadError && <ul className="space-y-2">
        {data?.content.map(folder => <li key={folder.id} className="flex flex-wrap items-center justify-between gap-2 border-t border-border pt-2">
          <button type="button" aria-pressed={scope === folder.id} onClick={() => onSelect(folder.id)}
            className="min-w-0 max-w-full text-left rounded px-1 py-1 focus-visible:outline-2 focus-visible:outline-ring">
            <span className="break-words font-medium">{folder.name}</span>
            <span className="ml-2 text-xs text-muted-foreground">{t("folderSetCount", { count: folder.studySetCount })}</span>
          </button>
          <div className="flex max-w-full flex-wrap gap-1">
            <Button size="sm" variant="ghost" onClick={() => open("edit", folder)} aria-label={t("folderRenameNamed", { name: folder.name })}>{t("folderRename")}</Button>
            <Button size="sm" variant="ghost" onClick={() => open("delete", folder)} aria-label={t("folderDeleteNamed", { name: folder.name })}>{t("folderDelete")}</Button>
          </div>
        </li>)}
      </ul>}
      {data && data.totalPages > 1 && <div className="flex flex-wrap items-center gap-2">
        <Button size="sm" variant="outline" disabled={loading || page === 0} onClick={() => setPage(page - 1)}>{t("folderPrevious")}</Button>
        <span className="text-sm">{page + 1} / {data.totalPages}</span>
        <Button size="sm" variant="outline" disabled={loading || page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>{t("folderNext")}</Button>
      </div>}
      <Dialog open={dialog !== null} onOpenChange={value => { if (!value && !busy) setDialog(null); }}>
        <DialogContent className="learning-module max-h-[90dvh] overflow-y-auto" showCloseButton={!busy}>
          <form noValidate onSubmit={submit} className="space-y-4">
            <DialogHeader>
              <LanguageSwitcher />
              <DialogTitle>{t(dialog?.mode === "delete" ? "folderDelete" : dialog?.mode === "edit" ? "folderRename" : "folderCreate")}</DialogTitle>
              <DialogDescription>{t(dialog?.mode === "delete" ? "folderDeleteExplanation" : "folderDescriptionHelp")}</DialogDescription>
            </DialogHeader>
            {dialog?.mode === "delete" ? <p className="break-words font-medium">{dialog.folder?.name}</p> : <>
              <div className="space-y-2"><Label htmlFor="folder-name">{t("folderName")}</Label>
                <Input id="folder-name" value={name} onChange={event => setName(event.target.value)} maxLength={100} required aria-invalid={!!error} aria-describedby={error ? "folder-error" : undefined} /></div>
              <div className="space-y-2"><Label htmlFor="folder-description">{t("learnDescriptionOptional")}</Label>
                <Textarea id="folder-description" value={description} onChange={event => setDescription(event.target.value)} maxLength={1000} /></div>
            </>}
            {error && <p id="folder-error" role="alert" className="text-sm text-destructive">{t(error)}</p>}
            <DialogFooter>
              <Button type="button" variant="outline" disabled={busy} onClick={() => setDialog(null)}>{t("learnCancel")}</Button>
              <Button type="submit" variant={dialog?.mode === "delete" ? "destructive" : "default"} disabled={busy}>{t(busy ? "learnSaving" : dialog?.mode === "delete" ? "folderDelete" : "learnSaveChanges")}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </section>
  );
}

export function MoveStudySetDialog({ item, onClose, onMoved }: {
  item: CollectionItem | null; onClose: () => void; onMoved: () => void;
}) {
  const { t } = useLocale();
  const [page, setPage] = useState(0);
  const [folders, setFolders] = useState<FolderPage | null>(null);
  const [destination, setDestination] = useState("");
  const [destinationName, setDestinationName] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<TranslationKey | null>(null);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    if (item) { setDestination(item.folderId ?? ""); setDestinationName(""); setPage(0); setError(null); }
  }, [item]);
  useEffect(() => {
    if (!item) return;
    let cancelled = false;
    setLoading(true); setError(null);
    foldersApi.list(page).then(result => { if (!cancelled) setFolders(result); })
      .catch(err => { if (!cancelled) setError(folderError(err)); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [item, page, reload]);
  const close = useCallback(() => { if (!busy) onClose(); }, [busy, onClose]);
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); if (!item || busy || loading) return;
    setBusy(true); setError(null);
    try { await collectionsApi.move(item.id, destination || null); onMoved(); onClose(); }
    catch (err) { setError(folderError(err)); } finally { setBusy(false); }
  };
  return <Dialog open={item !== null} onOpenChange={value => { if (!value) close(); }}>
    <DialogContent className="learning-module max-h-[90dvh] overflow-y-auto" showCloseButton={!busy}>
      <form onSubmit={submit} className="space-y-4">
        <DialogHeader><LanguageSwitcher /><DialogTitle>{t("folderMoveStudySet")}</DialogTitle>
          <DialogDescription>{t("folderMoveExplanation")}</DialogDescription></DialogHeader>
        <p className="break-words">{item?.title}</p>
        <Label htmlFor="folder-destination">{t("folderDestination")}</Label>
        <select id="folder-destination" className="w-full rounded-md border border-border bg-background p-2 text-sm focus-visible:outline-2 focus-visible:outline-ring"
          value={destination} onChange={event => { setDestination(event.target.value); setDestinationName(event.target.selectedOptions[0].text); }} disabled={loading || busy}>
          <option value="">{t("folderUnfiled")}</option>
          {destination && !folders?.content.some(f => f.id === destination) && <option value={destination}>{destinationName || t("folderCurrent")}</option>}
          {folders?.content.map(folder => <option key={folder.id} value={folder.id}>{folder.name}</option>)}
        </select>
        {loading && <p role="status">{t("folderLoading")}</p>}
        {error && <div role="alert" className="space-y-2 text-sm text-destructive"><p>{t(error)}</p><Button type="button" variant="outline" size="sm" onClick={() => setReload(value => value + 1)}>{t("learnRetry")}</Button></div>}
        {folders && folders.totalPages > 1 && <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" size="sm" disabled={loading || page === 0} onClick={() => setPage(page - 1)}>{t("folderPrevious")}</Button>
          <span>{page + 1} / {folders.totalPages}</span>
          <Button type="button" variant="outline" size="sm" disabled={loading || page + 1 >= folders.totalPages} onClick={() => setPage(page + 1)}>{t("folderNext")}</Button>
        </div>}
        <DialogFooter><Button type="button" variant="outline" disabled={busy} onClick={close}>{t("learnCancel")}</Button>
          <Button type="submit" disabled={busy || loading || !folders}>{t(busy ? "learnSaving" : "folderMoveStudySet")}</Button></DialogFooter>
      </form>
    </DialogContent>
  </Dialog>;
}
