"use client";

import * as React from "react";
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
import { collectionsApi, CollectionItem } from "@/lib/api/collections";
import { ApiError } from "@/lib/api/client";

export default function BulkImportPage() {
  // Step: "UPLOAD" | "PREVIEW" | "SUCCESS"
  const [step, setStep] = React.useState<"UPLOAD" | "PREVIEW" | "SUCCESS">("UPLOAD");

  // Collections for optional target selection
  const [collections, setCollections] = React.useState<CollectionItem[]>([]);
  const [selectedCollectionId, setSelectedCollectionId] = React.useState<string>("");
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
  const [errorMessage, setErrorMessage] = React.useState<string | null>(null);

  const fileInputRef = React.useRef<HTMLInputElement>(null);

  // Load collections on mount
  React.useEffect(() => {
    collectionsApi.list({ size: 100 })
      .then((res) => setCollections(res.content))
      .catch(() => {
        // Non-blocking: collection selector is optional
      });
  }, []);

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
      setErrorMessage("Please select a .csv or .tsv file.");
      return;
    }

    if (file.size > 2 * 1024 * 1024) {
      setErrorMessage("File exceeds maximum allowed size of 2 MB.");
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
        setErrorMessage(err.message || "Failed to parse import file.");
      } else if (err instanceof Error) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage("An unexpected error occurred while parsing the file.");
      }
    } finally {
      setIsPreviewLoading(false);
    }
  };

  const handleConfirmImport = async () => {
    if (!previewData || isConfirming) return;

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
      setErrorMessage("No valid vocabulary entries to import.");
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
        setErrorMessage(err.message || "Import confirmation failed.");
      } else if (err instanceof Error) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage("An unexpected error occurred while saving imported vocabulary.");
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
      setErrorMessage(`Failed to download ${format.toUpperCase()} template.`);
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
            aria-label="Back to Vocabulary"
          >
            <ChevronLeft className="h-4 w-4" />
          </Link>
          <div>
            <h1 className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
              Import Vocabulary
            </h1>
            <p className="text-xs text-muted-foreground sm:text-sm">
              {selectedFile ? `File: ${selectedFile.name} (${(selectedFile.size / 1024).toFixed(1)} KB)` : "Batch ingest vocabulary from CSV or TSV files with duplicate validation and automatic SRS scheduling."}
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
              <Download className="mr-1.5 h-3.5 w-3.5" />
              Template (CSV)
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => handleDownloadTemplate('tsv')}
              className="text-xs"
            >
              <Download className="mr-1.5 h-3.5 w-3.5" />
              Template (TSV)
            </Button>
          </div>
        )}
      </div>

      {/* Error Banner */}
      {errorMessage && (
        <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-3 text-destructive flex items-start gap-2.5 text-sm">
          <AlertCircle className="h-4 w-4 mt-0.5 shrink-0" />
          <div className="flex-1">
            <p className="font-medium">Import Advisory</p>
            <p className="text-xs opacity-90 mt-0.5">{errorMessage}</p>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setErrorMessage(null)}
            className="h-6 px-2 text-xs"
          >
            Dismiss
          </Button>
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
                <CardTitle className="text-base">Upload File</CardTitle>
                <CardDescription className="text-xs">
                  Upload a spreadsheet export containing vocabulary items. UTF-8 encoded with standard comma or tab delimiters.
                </CardDescription>
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
                      <p className="text-sm font-medium">Parsing and validating entries...</p>
                      <p className="text-xs text-muted-foreground">Checking diacritics and library duplicates</p>
                    </div>
                  ) : (
                    <div className="flex flex-col items-center gap-2">
                      <div className="rounded-full bg-muted p-3">
                        <Upload className="h-6 w-6 text-foreground" />
                      </div>
                      <p className="text-sm font-medium">
                        Click to select or drag and drop a file
                      </p>
                      <p className="text-xs text-muted-foreground">
                        Supports CSV, TSV (Max 2 MB, up to 1,000 entries)
                      </p>
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
                <CardTitle className="text-base">Import Settings</CardTitle>
                <CardDescription className="text-xs">
                  Configure target destination and conflict handling.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4 text-xs">
                {/* Collection selector */}
                <div className="space-y-1.5">
                  <label htmlFor="target-collection" className="font-medium text-foreground block">
                    Assign to Collection (Optional)
                  </label>
                  <select
                    id="target-collection"
                    value={selectedCollectionId}
                    onChange={(e) => setSelectedCollectionId(e.target.value)}
                    className="w-full rounded-md border border-input bg-background px-2.5 py-1.5 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
                    data-testid="collection-select"
                  >
                    <option value="">No Collection (Library Only)</option>
                    {collections.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.title} ({c.wordCount || 0} words)
                      </option>
                    ))}
                  </select>
                  <p className="text-[11px] text-muted-foreground">
                    Imported words will be automatically added to this deck.
                  </p>
                </div>

                {/* Duplicate Policy */}
                <div className="space-y-1.5 pt-2 border-t border-border">
                  <span className="font-medium text-foreground block">
                    Duplicate Handling Policy
                  </span>
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
                        <span className="font-medium text-foreground">Skip Duplicates (Recommended)</span>
                        <p className="text-[11px] text-muted-foreground">
                          Ignore existing library words and import new words.
                        </p>
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
                        <span className="font-medium text-foreground">Reject Entire Batch</span>
                        <p className="text-[11px] text-muted-foreground">
                          Cancel import if any duplicate term exists.
                        </p>
                      </div>
                    </label>
                  </div>
                </div>

                {/* Rules Disclosure */}
                <div className="rounded border border-border bg-muted/30 p-2.5 text-[11px] text-muted-foreground space-y-1">
                  <p className="font-medium text-foreground">Guarantees & Constraints:</p>
                  <ul className="list-disc pl-3.5 space-y-0.5">
                    <li>Creates exactly 1 SRS flashcard per imported word.</li>
                    <li>Awards 0 study points (does not advance daily goals).</li>
                    <li>Required columns: <code className="font-mono">term</code>, <code className="font-mono">vietnameseMeaning</code>.</li>
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
              <span className="text-[11px] font-medium text-muted-foreground">Total Rows</span>
              <p className="text-xl font-bold text-foreground mt-0.5">{previewData.totalRows}</p>
            </Card>
            <Card className="p-3 border-emerald-500/30 bg-emerald-500/5">
              <span className="text-[11px] font-medium text-emerald-600 dark:text-emerald-400">Ready to Import</span>
              <p className="text-xl font-bold text-foreground mt-0.5">{previewData.validRows}</p>
            </Card>
            <Card className="p-3 border-amber-500/30 bg-amber-500/5">
              <span className="text-[11px] font-medium text-amber-600 dark:text-amber-400">Duplicates Detected</span>
              <p className="text-xl font-bold text-foreground mt-0.5">{previewData.duplicateRows}</p>
            </Card>
            <Card className="p-3 border-destructive/30 bg-destructive/5">
              <span className="text-[11px] font-medium text-destructive">Invalid / Errors</span>
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
              >
                All ({previewData.totalRows})
              </button>
              <button
                type="button"
                onClick={() => setTableFilter("VALID")}
                className={cn(
                  "px-2.5 py-1 rounded font-medium transition-colors",
                  tableFilter === "VALID" ? "bg-background shadow-xs text-emerald-600 dark:text-emerald-400" : "text-muted-foreground hover:text-foreground"
                )}
              >
                Valid ({previewData.validRows})
              </button>
              {previewData.duplicateRows > 0 && (
                <button
                  type="button"
                  onClick={() => setTableFilter("DUPLICATES")}
                  className={cn(
                    "px-2.5 py-1 rounded font-medium transition-colors",
                    tableFilter === "DUPLICATES" ? "bg-background shadow-xs text-amber-600 dark:text-amber-400" : "text-muted-foreground hover:text-foreground"
                  )}
                >
                  Duplicates ({previewData.duplicateRows})
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
                >
                  Errors ({previewData.errorRows})
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
              >
                Choose Another File
              </Button>
              <Button
                size="sm"
                onClick={handleConfirmImport}
                disabled={isConfirming || previewData.validRows === 0}
                className="text-xs"
                data-testid="confirm-import-button"
              >
                {isConfirming ? (
                  <>
                    <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                    Importing...
                  </>
                ) : (
                  <>
                    Confirm Import ({previewData.validRows} words)
                  </>
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
                    <th className="py-2 px-3 w-12 text-center">Row</th>
                    <th className="py-2 px-3">Term</th>
                    <th className="py-2 px-3 w-20">POS</th>
                    <th className="py-2 px-3">Vietnamese Meaning</th>
                    <th className="py-2 px-3 w-16 text-center">Level</th>
                    <th className="py-2 px-3 w-28 text-center">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {filteredPreviewRows.length === 0 ? (
                    <tr>
                      <td colSpan={6} className="text-center py-8 text-muted-foreground">
                        No rows found matching current filter.
                      </td>
                    </tr>
                  ) : (
                    filteredPreviewRows.map((row) => (
                      <tr
                        key={row.rowNumber}
                        className={cn(
                          "hover:bg-muted/30 transition-colors",
                          row.status === "INVALID" && "bg-destructive/5",
                          row.status.startsWith("DUPLICATE_") && "bg-amber-500/5"
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
                              {row.validationMessage}
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
                            <span className="inline-flex items-center gap-1 rounded-full bg-emerald-500/10 px-2 py-0.5 text-[10px] font-medium text-emerald-600 dark:text-emerald-400">
                              <CheckCircle2 className="h-3 w-3" /> Valid
                            </span>
                          )}
                          {row.status.startsWith("DUPLICATE_") && (
                            <span className="inline-flex items-center gap-1 rounded-full bg-amber-500/10 px-2 py-0.5 text-[10px] font-medium text-amber-600 dark:text-amber-400">
                              <AlertTriangle className="h-3 w-3" /> Duplicate
                            </span>
                          )}
                          {row.status === "INVALID" && (
                            <span className="inline-flex items-center gap-1 rounded-full bg-destructive/10 px-2 py-0.5 text-[10px] font-medium text-destructive">
                              <AlertCircle className="h-3 w-3" /> Error
                            </span>
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
            <div className="mx-auto rounded-full bg-emerald-500/10 p-4 w-16 h-16 flex items-center justify-center">
              <CheckCircle2 className="h-8 w-8 text-emerald-600 dark:text-emerald-400" />
            </div>

            <div className="space-y-1">
              <h2 className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
                Vocabulary Import Complete
              </h2>
              <p className="text-sm text-muted-foreground max-w-md mx-auto">
                Successfully processed entries. All valid words have been persisted to your library and provisioned into your flashcard queue.
              </p>
            </div>

            {/* Metrics */}
            <div className="flex items-center justify-center gap-4 py-2">
              <div className="rounded-lg border border-border p-3 min-w-[120px]">
                <span className="text-xs text-muted-foreground">Imported</span>
                <p className="text-2xl font-bold text-foreground mt-0.5">
                  {confirmResult.importedCount}
                </p>
              </div>
              <div className="rounded-lg border border-border p-3 min-w-[120px]">
                <span className="text-xs text-muted-foreground">Skipped</span>
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
                <BookOpen className="mr-1.5 h-3.5 w-3.5" />
                View in Library
              </Link>
              {selectedCollectionId && (
                <Link
                  href={`/collections/${selectedCollectionId}`}
                  className={cn(buttonVariants({ variant: "outline" }), "text-xs")}
                >
                  <FolderPlus className="mr-1.5 h-3.5 w-3.5" />
                  View in Collection
                </Link>
              )}
              <Link
                href="/review"
                className={cn(buttonVariants({ variant: "outline" }), "text-xs")}
              >
                Review Cards (SRS)
              </Link>
              <Button
                variant="ghost"
                size="sm"
                onClick={handleReset}
                className="text-xs"
              >
                <RefreshCw className="mr-1.5 h-3.5 w-3.5" />
                Import Another File
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
