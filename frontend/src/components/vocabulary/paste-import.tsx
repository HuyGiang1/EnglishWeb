"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useLocale } from "@/lib/i18n/locale-provider";
import type { TranslationKey } from "@/lib/i18n/dictionaries";
import { ApiError } from "@/lib/api/client";
import { pasteImportApi, PASTE_TEXT_BYTES, PASTE_MAX_ROWS, type PasteField, type PasteRow, type PastePreview, type PastePreviewRow, type PasteResult } from "@/lib/api/import";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const issueKeys: Record<string, TranslationKey> = {
  MISSING_TERM: "pasteMissingTerm", MISSING_MEANING: "pasteMissingMeaning", TERM_TOO_LONG: "pasteTermLong",
  MEANING_TOO_LONG: "pasteMeaningLong", IPA_TOO_LONG: "pasteIpaLong", INVALID_POS: "pasteInvalidPos",
  INVALID_FIELDS: "pasteInvalidFields", AMBIGUOUS_EXISTING: "pasteAmbiguousExisting",
  FIRST_ROW_KEPT: "pasteFirstKept", PRESERVE_EXISTING: "pastePreserveExisting", RAGGED_ROW: "pasteRagged",
};
const statusKeys: Record<string, TranslationKey> = {
  NEW: "pasteNew", EXISTING: "pasteExisting", ALREADY_PRESENT: "pasteAlreadyPresent",
  DUPLICATE_INPUT: "pasteDuplicate", INVALID: "pasteInvalid", EXCLUDED: "pasteExcluded",
  AMBIGUOUS_EXISTING: "pasteAmbiguousExisting", UNCHECKED: "pasteUnchecked",
};
function errorKey(error: unknown): TranslationKey {
  if (!(error instanceof ApiError)) return "pasteRequestFailed";
  if (["FILE_TOO_LARGE", "PAYLOAD_TOO_LARGE", "ROW_LIMIT_EXCEEDED", "COLUMN_LIMIT_EXCEEDED"].includes(error.code)) return "pasteLimit";
  if (error.code === "INVALID_MAPPING") return "pasteMappingError";
  if (["NOT_FOUND", "INVALID_DESTINATION"].includes(error.code)) return "pasteDestinationUnavailable";
  if (error.code === "PARSER_ERROR") return "pasteParseError";
  if (error.code === "INVALID_ITEM_DATA") return "pasteInvalidBatch";
  return "pasteRequestFailed";
}
const fields: PasteField[] = ["term", "vietnameseMeaning", "phoneticIpa", "partOfSpeech", "ignore"];
const fieldLabels: Record<PasteField, TranslationKey> = {
  term: "learnTerm", vietnameseMeaning: "pasteMeaning", phoneticIpa: "pasteIpa", partOfSpeech: "pastePos", ignore: "pasteIgnore",
};
function payload(rows: PastePreviewRow[]): PasteRow[] {
  return rows.map(({ rowNumber, term, vietnameseMeaning, phoneticIpa, partOfSpeech, excluded }) =>
    ({ rowNumber, term, vietnameseMeaning, phoneticIpa, partOfSpeech, excluded }));
}

export function PasteImport({ collectionId, destinationBlocked, onBusyChange }: {
  collectionId?: string; destinationBlocked: boolean; onBusyChange: (busy: boolean) => void;
}) {
  const { t } = useLocale();
  const [raw, setRaw] = useState("");
  const [delimiter, setDelimiter] = useState("AUTO");
  const [mapping, setMapping] = useState<PasteField[] | null>(null);
  const [skipHeader, setSkipHeader] = useState(false);
  const [parsed, setParsed] = useState<PastePreview | null>(null);
  const [rows, setRows] = useState<PastePreviewRow[]>([]);
  const [validated, setValidated] = useState(false);
  const [warningsAccepted, setWarningsAccepted] = useState(false);
  const [policyAccepted, setPolicyAccepted] = useState(false);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const revision = useRef(0);
  const [error, setError] = useState<TranslationKey | null>(null);
  const [result, setResult] = useState<PasteResult | null>(null);
  const [page, setPage] = useState(0);
  const textBytes = new TextEncoder().encode(raw).length;
  const active = rows.filter(row => !row.excluded);
  const invalid = active.some(row => ["INVALID", "AMBIGUOUS_EXISTING"].includes(row.status));
  const warnings = parsed?.warnings ?? [];
  const warningRequired = warnings.length > 0;

  useEffect(() => {
    revision.current++; setValidated(false); setResult(null); setError(null);
    setPolicyAccepted(false);
  }, [collectionId]);

  const invalidate = (clear: boolean) => {
    revision.current++; setValidated(false); setResult(null); setError(null);
    setPolicyAccepted(false); setWarningsAccepted(false);
    if (clear) { setRows([]); setPage(0); }
  };
  const run = async (operation: "parse" | "validate" | "confirm") => {
    if (busyRef.current || destinationBlocked || textBytes > PASTE_TEXT_BYTES) return;
    busyRef.current = true; setBusy(true); onBusyChange(true); setError(null);
    const current = revision.current;
    try {
      if (operation === "parse") {
        const response = await pasteImportApi.preview({ text: raw, delimiter, mapping, skipHeader, collectionId });
        if (revision.current !== current) return;
        setParsed(response); setMapping(response.mapping);
        setRows(response.rows); setValidated(true); setPage(0); setWarningsAccepted(false); setPolicyAccepted(false);
      } else if (operation === "validate") {
        const response = await pasteImportApi.validate({ rows: payload(rows), collectionId });
        if (revision.current !== current) return;
        setRows(response.rows.map(row => ({ ...row, cells: rows.find(old => old.rowNumber === row.rowNumber)?.cells ?? [] })));
        setValidated(true);
      } else {
        if (!validated || invalid || !active.length || !policyAccepted || (warningRequired && !warningsAccepted)) return;
        const response = await pasteImportApi.confirm({ rows: payload(rows), collectionId, conflictPolicy: "PRESERVE_EXISTING" });
        if (revision.current !== current) return;
        setResult(response);
      }
    } catch (err) {
      if (revision.current === current) {
        setError(errorKey(err));
        if (operation === "confirm") setValidated(false);
      }
    } finally { busyRef.current = false; setBusy(false); onBusyChange(false); }
  };
  const edit = (number: number, updates: Partial<PasteRow>) => {
    invalidate(false);
    setRows(old => old.map(row => row.rowNumber === number ? { ...row, ...updates, status: updates.excluded ? "EXCLUDED" : "UNCHECKED", issues: [] } : row));
  };
  const reset = () => {
    invalidate(true); setRaw(""); setParsed(null); setMapping(null); setDelimiter("AUTO"); setSkipHeader(false);
  };

  if (result) return <section className="space-y-4 rounded-lg border border-border p-4" data-testid="paste-result">
    <h1 className="text-xl font-semibold">{t("pasteComplete")}</h1>
    <dl className="grid grid-cols-1 sm:grid-cols-2 gap-3">
      {([
        ["pasteCreated", result.createdCount], ["pasteLinked", result.linkedCount], ["pastePresentCount", result.alreadyPresentCount],
        ["pasteDuplicatesCount", result.duplicateCount], ["pasteExcludedCount", result.excludedCount],
      ] as const).map(([key, value]) => <div key={key}><dt className="text-sm text-muted-foreground">{t(key)}</dt><dd className="font-semibold">{value}</dd></div>)}
    </dl>
    <p className="text-sm text-muted-foreground">{t("pasteRetryNotice")}</p>
    <div className="flex flex-wrap gap-3">
      {result.collectionId && <Link className="underline" href={"/collections/" + encodeURIComponent(result.collectionId)}>{t("learnBackToStudySet")}</Link>}
      <Button variant="outline" onClick={reset}>{t("pasteStartOver")}</Button>
    </div>
  </section>;

  return <section className="space-y-5" aria-labelledby="paste-title">
    <div><h1 id="paste-title" className="text-xl font-semibold">{t("pasteTitle")}</h1>
      <p className="text-sm text-muted-foreground">{t("pasteHelp")}</p></div>
    <div className="space-y-2">
      <Label htmlFor="paste-text">{t("pasteText")}</Label>
      <Textarea id="paste-text" rows={7} value={raw} disabled={busy}
        onChange={event => { setRaw(event.target.value); invalidate(true); setParsed(null); }}
        aria-describedby="paste-limits" placeholder={"analyze : phân tích\nsignificant : đáng kể"} />
      <p id="paste-limits" className="text-xs text-muted-foreground">{t("pasteLimits")}</p>
      {textBytes > PASTE_TEXT_BYTES && <p role="alert">{t("pasteLimit")}</p>}
    </div>
    <div className="flex flex-wrap items-end gap-4">
      <div className="min-w-0 w-full sm:w-auto space-y-2"><Label htmlFor="paste-delimiter">{t("pasteDelimiter")}</Label>
        <select id="paste-delimiter" className="w-full max-w-full rounded border border-border bg-background p-2 text-sm" disabled={busy} value={delimiter}
          onChange={event => { setDelimiter(event.target.value); setMapping(null); setParsed(null); invalidate(true); }}>
          {(["AUTO", "TAB", "COLON", "SEMICOLON", "COMMA", "NONE"] as const).map(value =>
            <option value={value} key={value}>{t(({ AUTO: "pasteAuto", TAB: "pasteTab", COLON: "pasteColon", SEMICOLON: "pasteSemicolon", COMMA: "pasteComma", NONE: "pasteNone" } as const)[value])}</option>)}
        </select>
      </div>
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={skipHeader} disabled={busy}
        onChange={event => { setSkipHeader(event.target.checked); invalidate(true); }} />{t("pasteSkipHeader")}</label>
      <Button onClick={() => run("parse")} disabled={busy || destinationBlocked || !raw.trim() || textBytes > PASTE_TEXT_BYTES}>{t("pasteParse")}</Button>
    </div>
    <p className="text-xs text-muted-foreground">{t("pasteColonHelp")}</p>
    {parsed && <p className="text-xs text-muted-foreground">{t("pasteSuggestedDelimiter", { delimiter: t(({ TAB: "pasteTab", COLON: "pasteColon", SEMICOLON: "pasteSemicolon", COMMA: "pasteComma", NONE: "pasteNone" } as Record<string, TranslationKey>)[parsed.delimiter] ?? "pasteNone") })}</p>}
    {mapping && <fieldset className="space-y-3 rounded-lg border border-border p-3">
      <legend className="px-1 font-medium">{t("pasteMapping")}</legend>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {mapping.map((field, index) => <div key={index} className="space-y-1">
          <Label htmlFor={"paste-column-" + index}>{t("pasteColumn", { number: index + 1 })}</Label>
          <select id={"paste-column-" + index} className="w-full rounded border border-border bg-background p-2 text-sm" value={field} disabled={busy}
            onChange={event => { setMapping(mapping.map((old, i) => i === index ? event.target.value as PasteField : old)); invalidate(true); }}>
            {fields.map(field => <option key={field} value={field}>{t(fieldLabels[field])}</option>)}
          </select>
        </div>)}
      </div>
      <p className="text-xs text-muted-foreground">{t("pasteReparseMapping")}</p>
    </fieldset>}
    {error && <p role="alert" className="text-sm text-destructive">{t(error)}</p>}
    {busy && <p role="status">{t("pasteWorking")}</p>}
    {!!rows.length && <div className="space-y-4">
      <p role="status" className="text-sm">{t(validated ? "pastePreviewOnly" : "pasteValidationStale", { count: rows.length })}</p>
      <p className="text-xs text-muted-foreground">{t("pastePosHelp")}</p>
      {warnings.map(warning => <p key={warning} className="text-sm" role="alert">
        {t(warning === "AMBIGUOUS_DELIMITER" ? "pasteDelimiterWarning" : warning === "UNUSED_COLUMNS" ? "pasteUnusedWarning" : "pasteRagged")}
      </p>)}
      {rows.slice(page * 25, page * 25 + 25).map(row => <article key={row.rowNumber} className="space-y-3 rounded-lg border border-border p-3" data-testid={"paste-row-" + row.rowNumber}>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-sm font-semibold">{t("pasteSourceRow", { number: row.rowNumber })}</h2>
          <span className="text-xs rounded border border-border px-2 py-1">{t(validated ? statusKeys[row.status] ?? "pasteUnchecked" : row.excluded ? "pasteExcluded" : "pasteUnchecked")}</span>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={!row.excluded} disabled={busy}
            aria-label={t("pasteIncludeRow", { number: row.rowNumber })} onChange={event => edit(row.rowNumber, { excluded: !event.target.checked })} />{t("pasteInclude")}</label>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {(["term", "vietnameseMeaning", "phoneticIpa", "partOfSpeech"] as const).map(field => <div key={field} className="space-y-1">
            <Label htmlFor={"paste-" + field + "-" + row.rowNumber}>{t(fieldLabels[field])}</Label>
            {field === "vietnameseMeaning"
              ? <Textarea id={"paste-" + field + "-" + row.rowNumber} value={row[field]} rows={2} disabled={busy || row.excluded} onChange={event => edit(row.rowNumber, { [field]: event.target.value })} />
              : <Input id={"paste-" + field + "-" + row.rowNumber} value={row[field]} disabled={busy || row.excluded} onChange={event => edit(row.rowNumber, { [field]: event.target.value })} />}
          </div>)}
        </div>
        {!!row.issues.length && <ul className="list-disc pl-5 text-xs">{row.issues.map(issue => <li key={issue}>{t(issueKeys[issue] ?? "pasteInvalidFields")}</li>)}</ul>}
        {row.existingMeaning != null && <p className="text-xs break-words">{t("pasteExistingMetadata")} {row.existingMeaning} {row.existingIpa}</p>}
        {!!row.cells.length && <details className="text-xs"><summary className="cursor-pointer">{t("pasteOriginalColumns")}</summary>
          <ol className="list-decimal pl-5">{row.cells.map((cell, index) => <li className="whitespace-pre-wrap break-words" key={index}>{cell || t("pasteEmptyCell")}</li>)}</ol></details>}
      </article>)}
      {rows.length > 25 && <div className="flex flex-wrap items-center gap-3">
        <Button variant="outline" disabled={busy || page === 0} onClick={() => setPage(page - 1)}>{t("folderPrevious")}</Button>
        <span className="text-sm">{page + 1} / {Math.ceil(rows.length / 25)}</span>
        <Button variant="outline" disabled={busy || (page + 1) * 25 >= rows.length} onClick={() => setPage(page + 1)}>{t("folderNext")}</Button>
      </div>}
      {warningRequired && <label className="flex items-start gap-2 text-sm"><input id="paste-warning-ack" type="checkbox" checked={warningsAccepted} disabled={busy} onChange={event => setWarningsAccepted(event.target.checked)} />{t("pasteAcknowledgeWarnings")}</label>}
      <label className="flex items-start gap-2 text-sm"><input id="paste-policy-ack" type="checkbox" checked={policyAccepted} disabled={busy} onChange={event => setPolicyAccepted(event.target.checked)} />{t("pastePolicy")}</label>
      <div className="flex flex-wrap gap-3">
        <Button variant="outline" disabled={busy || destinationBlocked || rows.length > PASTE_MAX_ROWS} onClick={() => run("validate")}>{t("pasteValidate")}</Button>
        <Button disabled={busy || destinationBlocked || !validated || invalid || !active.length || !policyAccepted || (warningRequired && !warningsAccepted)} onClick={() => run("confirm")}>{t("pasteConfirm")}</Button>
        <Button variant="ghost" disabled={busy} onClick={reset}>{t("learnCancel")}</Button>
      </div>
      <p className="text-xs text-muted-foreground">{t("pasteRetryNotice")}</p>
    </div>}
  </section>;
}
