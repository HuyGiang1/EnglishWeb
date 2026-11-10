"use client";

import { useLocale } from "@/lib/i18n/locale-provider";
import type { TranslationKey } from "@/lib/i18n/dictionaries";
import { learningErrorKey } from "@/lib/i18n/learning-errors";

import * as React from "react";
import { ImportWorkspace } from "@/components/vocabulary/import-workspace";
import Link from "next/link";
import {
  Upload,
  CheckCircle2,
  AlertCircle,
  AlertTriangle,
  Loader2,
  ChevronLeft,
  Download,
  FolderPlus,
  RefreshCw,
  BookOpen,
} from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { cn } from "cn";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { importApi, BulkImportPreviewResponse } from "@/lib/api/import";
import { ApiError } from "@/lib/api/client";

export default function BulkImportPage() {
  return <React.Suspense fallback={null}><ImportWorkspace renderFile={(id, blocked, onBusy) =>
    <LegacyFileImport selectedCollectionId={id} destinationBlocked={blocked} onBusyChange={onBusy} />} /></React.Suspense>;
}

function LegacyFileImport({ selectedCollectionId, destinationBlocked, onBusyChange }: {
  selectedCollectionId: string; destinationBlocked: boolean; onBusyChange: (busy: boolean) => void;
}) {
  const { t } = useLocale();
  // Step: "UPLOAD" | "PREVIEW" | "SUCCESS"
  const [step, setStep] = React.useState<"UPLOAD" | "PREVIEW" | "SUCCESS">("UPLOAD");

  // Collections for optional target selection

  const [duplicatePolicy, setDuplicatePolicy] = React.useState<"SKIP" | "REJECT">("SKIP");

  // Upload & File State
  const [selectedFile, setSelectedFile] = React.useState<File | null>(null);
  const [isDragOver, setIsDragOver] = React.useState(false);
  const [isPreviewLoading, setIsPreviewLoading] = React.useState(false);
  const [previewData, setPreviewData] = React.useState<BulkImportPreviewResponse | null>(null);

  // Table filter inside preview: "ALL" | "VALID" | "DUPLICATES" | "ERRORS"
  const [tableFilter, setTableFilter] = React.useState<"ALL" | "VALID" | "DUPLICATES" | "ERRORS">("ALL");

  // Confirm State
  const [isConfirming, setIsConfirming] = React.useState(false);
  const [confirmResult, setConfirmResult] = React.useState<{
    importedCount: number;
    skippedCount: number;
    rejectedCount: number;
  } | null>(null);

  // Global Error State
  const [errorMessage, setErrorMessage] = React.useState<TranslationKey | null>(null);

  const fileInputRef = React.useRef<HTMLInputElement>(null);

  React.useEffect(() => { onBusyChange(isPreviewLoading || isConfirming); }, [isPreviewLoading, isConfirming, onBusyChange]);

  // Handle file drop
  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      handleFileSelected(e.dataTransfer.files[0]);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      handleFileSelected(e.target.files[0]);
    }
  };

  const handleFileSelected = (file: File) => {
    setErrorMessage(null);
    const ext = file.name.split('.').pop()?.toLowerCase();
    if (ext !== 'csv' && ext !== 'tsv' && ext !== 'txt') {
      setErrorMessage("learnPleaseSelectACsvOrTsvFile");
      return;
    }

    if (file.size > 2 * 1024 * 1024) {
      setErrorMessage("learnFileExceedsMaximumAllowedSizeOf2MB");
      return;
    }

    setSelectedFile(file);
    triggerPreview(file);
  };

  const triggerPreview = async (file: File) => {
    setIsPreviewLoading(true);
    setErrorMessage(null);

    try {
      const preview = await importApi.preview(file);
      setPreviewData(preview);
      setStep("PREVIEW");
    } catch (err) {
      if (err instanceof ApiError) {
        setErrorMessage(learningErrorKey(err, "learnFailedToParseImportFile"));
      } else if (err instanceof Error) {
        setErrorMessage(learningErrorKey(err));
      } else {
        setErrorMessage("learnAnUnexpectedErrorOccurredWhileParsingTheFile");
      }
    } finally {
      setIsPreviewLoading(false);
    }
  };

  const handleConfirmImport = async () => {
    if (!previewData || isConfirming || destinationBlocked) return;

    // Filter out structurally invalid rows; valid and duplicate rows are sent to backend for duplicatePolicy enforcement
    const itemsToImport = previewData.previewRows
      .filter((r) => r.status !== "INVALID")
      .map((r) => ({
        term: r.term,
        partOfSpeech: r.partOfSpeech,
        vietnameseMeaning: r.vietnameseMeaning,
        phoneticIpa: r.phoneticIpa,
        englishDefinition: r.englishDefinition,
        exampleSentence: r.exampleSentence,
        exampleTranslation: r.exampleTranslation,
        notes: r.notes,
        source: r.source,
        difficultyLevel: r.difficultyLevel,
      }));

    if (itemsToImport.length === 0) {
      setErrorMessage("learnNoValidVocabularyEntriesToImport");
      return;
    }

    setIsConfirming(true);
    setErrorMessage(null);

    try {
      const result = await importApi.confirm({
        items: itemsToImport,
        collectionId: selectedCollectionId || undefined,
        duplicatePolicy,
      });

      setConfirmResult(result);
      setStep("SUCCESS");
    } catch (err) {
      if (err instanceof ApiError) {
        setErrorMessage(learningErrorKey(err, "learnImportConfirmationFailed"));
      } else if (err instanceof Error) {
        setErrorMessage(learningErrorKey(err));
      } else {
        setErrorMessage("learnAnUnexpectedErrorOccurredWhileSavingImportedVocabulary");
      }
    } finally {
      setIsConfirming(false);
    }
  };

  const handleDownloadTemplate = async (format: 'csv' | 'tsv') => {
    try {
      const blob = await importApi.downloadTemplate(format);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `englishflow_vocabulary_template.${format}`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    } catch {
      setErrorMessage("learnDownloadFailed");
    }
  };

  const handleReset = () => {
    setSelectedFile(null);
    setPreviewData(null);
    setConfirmResult(null);
    setErrorMessage(null);
    setTableFilter("ALL");
    setStep("UPLOAD");
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const filteredPreviewRows = React.useMemo(() => {
    if (!previewData) return [];
    if (tableFilter === "VALID") {
      return previewData.previewRows.filter((r) => r.status === "VALID");
    }
    if (tableFilter === "DUPLICATES") {
      return previewData.previewRows.filter((r) => r.status.startsWith("DUPLICATE_"));
    }
    if (tableFilter === "ERRORS") {
      return previewData.previewRows.filter((r) => r.status === "INVALID");
    }
    return previewData.previewRows;
  }, [previewData, tableFilter]);

  return (
    <div className="space-y-6 max-w-5xl mx-auto py-2">
      {/* Header with back navigation */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between border-b border-border pb-4">
        <div className="flex items-center gap-2">
          <Link
            href="/vocabulary"
            className={cn(buttonVariants({ variant: "ghost", size: "sm" }), "h-8 w-8 p-0")}
            aria-label={t("learnBackToVocabulary")}
          >
            <ChevronLeft className="h-4 w-4" />
          </Link>
          <div>
            <h1 className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">{t("learnImportVocabulary")}</h1>
            <p className="text-xs text-muted-foreground sm:text-sm">
              {selectedFile ? t("learnFileInfo", { name: selectedFile.name, size: (selectedFile.size / 1024).toFixed(1) }) : t("learnBatchIngestVocabularyFromCSVOrTSVFilesWithDuplicateValidationAndAutomaticSRSScheduling")}
            </p>
          </div>
        </div>

        {step === "UPLOAD" && (
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => handleDownloadTemplate('csv')}
              className="text-xs"
            >
              <Download className="mr-1.5 h-3.5 w-3.5" />{t("learnTemplateCSV")}</Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => handleDownloadTemplate('tsv')}
              className="text-xs"
            >
              <Download className="mr-1.5 h-3.5 w-3.5" />{t("learnTemplateTSV")}</Button>
          </div>
        )}
      </div>

      <ol aria-label={t("learnImportVocabulary")} className="flex flex-wrap gap-3 text-sm">
        <li aria-current={step === "UPLOAD" ? "step" : undefined}>{t("learnUploadFile")}</li>
        <li aria-current={step === "PREVIEW" ? "step" : undefined}>{t("learn2ReviewData")}</li>
        <li aria-current={step === "SUCCESS" ? "step" : undefined}>{t("learnConfirmSubmission")}</li>
      </ol>

      {/* Error Banner */}
      {errorMessage && (
        <div role="alert" className="rounded-lg border border-destructive/50 bg-destructive/10 p-3 text-destructive flex items-start gap-2.5 text-sm">
          <AlertCircle className="h-4 w-4 mt-0.5 shrink-0" />
          <div className="flex-1">
            <p className="font-medium">{t("learnImportAdvisory")}</p>
            <p className="text-xs opacity-90 mt-0.5">{t(errorMessage)}</p>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setErrorMessage(null)}
            className="h-6 px-2 text-xs"
          >{t("learnDismiss")}</Button>
        </div>
      )}

      {/* ========================================================================= */}
      {/* STEP 1: UPLOAD & CONFIGURATION                                            */}
      {/* ========================================================================= */}
      {step === "UPLOAD" && (
        <div className="grid gap-6 md:grid-cols-3">
          {/* Main Upload Dropzone (2 cols) */}
          <div className="md:col-span-2 space-y-4">
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-base">{t("learnUploadFile")}</CardTitle>
                <CardDescription className="text-xs">{t("learnUploadASpreadsheetExportContainingVocabularyItemsUTF8EncodedWithStandardCommaOrTabDelimi")}</CardDescription>
              </CardHeader>
              <CardContent>
                <div
                  onDragOver={(e) => {
                    e.preventDefault();
                    setIsDragOver(true);
                  }}
                  onDragLeave={() => setIsDragOver(false)}
                  onDrop={handleDrop}
                  onClick={() => fileInputRef.current?.click()}
                  className={cn(
                    "flex flex-col items-center justify-center rounded-lg border-2 border-dashed p-8 text-center cursor-pointer transition-colors",
                    isDragOver
                      ? "border-foreground bg-accent/50"
                      : "border-border hover:border-foreground/50 hover:bg-muted/30"
                  )}
                >
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept=".csv,.tsv,.txt"
                    onChange={handleFileChange}
                    className="hidden"
                    data-testid="file-upload-input"
                  />

                  {isPreviewLoading ? (
                    <div className="flex flex-col items-center gap-2 py-4">
                      <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                      <p className="text-sm font-medium">{t("learnParsingAndValidatingEntries")}</p>
                      <p className="text-xs text-muted-foreground">{t("learnCheckingDiacriticsAndLibraryDuplicates")}</p>
                    </div>
                  ) : (
                    <div className="flex flex-col items-center gap-2">
                      <div className="rounded-full bg-muted p-3">
                        <Upload className="h-6 w-6 text-foreground" />
                      </div>
                      <p className="text-sm font-medium">{t("learnClickToSelectOrDragAndDropAFile")}</p>
                      <p className="text-xs text-muted-foreground">{t("learnSupportsCSVTSVMax2MBUpTo1000Entries")}</p>
                    </div>
                  )}
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Import Settings & Target Deck (1 col) */}
          <div className="space-y-4">
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-base">{t("learnImportSettings")}</CardTitle>
                <CardDescription className="text-xs">{t("learnConfigureTargetDestinationAndConflictHandling")}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4 text-xs">
                {/* Duplicate Policy */}
                <div className="space-y-1.5 pt-2 border-t border-border">
                  <span className="font-medium text-foreground block">{t("learnDuplicateHandlingPolicy")}</span>
                  <div className="space-y-2 pt-1">
                    <label className="flex items-start gap-2 cursor-pointer">
                      <input
                        type="radio"
                        name="duplicatePolicy"
                        value="SKIP"
                        checked={duplicatePolicy === "SKIP"}
                        onChange={() => setDuplicatePolicy("SKIP")}
                        className="mt-0.5"
                      />
                      <div>
                        <span className="font-medium text-foreground">{t("learnSkipDuplicatesRecommended")}</span>
                        <p className="text-[11px] text-muted-foreground">{t("learnIgnoreExistingLibraryWordsAndImportNewWords")}</p>
                      </div>
                    </label>

                    <label className="flex items-start gap-2 cursor-pointer">
                      <input
                        type="radio"
                        name="duplicatePolicy"
                        value="REJECT"
                        checked={duplicatePolicy === "REJECT"}
                        onChange={() => setDuplicatePolicy("REJECT")}
                        className="mt-0.5"
                      />
                      <div>
                        <span className="font-medium text-foreground">{t("learnRejectEntireBatch")}</span>
                        <p className="text-[11px] text-muted-foreground">{t("learnCancelImportIfAnyDuplicateTermExists")}</p>
                      </div>
                    </label>
                  </div>
                </div>

                {/* Rules Disclosure */}
                <div className="rounded border border-border bg-muted/30 p-2.5 text-[11px] text-muted-foreground space-y-1">
                  <p className="font-medium text-foreground">{t("learnGuaranteesConstraints")}</p>
                  <ul className="list-disc pl-3.5 space-y-0.5">
                    <li>{t("learnCreatesExactly1SRSFlashcardPerImportedWord")}</li>
                    <li>{t("learnAwards0StudyPointsDoesNotAdvanceDailyGoals")}</li>
                    <li>{t("learnRequiredColumns")}{" "}<code className="font-mono">term</code>, <code className="font-mono">vietnameseMeaning</code>.</li>
                  </ul>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* STEP 2: PREVIEW & VERIFICATION                                            */}
      {/* ========================================================================= */}
      {step === "PREVIEW" && previewData && (
        <div className="space-y-5">
          {/* File summary and KPI bar */}
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Card className="p-3">
              <span className="text-[11px] font-medium text-muted-foreground">{t("learnTotalRows")}</span>
              <p className="text-xl font-bold text-foreground mt-0.5">{previewData.totalRows}</p>
            </Card>
            <Card className="p-3 border-border bg-muted">
              <span className="text-[11px] font-medium text-foreground dark:text-foreground">{t("learnReadyToImport")}</span>
              <p className="text-xl font-bold text-foreground mt-0.5">{previewData.validRows}</p>
            </Card>
            <Card className="p-3 border-border bg-muted">
              <span className="text-[11px] font-medium text-foreground dark:text-foreground">{t("learnDuplicatesDetected")}</span>
              <p className="text-xl font-bold text-foreground mt-0.5">{previewData.duplicateRows}</p>
            </Card>
            <Card className="p-3 border-destructive/30 bg-destructive/5">
              <span className="text-[11px] font-medium text-destructive">{t("learnInvalidErrors")}</span>
              <p className="text-xl font-bold text-foreground mt-0.5">{previewData.errorRows}</p>
            </Card>
          </div>

          {/* Table Header Filter Tabs & Actions */}
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 bg-muted/40 p-2.5 rounded-lg border border-border">
            <div className="flex items-center gap-1.5 text-xs">
              <button
                type="button"
                onClick={() => setTableFilter("ALL")}
                className={cn(
                  "px-2.5 py-1 rounded font-medium transition-colors",
                  tableFilter === "ALL" ? "bg-background shadow-xs text-foreground" : "text-muted-foreground hover:text-foreground"
                )}
              >{t("learnAll")}{previewData.totalRows})
              </button>
              <button
                type="button"
                onClick={() => setTableFilter("VALID")}
                className={cn(
                  "px-2.5 py-1 rounded font-medium transition-colors",
                  tableFilter === "VALID" ? "bg-background shadow-xs text-foreground dark:text-foreground" : "text-muted-foreground hover:text-foreground"
                )}
              >{t("learnValid")}{previewData.validRows})
              </button>
              {previewData.duplicateRows > 0 && (
                <button
                  type="button"
                  onClick={() => setTableFilter("DUPLICATES")}
                  className={cn(
                    "px-2.5 py-1 rounded font-medium transition-colors",
                    tableFilter === "DUPLICATES" ? "bg-background shadow-xs text-foreground dark:text-foreground" : "text-muted-foreground hover:text-foreground"
                  )}
                >{t("learnDuplicates")}{previewData.duplicateRows})
                </button>
              )}
              {previewData.errorRows > 0 && (
                <button
                  type="button"
                  onClick={() => setTableFilter("ERRORS")}
                  className={cn(
                    "px-2.5 py-1 rounded font-medium transition-colors",
                    tableFilter === "ERRORS" ? "bg-background shadow-xs text-destructive" : "text-muted-foreground hover:text-foreground"
                  )}
                >{t("learnErrors")}{previewData.errorRows})
                </button>
              )}
            </div>

            <div className="flex items-center gap-2 w-full sm:w-auto justify-end">
              <Button
                variant="outline"
                size="sm"
                onClick={handleReset}
                disabled={isConfirming}
                className="text-xs"
              >{t("learnChooseAnotherFile")}</Button>
              <Button
                size="sm"
                onClick={handleConfirmImport}
                disabled={destinationBlocked || isConfirming || previewData.validRows === 0}
                className="text-xs"
                data-testid="confirm-import-button"
              >
                {isConfirming ? (
                  <>
                    <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />{t("learnImporting")}</>
                ) : (
                  <>{t("learnConfirmImport")}{previewData.validRows}{" "}{t("learnWords2")}</>
                )}
              </Button>
            </div>
          </div>

          {/* Preview Table */}
          <div className="rounded-lg border border-border overflow-hidden bg-card">
            <div className="overflow-x-auto max-h-[460px]">
              <table className="w-full text-xs text-left" data-testid="preview-table">
                <thead className="bg-muted text-muted-foreground uppercase text-[10px] tracking-wider sticky top-0 z-10 border-b border-border">
                  <tr>
                    <th className="py-2 px-3 w-12 text-center">{t("learnRow")}</th>
                    <th className="py-2 px-3">{t("learnTerm")}</th>
                    <th className="py-2 px-3 w-20">{t("learnPOS")}</th>
                    <th className="py-2 px-3">{t("learnVietnameseMeaning")}</th>
                    <th className="py-2 px-3 w-16 text-center">{t("learnLevel")}</th>
                    <th className="py-2 px-3 w-28 text-center">{t("learnStatus")}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {filteredPreviewRows.length === 0 ? (
                    <tr>
                      <td colSpan={6} className="text-center py-8 text-muted-foreground">{t("learnNoRowsFoundMatchingCurrentFilter")}</td>
                    </tr>
                  ) : (
                    filteredPreviewRows.map((row) => (
                      <tr
                        key={row.rowNumber}
                        className={cn(
                          "hover:bg-muted/30 transition-colors",
                          row.status === "INVALID" && "bg-destructive/5",
                          row.status.startsWith("DUPLICATE_") && "bg-muted"
                        )}
                      >
                        <td className="py-2 px-3 text-center text-muted-foreground font-mono">
                          {row.rowNumber}
                        </td>
                        <td className="py-2 px-3 font-medium text-foreground">
                          {row.term}
                          {row.phoneticIpa && (
                            <span className="ml-1.5 font-mono text-[11px] text-muted-foreground">
                              {row.phoneticIpa}
                            </span>
                          )}
                        </td>
                        <td className="py-2 px-3 text-muted-foreground font-mono text-[11px]">
                          {row.partOfSpeech || "other"}
                        </td>
                        <td className="py-2 px-3 text-foreground">
                          {row.vietnameseMeaning}
                          {row.validationMessage && (
                            <p className="text-[11px] text-destructive mt-0.5">
                              {row.status === "INVALID" ? t("learnThisRowNeedsCorrectionCheckTheRequiredColumnsValuesAndLengthLimits") : t("learnThisWordAlreadyExistsInYourLibraryOrAppearsEarlierInTheFile")}
                            </p>
                          )}
                        </td>
                        <td className="py-2 px-3 text-center">
                          {row.difficultyLevel ? (
                            <span className="rounded border border-border px-1 py-0.2 text-[10px] font-mono">
                              {row.difficultyLevel}
                            </span>
                          ) : (
                            <span className="text-muted-foreground">—</span>
                          )}
                        </td>
                        <td className="py-2 px-3 text-center">
                          {row.status === "VALID" && (
                            <span className="inline-flex items-center gap-1 rounded-full bg-muted px-2 py-0.5 text-[10px] font-medium text-foreground dark:text-foreground">
                              <CheckCircle2 className="h-3 w-3" />{" "}{t("learnValid2")}</span>
                          )}
                          {row.status.startsWith("DUPLICATE_") && (
                            <span className="inline-flex items-center gap-1 rounded-full bg-muted px-2 py-0.5 text-[10px] font-medium text-foreground dark:text-foreground">
                              <AlertTriangle className="h-3 w-3" />{" "}{t("learnDuplicate")}</span>
                          )}
                          {row.status === "INVALID" && (
                            <span className="inline-flex items-center gap-1 rounded-full bg-destructive/10 px-2 py-0.5 text-[10px] font-medium text-destructive">
                              <AlertCircle className="h-3 w-3" />{" "}{t("learnError")}</span>
                          )}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* STEP 3: SUCCESS & CONFIRMATION RESULT                                     */}
      {/* ========================================================================= */}
      {step === "SUCCESS" && confirmResult && (
        <Card className="border-border">
          <CardContent className="pt-8 pb-8 text-center space-y-6">
            <div className="mx-auto rounded-full bg-muted p-4 w-16 h-16 flex items-center justify-center">
              <CheckCircle2 className="h-8 w-8 text-foreground dark:text-foreground" />
            </div>

            <div className="space-y-1">
              <h2 className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">{t("learnVocabularyImportComplete")}</h2>
              <p className="text-sm text-muted-foreground max-w-md mx-auto">{t("learnSuccessfullyProcessedEntriesAllValidWordsHaveBeenPersistedToYourLibraryAndProvisionedInt")}</p>
            </div>

            {/* Metrics */}
            <div className="flex items-center justify-center gap-4 py-2">
              <div className="rounded-lg border border-border p-3 min-w-[120px]">
                <span className="text-xs text-muted-foreground">{t("learnImported")}</span>
                <p className="text-2xl font-bold text-foreground mt-0.5">
                  {confirmResult.importedCount}
                </p>
              </div>
              <div className="rounded-lg border border-border p-3 min-w-[120px]">
                <span className="text-xs text-muted-foreground">{t("learnSkipped")}</span>
                <p className="text-2xl font-bold text-muted-foreground mt-0.5">
                  {confirmResult.skippedCount}
                </p>
              </div>
            </div>

            {/* Quick Action Navigation */}
            <div className="flex flex-wrap items-center justify-center gap-2.5 pt-2">
              <Link
                href="/vocabulary"
                className={cn(buttonVariants({ variant: "default" }), "text-xs")}
              >
                <BookOpen className="mr-1.5 h-3.5 w-3.5" />{t("learnViewInLibrary")}</Link>
              {selectedCollectionId && (
                <Link
                  href={`/collections/${selectedCollectionId}`}
                  className={cn(buttonVariants({ variant: "outline" }), "text-xs")}
                >
                  <FolderPlus className="mr-1.5 h-3.5 w-3.5" />{t("learnViewInCollection")}</Link>
              )}
              <Link
                href="/review"
                className={cn(buttonVariants({ variant: "outline" }), "text-xs")}
              >{t("learnReviewCardsSRS")}</Link>
              <Button
                variant="ghost"
                size="sm"
                onClick={handleReset}
                className="text-xs"
              >
                <RefreshCw className="mr-1.5 h-3.5 w-3.5" />{t("learnImportAnotherFile")}</Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
