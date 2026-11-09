"use client";

import { LanguageSwitcher } from "@/components/auth/language-switcher";
import { useLocale } from "@/lib/i18n/locale-provider";
import type { TranslationKey } from "@/lib/i18n/dictionaries";
import { learningErrorKey } from "@/lib/i18n/learning-errors";

import * as React from "react";
import { FolderPanel, MoveStudySetDialog } from "@/components/vocabulary/folder-organization";
import Link from "next/link";
import {
  Plus,
  Search,
  AlertCircle,
  Loader2,
  Trash2,
  Edit2,
  Folder,
  Layers,
  Calendar,
  ExternalLink,
} from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { cn } from "cn";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  collectionsApi,
  CollectionItem,
  CollectionPageResponse,
} from "@/lib/api/collections";
import { ApiError } from "@/lib/api/client";

export default function CollectionsPage() {
  const { t, locale } = useLocale();
  const [data, setData] = React.useState<CollectionPageResponse | null>(null);
  const [isLoading, setIsLoading] = React.useState(true);
  const [error, setError] = React.useState<TranslationKey | null>(null);

  const [folderScope, setFolderScope] = React.useState("all");
  const [page, setPage] = React.useState(0);
  const [folderVersion, setFolderVersion] = React.useState(0);
  const [movingItem, setMovingItem] = React.useState<CollectionItem | null>(null);
  const queueVersion = React.useRef(0);

  // Search & Filter
  const [search, setSearch] = React.useState("");

  // Dialogs State
  const [isCreateOpen, setIsCreateOpen] = React.useState(false);
  const [newTitle, setNewTitle] = React.useState("");
  const [newDescription, setNewDescription] = React.useState("");
  const [createError, setCreateError] = React.useState<TranslationKey | null>(null);
  const [isCreating, setIsCreating] = React.useState(false);

  const [editingItem, setEditingItem] = React.useState<CollectionItem | null>(null);
  const [editTitle, setEditTitle] = React.useState("");
  const [editDescription, setEditDescription] = React.useState("");
  const [editError, setEditError] = React.useState<TranslationKey | null>(null);
  const [isUpdating, setIsUpdating] = React.useState(false);

  const [itemToDelete, setItemToDelete] = React.useState<CollectionItem | null>(null);
  const [isDeleting, setIsDeleting] = React.useState(false);
  const [deleteError, setDeleteError] = React.useState<TranslationKey | null>(null);

  const fetchCollections = React.useCallback(async () => {
    const requestVersion = ++queueVersion.current;
    setIsLoading(true);
    setError(null);
    try {
      const response = await collectionsApi.list({ page, size: 50,
        folderId: folderScope !== "all" && folderScope !== "unfiled" ? folderScope : undefined,
        unfiled: folderScope === "unfiled" ? true : undefined,
      });
      if (requestVersion !== queueVersion.current) return;
      if (response.content.length === 0 && page > 0) { setPage(page - 1); return; }
      setData(response);
      setFolderVersion(value => value + 1);
    } catch (err) {
      if (requestVersion !== queueVersion.current) return;
      if (err instanceof ApiError) {
        setError(learningErrorKey(err));
      } else {
        setError("learnFailedToLoadCollectionsPleaseCheckYourConnection");
      }
    } finally {
      if (requestVersion === queueVersion.current) setIsLoading(false);
    }
  }, [page, folderScope]);

  React.useEffect(() => {
    fetchCollections();
  }, [fetchCollections]);

  // Handle Create Collection
  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim()) {
      setCreateError("learnCollectionTitleIsRequired");
      return;
    }
    if (newTitle.trim().length > 100) {
      setCreateError("learnCollectionTitleMustNotExceed100Characters");
      return;
    }

    setIsCreating(true);
    setCreateError(null);
    try {
      await collectionsApi.create({
        title: newTitle.trim(),
        description: newDescription.trim() || null,
        folderId: folderScope !== "all" && folderScope !== "unfiled" ? folderScope : null,
      });
      setIsCreateOpen(false);
      setNewTitle("");
      setNewDescription("");
      await fetchCollections();
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === "TITLE_ALREADY_EXISTS") {
          setCreateError("learnACollectionWithThisTitleAlreadyExists");
        } else {
          setCreateError(learningErrorKey(err));
        }
      } else {
        setCreateError("learnAnUnexpectedErrorOccurredWhileCreatingCollection");
      }
    } finally {
      setIsCreating(false);
    }
  };

  // Open Edit Dialog
  const handleOpenEdit = (col: CollectionItem) => {
    setEditingItem(col);
    setEditTitle(col.title);
    setEditDescription(col.description || "");
    setEditError(null);
  };

  // Handle Update Collection
  const handleUpdateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingItem) return;

    if (!editTitle.trim()) {
      setEditError("learnCollectionTitleIsRequired");
      return;
    }
    if (editTitle.trim().length > 100) {
      setEditError("learnCollectionTitleMustNotExceed100Characters");
      return;
    }

    setIsUpdating(true);
    setEditError(null);
    try {
      await collectionsApi.update(editingItem.id, {
        title: editTitle.trim(),
        description: editDescription.trim() || null,
      });
      setEditingItem(null);
      await fetchCollections();
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === "TITLE_ALREADY_EXISTS") {
          setEditError("learnACollectionWithThisTitleAlreadyExists");
        } else {
          setEditError(learningErrorKey(err));
        }
      } else {
        setEditError("learnFailedToUpdateCollection");
      }
    } finally {
      setIsUpdating(false);
    }
  };

  // Handle Delete Collection (Safe Non-Destructive)
  const handleDeleteSubmit = async () => {
    if (!itemToDelete) return;

    setIsDeleting(true);
    setDeleteError(null);
    try {
      await collectionsApi.delete(itemToDelete.id);
      setItemToDelete(null);
      await fetchCollections();
    } catch (err) {
      if (err instanceof ApiError) {
        setDeleteError(learningErrorKey(err));
      } else {
        setDeleteError("learnFailedToDeleteCollection");
      }
    } finally {
      setIsDeleting(false);
    }
  };

  // Filter collections by search term
  const filteredCollections = (data?.content || []).filter((c) =>
    c.title.toLowerCase().includes(search.toLowerCase()) ||
    (c.description && c.description.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <div className="learning-module space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-border pb-5">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">{t("learnCollectionsDecks")}</h1>
          <p className="text-sm text-muted-foreground mt-1">{t("learnCurateThematicDecksAndOrganizeYourVocabularyForFocusedStudySessions")}</p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            onClick={() => {
              setIsCreateOpen(true);
              setCreateError(null);
              setNewTitle("");
              setNewDescription("");
            }}
            className="flex items-center gap-1.5"
            id="create-collection-btn"
          >
            <Plus className="h-4 w-4" />
            <span>{t("learnCreateCollection")}</span>
          </Button>
        </div>
      </div>

      <FolderPanel scope={folderScope} onSelect={value => { setFolderScope(value); setPage(0); }}
        version={folderVersion} onChanged={fetchCollections} />
      <h2 className="font-semibold">{t(folderScope === "all" ? "folderAllStudySets" : folderScope === "unfiled" ? "folderUnfiled" : "folderSelectedStudySets")}</h2>
      <p className="text-xs text-muted-foreground">{t("folderSearchPage")}</p>
      {/* Search Input Bar */}
      <div className="flex flex-col sm:flex-row items-center gap-3">
        <div className="relative flex-1 w-full">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label={t("learnSearchCollectionsByTitleOrDescription")} placeholder={t("learnSearchCollectionsByTitleOrDescription")}
            className="pl-9 bg-background"
            id="search-collections-input"
          />
        </div>
      </div>

      {/* Loading Skeleton */}
      {isLoading && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4" id="collections-loading-skeleton">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <div
              key={i}
              className="p-5 rounded-lg border border-border bg-card animate-pulse space-y-3"
            >
              <div className="flex justify-between items-center">
                <div className="h-5 bg-muted rounded w-1/2"></div>
                <div className="h-5 bg-muted rounded w-16"></div>
              </div>
              <div className="h-4 bg-muted rounded w-3/4"></div>
              <div className="h-3 bg-muted rounded w-1/3 pt-4"></div>
            </div>
          ))}
        </div>
      )}

      {/* Error State */}
      {!isLoading && error && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 p-5 text-center space-y-3">
          <AlertCircle className="h-6 w-6 text-destructive mx-auto" />
          <p className="text-sm font-medium text-destructive">{t(error)}</p>
          <Button variant="outline" size="sm" onClick={fetchCollections}>{t("learnRetry")}</Button>
        </div>
      )}

      {/* Empty State */}
      {!isLoading && !error && filteredCollections.length === 0 && (
        <div className="rounded-lg border border-dashed border-border p-12 text-center space-y-4 bg-card/40">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-muted">
            <Folder className="h-6 w-6 text-muted-foreground" />
          </div>
          <div>
            <h3 className="text-base font-semibold text-foreground">
              {search ? t("learnNoMatchingCollections") : t(folderScope === "all" ? "learnNoCollectionsYet" : "folderEmpty")}
            </h3>
            <p className="text-sm text-muted-foreground mt-1 max-w-sm mx-auto">
              {search
                ? t("learnNoSetsMatch", { search })
                : t("learnOrganizeWordsByTopicIELTSUnitsOrCustomStudyDecksToTargetSpecificVocabulary")}
            </p>
          </div>
          {!search && (
            <Button
              onClick={() => setIsCreateOpen(true)}
              variant="outline"
              className="mt-2"
            >
              <Plus className="h-4 w-4 mr-1.5" />{t("learnCreateYourFirstCollection")}</Button>
          )}
        </div>
      )}

      {/* Collections Grid */}
      {!isLoading && !error && filteredCollections.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4" id="collections-grid">
          {filteredCollections.map((col) => (
            <div
              key={col.id}
              className="group relative flex flex-col justify-between rounded-lg border border-border bg-card p-5 hover:border-foreground/40 transition-colors"
            >
              <div>
                <div className="flex items-start justify-between gap-3">
                  <Link
                    href={`/collections/${col.id}`}
                    className="font-semibold text-base text-foreground hover:underline line-clamp-1"
                  >
                    {col.title}
                  </Link>
                  <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-muted text-foreground whitespace-nowrap">
                    <Layers className="h-3 w-3 mr-1" />
                    {t("learnWordCount", { count: col.wordCount })}
                  </span>
                </div>

                <p className="text-sm text-muted-foreground mt-2 line-clamp-2 min-h-[2.5rem]">
                  {col.description || t("learnNoDescriptionProvided")}
                </p>
              </div>

              <div className="mt-5 pt-3 border-t border-border/60 flex flex-wrap gap-2 items-center justify-between text-xs text-muted-foreground">
                <span className="flex items-center gap-1">
                  <Calendar className="h-3 w-3" />
                  {new Date(col.updatedAt).toLocaleDateString(locale, {
                    month: "short",
                    day: "numeric",
                    year: "numeric",
                  })}
                </span>

                <div className="flex flex-wrap items-center gap-1">
                  <Button variant="ghost" size="sm" onClick={() => setMovingItem(col)} aria-label={t("folderMoveNamed", { name: col.title })}>{t("folderMove")}</Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-8 px-2 text-muted-foreground hover:text-foreground"
                    onClick={() => handleOpenEdit(col)}
                    title={t("learnEditCollection")}
                  >
                    <Edit2 className="h-3.5 w-3.5" />
                    <span className="sr-only">{t("learnEdit")}</span>
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-8 px-2 text-muted-foreground hover:text-destructive"
                    onClick={() => setItemToDelete(col)}
                    title={t("learnDeleteCollection")}
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                    <span className="sr-only">{t("learnDelete")}</span>
                  </Button>
                  <Link
                    href={`/collections/${col.id}`}
                    className={cn(buttonVariants({ variant: "outline", size: "sm" }), "h-8 px-2.5 ml-1 text-xs inline-flex items-center")}
                  >{t("learnOpen")}<ExternalLink className="h-3 w-3 ml-1" />
                  </Link>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {data && data.totalPages > 1 && <div className="flex flex-wrap items-center gap-2">
        <Button variant="outline" size="sm" disabled={isLoading || page === 0} onClick={() => setPage(page - 1)}>{t("folderPrevious")}</Button>
        <span className="text-sm">{page + 1} / {data.totalPages}</span>
        <Button variant="outline" size="sm" disabled={isLoading || page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>{t("folderNext")}</Button>
      </div>}
      <MoveStudySetDialog item={movingItem} onClose={() => setMovingItem(null)} onMoved={fetchCollections} />
      {/* CREATE COLLECTION DIALOG */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent className="learning-module sm:max-w-md">
          <form noValidate onSubmit={handleCreateSubmit} className="space-y-4">
            <DialogHeader>
            <LanguageSwitcher />
              <DialogTitle>{t("learnCreateCollection")}</DialogTitle>
              <DialogDescription>{t("learnCreateAThematicDeckToGroupAndReviewRelatedVocabularyItems")}</DialogDescription>
            </DialogHeader>

            {createError && (
              <div role="alert" id="create-title-error" className="rounded border border-destructive/40 bg-destructive/10 p-2.5 text-xs text-destructive">
                {t(createError)}
              </div>
            )}

            <div className="space-y-3 py-1">
              <div className="space-y-1.5">
                <Label htmlFor="create-title">{t("learnCollectionTitle")}</Label>
                <Input
                  id="create-title" aria-invalid={!!createError} aria-describedby={createError ? "create-title-error" : undefined}
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  placeholder={t("learnEGIELTSAcademicCorePhrasalVerbs")}
                  maxLength={100}
                  required
                  autoFocus
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="create-description">{t("learnDescriptionOptional")}</Label>
                <Textarea
                  id="create-description"
                  value={newDescription}
                  onChange={(e) => setNewDescription(e.target.value)}
                  placeholder={t("learnEGHighFrequencyVocabularyForIELTSReadingWritingTasks")}
                  rows={3}
                />
              </div>
            </div>

            <DialogFooter className="gap-2 sm:gap-0">
              <Button
                type="button"
                variant="outline"
                onClick={() => setIsCreateOpen(false)}
                disabled={isCreating}
              >{t("learnCancel")}</Button>
              <Button type="submit" disabled={isCreating} id="submit-create-collection-btn">
                {isCreating ? (
                  <>
                    <Loader2 className="h-4 w-4 mr-1.5 animate-spin" />{t("learnCreating")}</>
                ) : (
                  t("learnCreateCollection")
                )}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* EDIT COLLECTION DIALOG */}
      <Dialog open={!!editingItem} onOpenChange={(open) => !open && setEditingItem(null)}>
        <DialogContent className="learning-module sm:max-w-md">
          <form noValidate onSubmit={handleUpdateSubmit} className="space-y-4">
            <DialogHeader>
            <LanguageSwitcher />
              <DialogTitle>{t("learnEditCollection")}</DialogTitle>
              <DialogDescription>{t("learnUpdateTheTitleAndDescriptionForThisDeck")}</DialogDescription>
            </DialogHeader>

            {editError && (
              <div role="alert" id="edit-title-error" className="rounded border border-destructive/40 bg-destructive/10 p-2.5 text-xs text-destructive">
                {t(editError)}
              </div>
            )}

            <div className="space-y-3 py-1">
              <div className="space-y-1.5">
                <Label htmlFor="edit-title">{t("learnCollectionTitle")}</Label>
                <Input
                  id="edit-title" aria-invalid={!!editError} aria-describedby={editError ? "edit-title-error" : undefined}
                  value={editTitle}
                  onChange={(e) => setEditTitle(e.target.value)}
                  maxLength={100}
                  required
                  autoFocus
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="edit-description">{t("learnDescription")}</Label>
                <Textarea
                  id="edit-description"
                  value={editDescription}
                  onChange={(e) => setEditDescription(e.target.value)}
                  rows={3}
                />
              </div>
            </div>

            <DialogFooter className="gap-2 sm:gap-0">
              <Button
                type="button"
                variant="outline"
                onClick={() => setEditingItem(null)}
                disabled={isUpdating}
              >{t("learnCancel")}</Button>
              <Button type="submit" disabled={isUpdating} id="submit-update-collection-btn">
                {isUpdating ? (
                  <>
                    <Loader2 className="h-4 w-4 mr-1.5 animate-spin" />{t("learnSaving")}</>
                ) : (
                  t("learnSaveChanges")
                )}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* DELETE CONFIRMATION DIALOG (NON-DESTRUCTIVE SEMANTICS) */}
      <Dialog open={!!itemToDelete} onOpenChange={(open) => !open && setItemToDelete(null)}>
        <DialogContent className="learning-module sm:max-w-md">
          <div className="space-y-4">
            <DialogHeader>
            <LanguageSwitcher />
              <DialogTitle className="text-destructive flex items-center gap-2">
                <AlertCircle className="h-5 w-5" />{t("learnDeleteCollection")}</DialogTitle>
              <DialogDescription>{t("learnAreYouSureYouWantToDelete")}{" "}<span className="font-semibold text-foreground">&quot;{itemToDelete?.title}&quot;</span>?
              </DialogDescription>
            </DialogHeader>

            <div className="rounded-md border border-border bg-muted/50 p-3.5 text-xs text-muted-foreground space-y-1.5">
              <p className="font-medium text-foreground">{t("learnNonDestructiveDeletionGuarantee")}</p>
              <p>{t("learnDeletingThisCollectionRemovesOnlyTheDeckOrganizationAll")}{" "}
                <span className="font-medium text-foreground">{itemToDelete?.wordCount || 0}{" "}{t("learnVocabularyWords")}</span>{" "}{t("learnInsideItWillRemainSafeInYourVocabularyLibraryAndYourSpacedRepetitionSRSReviewSchedulesAn")}</p>
            </div>

            {deleteError && (
              <div className="rounded border border-destructive/40 bg-destructive/10 p-2.5 text-xs text-destructive">
                {t(deleteError)}
              </div>
            )}

            <DialogFooter className="gap-2 sm:gap-0">
              <Button
                type="button"
                variant="outline"
                onClick={() => setItemToDelete(null)}
                disabled={isDeleting}
              >{t("learnCancel")}</Button>
              <Button
                type="button"
                variant="destructive"
                onClick={handleDeleteSubmit}
                disabled={isDeleting}
                id="confirm-delete-collection-btn"
              >
                {isDeleting ? (
                  <>
                    <Loader2 className="h-4 w-4 mr-1.5 animate-spin" />{t("learnDeleting")}</>
                ) : (
                  t("learnDeleteCollection")
                )}
              </Button>
            </DialogFooter>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
