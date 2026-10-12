"use client";

import * as React from "react";
import Link from "next/link";
import {
  Plus,
  Search,
  AlertCircle,
  Loader2,
  Trash2,
  ExternalLink,
  ChevronLeft,
  ChevronRight,
  BookOpen,
} from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { cn } from "cn";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { QuickAddDialog } from "@/components/vocabulary/quick-add-dialog";
import {
  vocabularyApi,
  VocabularyItem,
  VocabularyPageResponse,
} from "@/lib/api/vocabulary";

export default function VocabularyPage() {
  const [data, setData] = React.useState<VocabularyPageResponse | null>(null);
  const [isLoading, setIsLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  // Filters & Pagination State
  const [search, setSearch] = React.useState("");
  const [debouncedSearch, setDebouncedSearch] = React.useState("");
  const [partOfSpeech, setPartOfSpeech] = React.useState("");
  const [difficultyLevel, setDifficultyLevel] = React.useState("");
  const [page, setPage] = React.useState(0);
  const [sort, setSort] = React.useState("createdAt,desc");

  // Dialog states
  const [isQuickAddOpen, setIsQuickAddOpen] = React.useState(false);
  const [itemToDelete, setItemToDelete] = React.useState<VocabularyItem | null>(null);
  const [isDeleting, setIsDeleting] = React.useState(false);

  const searchInputRef = React.useRef<HTMLInputElement>(null);

  // Debounce search input by 300ms
  React.useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search);
      setPage(0); // Reset to first page on search change
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  // Fetch vocabulary data
  const fetchData = React.useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await vocabularyApi.list({
        search: debouncedSearch || undefined,
        partOfSpeech: partOfSpeech || undefined,
        difficultyLevel: difficultyLevel || undefined,
        page,
        size: 20,
        sort,
      });
      setData(response);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to load vocabulary list";
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  }, [debouncedSearch, partOfSpeech, difficultyLevel, page, sort]);

  React.useEffect(() => {
    fetchData();
  }, [fetchData]);

  // Global Keyboard Shortcuts (Guarded)
  React.useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Ignore when typing inside input, textarea, or contentEditable
      const target = e.target as HTMLElement | null;
      const isInput =
        target &&
        (target.tagName === "INPUT" ||
          target.tagName === "TEXTAREA" ||
          target.tagName === "SELECT" ||
          target.isContentEditable);

      if (isInput) return;

      // 'C' shortcut to open Quick Add modal
      if ((e.key === "c" || e.key === "C") && !e.metaKey && !e.ctrlKey) {
        e.preventDefault();
        setIsQuickAddOpen(true);
      }

      // '/' shortcut to focus search
      if (e.key === "/" && !e.metaKey && !e.ctrlKey) {
        e.preventDefault();
        searchInputRef.current?.focus();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  const handleDeleteConfirm = async () => {
    if (!itemToDelete) return;
    setIsDeleting(true);
    try {
      await vocabularyApi.delete(itemToDelete.id);
      setItemToDelete(null);
      await fetchData();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to delete item";
      setError(msg);
    } finally {
      setIsDeleting(false);
    }
  };

  const handleWordCreated = () => {
    fetchData();
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-foreground">
              Vocabulary Library
            </h1>
            {data && (
              <span className="rounded-full border border-border bg-muted px-2.5 py-0.5 text-xs font-mono font-medium text-muted-foreground">
                {data.totalElements} words
              </span>
            )}
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Personal lexical inventory with definitions, phonetics, and contextual examples.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button onClick={() => setIsQuickAddOpen(true)}>
            <Plus className="mr-1.5 h-4 w-4" />
            Add Word <kbd className="ml-1 text-[10px] font-mono opacity-60">C</kbd>
          </Button>
        </div>
      </div>

      {/* Filter & Search Toolbar */}
      <div className="flex flex-col gap-3 rounded-lg border border-border bg-card p-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-1 flex-wrap items-center gap-2.5">
          {/* Search Input with / shortcut hint */}
          <div className="relative min-w-[200px] flex-1 sm:max-w-xs">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              ref={searchInputRef}
              type="search"
              placeholder="Search word, meaning..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-8 pr-8"
            />
            <kbd className="pointer-events-none absolute right-2.5 top-2.5 hidden sm:inline-block rounded border border-border bg-muted px-1.5 py-0.2 text-[10px] font-mono text-muted-foreground">
              /
            </kbd>
          </div>

          {/* Part of Speech Filter */}
          <select
            value={partOfSpeech}
            onChange={(e) => {
              setPartOfSpeech(e.target.value);
              setPage(0);
            }}
            className="h-9 rounded-md border border-input bg-background px-3 py-1 text-sm shadow-xs focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            aria-label="Filter by part of speech"
          >
            <option value="">All Parts of Speech</option>
            <option value="noun">Noun</option>
            <option value="verb">Verb</option>
            <option value="adjective">Adjective</option>
            <option value="adverb">Adverb</option>
            <option value="idiom">Idiom</option>
            <option value="phrase">Phrase</option>
            <option value="preposition">Preposition</option>
            <option value="conjunction">Conjunction</option>
            <option value="other">Other</option>
          </select>

          {/* CEFR Level Filter */}
          <select
            value={difficultyLevel}
            onChange={(e) => {
              setDifficultyLevel(e.target.value);
              setPage(0);
            }}
            className="h-9 rounded-md border border-input bg-background px-3 py-1 text-sm shadow-xs focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            aria-label="Filter by CEFR level"
          >
            <option value="">All CEFR Levels</option>
            <option value="A1">A1</option>
            <option value="A2">A2</option>
            <option value="B1">B1</option>
            <option value="B2">B2</option>
            <option value="C1">C1</option>
            <option value="C2">C2</option>
          </select>
        </div>

        {/* Sort Selector */}
        <div className="flex items-center gap-2">
          <span className="text-xs text-muted-foreground">Sort:</span>
          <select
            value={sort}
            onChange={(e) => {
              setSort(e.target.value);
              setPage(0);
            }}
            className="h-9 rounded-md border border-input bg-background px-3 py-1 text-sm shadow-xs focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            aria-label="Sort vocabulary items"
          >
            <option value="createdAt,desc">Recently Added</option>
            <option value="term,asc">Alphabetical (A–Z)</option>
            <option value="updatedAt,desc">Last Updated</option>
          </select>
        </div>
      </div>

      {/* Main Content Area */}
      {error && (
        <div
          role="alert"
          className="flex items-center justify-between rounded-md border border-neutral-300 bg-neutral-100 p-4 text-sm text-neutral-900"
        >
          <div className="flex items-center gap-2">
            <AlertCircle className="h-4 w-4 shrink-0 text-neutral-700" />
            <span>{error}</span>
          </div>
          <Button variant="outline" size="sm" onClick={fetchData}>
            Retry
          </Button>
        </div>
      )}

      {/* Loading Skeleton */}
      {isLoading && !data && (
        <div className="rounded-lg border border-border bg-card">
          <div className="p-4 space-y-3">
            {[...Array(8)].map((_, i) => (
              <div
                key={i}
                className="h-12 w-full animate-pulse rounded bg-muted/50"
              />
            ))}
          </div>
        </div>
      )}

      {/* Empty State */}
      {!isLoading && data && data.content.length === 0 && (
        <div className="rounded-lg border border-dashed border-border bg-card p-12 text-center">
          <BookOpen className="mx-auto h-10 w-10 text-muted-foreground mb-3 opacity-60" />
          <h2 className="text-base font-semibold text-foreground mb-1">
            {search || partOfSpeech || difficultyLevel
              ? "No matching vocabulary words"
              : "Your vocabulary library is empty"}
          </h2>
          <p className="text-sm text-muted-foreground mb-6 max-w-sm mx-auto">
            {search || partOfSpeech || difficultyLevel
              ? "Try adjusting your search query or filters to find what you are looking for."
              : "Capture new words with phonetics, definitions, and examples as you study."}
          </p>
          <Button onClick={() => setIsQuickAddOpen(true)}>
            <Plus className="mr-1.5 h-4 w-4" />
            Add Your First Word
          </Button>
        </div>
      )}

      {/* Data Table (Desktop) / Cards (Mobile) */}
      {!isLoading && data && data.content.length > 0 && (
        <div className="space-y-4">
          {/* Desktop Table */}
          <div className="hidden md:block overflow-hidden rounded-lg border border-border bg-card">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-border bg-muted/40 font-medium text-muted-foreground">
                <tr>
                  <th scope="col" className="px-4 py-3 font-semibold">Term</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Phonetic</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Part of Speech</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Vietnamese Meaning</th>
                  <th scope="col" className="px-4 py-3 font-semibold">CEFR</th>
                  <th scope="col" className="px-4 py-3 font-semibold text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.content.map((item) => (
                  <tr
                    key={item.id}
                    className="group transition-colors hover:bg-muted/30"
                  >
                    <td className="px-4 py-3 font-semibold text-foreground">
                      <Link
                        href={`/vocabulary/${item.id}`}
                        className="hover:underline flex items-center gap-1.5"
                      >
                        {item.term}
                      </Link>
                    </td>
                    <td className="px-4 py-3 font-mono text-xs text-muted-foreground">
                      {item.phoneticIpa || "—"}
                    </td>
                    <td className="px-4 py-3">
                      <span className="inline-flex items-center rounded-md border border-border px-2 py-0.5 text-xs font-mono font-medium text-foreground bg-muted">
                        {item.partOfSpeech}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-foreground truncate max-w-xs">
                      {item.vietnameseMeaning}
                    </td>
                    <td className="px-4 py-3">
                      {item.difficultyLevel ? (
                        <span className="inline-flex items-center rounded border border-foreground/30 px-1.5 py-0.2 text-[11px] font-semibold text-foreground">
                          {item.difficultyLevel}
                        </span>
                      ) : (
                        <span className="text-muted-foreground text-xs">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <Link
                          href={`/vocabulary/${item.id}`}
                          className={cn(buttonVariants({ variant: "ghost", size: "sm" }))}
                          aria-label={`View ${item.term}`}
                        >
                          <ExternalLink className="h-3.5 w-3.5" />
                        </Link>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-neutral-500 hover:text-black hover:bg-neutral-100"
                          onClick={() => setItemToDelete(item)}
                          aria-label={`Delete ${item.term}`}
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile Card List */}
          <div className="grid grid-cols-1 gap-3 md:hidden">
            {data.content.map((item) => (
              <div
                key={item.id}
                className="rounded-lg border border-border bg-card p-4 space-y-2.5"
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <Link
                      href={`/vocabulary/${item.id}`}
                      className="font-semibold text-base text-foreground hover:underline"
                    >
                      {item.term}
                    </Link>
                    {item.phoneticIpa && (
                      <p className="font-mono text-xs text-muted-foreground mt-0.5">
                        {item.phoneticIpa}
                      </p>
                    )}
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className="rounded-md border border-border bg-muted px-2 py-0.5 text-xs font-mono">
                      {item.partOfSpeech}
                    </span>
                    {item.difficultyLevel && (
                      <span className="rounded border border-foreground/30 px-1.5 py-0.5 text-xs font-semibold">
                        {item.difficultyLevel}
                      </span>
                    )}
                  </div>
                </div>

                <p className="text-sm text-foreground line-clamp-2">
                  {item.vietnameseMeaning}
                </p>

                <div className="flex items-center justify-end gap-2 pt-2 border-t border-border">
                  <Link
                    href={`/vocabulary/${item.id}`}
                    className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
                  >
                    View & Edit
                  </Link>
                  <Button
                    variant="outline"
                    size="sm"
                    className="text-neutral-600 hover:text-black"
                    onClick={() => setItemToDelete(item)}
                  >
                    Delete
                  </Button>
                </div>
              </div>
            ))}
          </div>

          {/* Pagination Controls */}
          {data.totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-border pt-4 text-sm">
              <span className="text-xs text-muted-foreground">
                Showing {page * 20 + 1}–{Math.min((page + 1) * 20, data.totalElements)} of{" "}
                {data.totalElements} items
              </span>

              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                >
                  <ChevronLeft className="mr-1 h-3.5 w-3.5" />
                  Previous
                </Button>
                <span className="font-mono text-xs text-foreground px-2">
                  Page {page + 1} of {data.totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
                  disabled={page >= data.totalPages - 1}
                >
                  Next
                  <ChevronRight className="ml-1 h-3.5 w-3.5" />
                </Button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Quick Add Modal */}
      <QuickAddDialog
        open={isQuickAddOpen}
        onOpenChange={setIsQuickAddOpen}
        onSuccess={handleWordCreated}
      />

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={itemToDelete !== null}
        onOpenChange={(open) => {
          if (!open) setItemToDelete(null);
        }}
      >
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Delete Vocabulary Word?</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete <strong>&quot;{itemToDelete?.term}&quot;</strong>? This
              word will be soft-deleted and removed from your library.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="gap-2 sm:gap-0 pt-4">
            <Button
              variant="outline"
              onClick={() => setItemToDelete(null)}
              disabled={isDeleting}
            >
              Cancel
            </Button>
            <Button
              variant="outline"
              className="border-neutral-300 text-neutral-900 hover:bg-neutral-100"
              onClick={handleDeleteConfirm}
              disabled={isDeleting}
            >
              {isDeleting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Deleting...
                </>
              ) : (
                "Confirm Delete"
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
