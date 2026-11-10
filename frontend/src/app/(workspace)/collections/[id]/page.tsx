"use client";

import { LanguageSwitcher } from "@/components/auth/language-switcher";
import { useLocale } from "@/lib/i18n/locale-provider";
import type { TranslationKey } from "@/lib/i18n/dictionaries";
import { learningErrorKey } from "@/lib/i18n/learning-errors";

import * as React from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  ArrowLeft,
  Plus,
  Search,
  AlertCircle,
  Loader2,
  Trash2,
  Edit2,
  Layers,
  BookOpen,
  Calendar,
  X,
  Check,
  RotateCcw,
} from "lucide-react";
import { Button } from "@/components/ui/button";
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
} from "@/lib/api/collections";
import {
  vocabularyApi,
  VocabularyItem,
  VocabularyPageResponse,
} from "@/lib/api/vocabulary";
import { ApiError } from "@/lib/api/client";

export default function CollectionDetailPage() {
  const { t, locale } = useLocale();
  const params = useParams();
  const router = useRouter();
  const collectionId = params.id as string;

  const [collection, setCollection] = React.useState<CollectionItem | null>(null);
  const [vocabData, setVocabData] = React.useState<VocabularyPageResponse | null>(null);
  const [isLoading, setIsLoading] = React.useState(true);
  const [error, setError] = React.useState<TranslationKey | null>(null);

  // Search filter inside collection
  const [search, setSearch] = React.useState("");

  // Add words modal state
  const [isAddWordsOpen, setIsAddWordsOpen] = React.useState(false);
  const [libraryWords, setLibraryWords] = React.useState<VocabularyItem[]>([]);
  const [selectedWordIds, setSelectedWordIds] = React.useState<Set<string>>(new Set());
  const [isLoadingLibrary, setIsLoadingLibrary] = React.useState(false);
  const [librarySearch, setLibrarySearch] = React.useState("");
  const [isAssigning, setIsAssigning] = React.useState(false);
  const [addWordsError, setAddWordsError] = React.useState<TranslationKey | null>(null);

  // Edit Collection state
  const [isEditOpen, setIsEditOpen] = React.useState(false);
  const [editTitle, setEditTitle] = React.useState("");
  const [editDescription, setEditDescription] = React.useState("");
  const [editError, setEditError] = React.useState<TranslationKey | null>(null);
  const [isUpdating, setIsUpdating] = React.useState(false);

  // Remove Word state
  const [wordToRemove, setWordToRemove] = React.useState<VocabularyItem | null>(null);
  const [isRemovingWord, setIsRemovingWord] = React.useState(false);

  // Delete Collection state
  const [isDeleteOpen, setIsDeleteOpen] = React.useState(false);
  const [isDeleting, setIsDeleting] = React.useState(false);
  const [deleteError, setDeleteError] = React.useState<TranslationKey | null>(null);

  const fetchCollectionData = React.useCallback(async () => {
    if (!collectionId) return;
    setIsLoading(true);
    setError(null);
    try {
      const [colRes, vocabRes] = await Promise.all([
        collectionsApi.getById(collectionId),
        collectionsApi.listVocabularies(collectionId, { page: 0, size: 100 }),
      ]);
      setCollection(colRes);
      setVocabData(vocabRes);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 404) {
          setError("learnCollectionNotFoundOrAccessDenied");
        } else {
          setError(learningErrorKey(err));
        }
      } else {
        setError("learnFailedToLoadCollectionDetails");
      }
    } finally {
      setIsLoading(false);
    }
  }, [collectionId]);

  React.useEffect(() => {
    fetchCollectionData();
  }, [fetchCollectionData]);

  // Load active library words for the "Add Words" dialog
  const handleOpenAddWords = async () => {
    setIsAddWordsOpen(true);
    setSelectedWordIds(new Set());
    setLibrarySearch("");
    setAddWordsError(null);
    setIsLoadingLibrary(true);
    try {
      const res = await vocabularyApi.list({ page: 0, size: 100 });
      setLibraryWords(res.content);
    } catch {
      setAddWordsError("learnFailedToFetchVocabularyLibrary");
    } finally {
      setIsLoadingLibrary(false);
    }
  };

  const toggleSelectWord = (id: string) => {
    setSelectedWordIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const handleAssignWordsSubmit = async () => {
    if (selectedWordIds.size === 0) return;

    setIsAssigning(true);
    setAddWordsError(null);
    try {
      await collectionsApi.assignVocabularies(collectionId, Array.from(selectedWordIds));
      setIsAddWordsOpen(false);
      await fetchCollectionData();
    } catch (err) {
      if (err instanceof ApiError) {
        setAddWordsError(learningErrorKey(err));
      } else {
        setAddWordsError("learnFailedToAddWordsToCollection");
      }
    } finally {
      setIsAssigning(false);
    }
  };

  // Handle Edit Collection
  const handleOpenEdit = () => {
    if (!collection) return;
    setEditTitle(collection.title);
    setEditDescription(collection.description || "");
    setEditError(null);
    setIsEditOpen(true);
  };

  const handleEditSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editTitle.trim()) {
      setEditError("learnTitleIsRequired");
      return;
    }

    setIsUpdating(true);
    setEditError(null);
    try {
      const updated = await collectionsApi.update(collectionId, {
        title: editTitle.trim(),
        description: editDescription.trim() || null,
      });
      setCollection(updated);
      setIsEditOpen(false);
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

  // Handle Remove Word from Collection
  const handleRemoveWord = async () => {
    if (!wordToRemove) return;

    setIsRemovingWord(true);
    try {
      await collectionsApi.removeVocabulary(collectionId, wordToRemove.id);
      setWordToRemove(null);
      await fetchCollectionData();
    } catch (err) {
      setError(learningErrorKey(err, "learnFailedToRemoveWord"));
    } finally {
      setIsRemovingWord(false);
    }
  };

  // Handle Delete Collection
  const handleDeleteSubmit = async () => {
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await collectionsApi.delete(collectionId);
      router.push("/collections");
    } catch (err) {
      if (err instanceof ApiError) {
        setDeleteError(learningErrorKey(err));
      } else {
        setDeleteError("learnFailedToDeleteCollection");
      }
      setIsDeleting(false);
    }
  };

  // Words currently in the collection
  const existingWordIds = new Set((vocabData?.content || []).map((w) => w.id));

  // Filter words available to add
  const availableWordsToAdd = libraryWords
    .filter((w) => !existingWordIds.has(w.id))
    .filter((w) =>
      w.term.toLowerCase().includes(librarySearch.toLowerCase()) ||
      w.vietnameseMeaning.toLowerCase().includes(librarySearch.toLowerCase())
    );

  // Filter words displayed in collection
  const filteredWords = (vocabData?.content || []).filter((w) =>
    w.term.toLowerCase().includes(search.toLowerCase()) ||
    w.vietnameseMeaning.toLowerCase().includes(search.toLowerCase()) ||
    (w.englishDefinition && w.englishDefinition.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <div className="learning-module space-y-6">
      {/* Back Navigation Bar */}
      <div>
        <Link
          href="/collections"
          className="inline-flex items-center text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="h-4 w-4 mr-1.5" />{t("learnBackToCollections")}</Link>
      </div>

      {/* Loading State */}
      {isLoading && (
        <div className="space-y-4 animate-pulse">
          <div className="h-8 bg-muted rounded w-1/3"></div>
          <div className="h-4 bg-muted rounded w-2/3"></div>
          <div className="h-32 bg-muted rounded"></div>
        </div>
      )}

      {/* Error State */}
      {!isLoading && error && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 p-6 text-center space-y-3">
          <AlertCircle className="h-6 w-6 text-destructive mx-auto" />
          <p className="text-sm font-medium text-destructive">{t(error)}</p>
          <Button variant="outline" size="sm" onClick={fetchCollectionData}>{t("learnRetry")}</Button>
        </div>
      )}

      {/* Main Content */}
      {!isLoading && !error && collection && (
        <div className="space-y-6">
          {/* Deck Header */}
          <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 border-b border-border pb-5">
            <div>
              <div className="flex items-center gap-3">
                <h1 className="text-2xl font-bold tracking-tight text-foreground">
                  {collection.title}
                </h1>
                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-muted text-foreground">
                  <Layers className="h-3 w-3 mr-1" />
                  {t("learnWordCount", { count: collection.wordCount })}
                </span>
              </div>
              <p className="text-sm text-muted-foreground mt-1 max-w-2xl">
                {collection.description || t("learnNoDescriptionProvidedForThisCollection")}
              </p>
              <div className="flex items-center gap-4 text-xs text-muted-foreground mt-3">
                <span className="flex items-center gap-1">
                  <Calendar className="h-3 w-3" />{t("learnCreated")}{" "}{new Date(collection.createdAt).toLocaleDateString(locale)}
                </span>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <Link className="inline-flex items-center rounded-md border border-border px-3 py-2 text-sm font-medium hover:bg-muted focus-visible:outline-2 focus-visible:outline-ring" href={"/vocabulary/import?collectionId=" + encodeURIComponent(collectionId) + "&mode=paste"}>{t("learnImportVocabulary")}</Link>
              <Link className="inline-flex items-center rounded-md border border-border px-3 py-2 text-sm font-medium hover:bg-muted focus-visible:outline-2 focus-visible:outline-ring" href={`/review?collectionId=${encodeURIComponent(collectionId)}`}>{t("learnReviewStudySet")}</Link>
              <Button
                onClick={handleOpenAddWords}
                className="flex items-center gap-1.5"
                id="add-words-to-collection-btn"
              >
                <Plus className="h-4 w-4" />
                <span>{t("learnAddWords")}</span>
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={handleOpenEdit}
                title={t("learnEditCollection")}
              >
                <Edit2 className="h-3.5 w-3.5 mr-1" />{t("learnEdit")}</Button>
              <Button
                variant="outline"
                size="sm"
                className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                onClick={() => setIsDeleteOpen(true)}
                title={t("learnDeleteCollection")}
              >
                <Trash2 className="h-3.5 w-3.5 mr-1" />{t("learnDeleteDeck")}</Button>
            </div>
          </div>

          {/* Search bar within collection */}
          {vocabData && vocabData.content.length > 0 && (
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                aria-label={t("learnSearchWordsInThisCollection")} placeholder={t("learnSearchWordsInThisCollection")}
                className="pl-9 bg-background max-w-md"
              />
            </div>
          )}

          {/* Empty Collection State */}
          {vocabData && vocabData.content.length === 0 && (
            <div className="rounded-lg border border-dashed border-border p-12 text-center space-y-4 bg-card/40">
              <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-muted">
                <BookOpen className="h-6 w-6 text-muted-foreground" />
              </div>
              <div>
                <h3 className="text-base font-semibold text-foreground">{t("learnThisCollectionIsEmpty")}</h3>
                <p className="text-sm text-muted-foreground mt-1 max-w-sm mx-auto">{t("learnAddWordsFromYourExistingVocabularyLibraryToOrganizeThemIntoThisThematicDeck")}</p>
              </div>
              <Button onClick={handleOpenAddWords} className="mt-2">
                <Plus className="h-4 w-4 mr-1.5" />{t("learnAddWordsToCollection")}</Button>
            </div>
          )}

          {/* Words List in Collection */}
          {filteredWords.length > 0 && (
            <div className="rounded-lg border border-border bg-card overflow-hidden">
              <div className="divide-y divide-border">
                {filteredWords.map((item) => (
                  <div
                    key={item.id}
                    className="p-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 hover:bg-muted/30 transition-colors"
                  >
                    <div className="space-y-1 flex-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <Link
                          href={`/vocabulary/${item.id}`}
                          className="font-semibold text-base text-foreground hover:underline"
                        >
                          {item.term}
                        </Link>
                        <span className="text-xs px-2 py-0.5 rounded bg-muted text-muted-foreground font-mono">
                          {item.partOfSpeech}
                        </span>
                        {item.phoneticIpa && (
                          <span className="text-xs text-muted-foreground font-mono">
                            {item.phoneticIpa}
                          </span>
                        )}
                        {item.difficultyLevel && (
                          <span className="text-xs px-1.5 py-0.2 rounded border border-border text-muted-foreground">
                            {item.difficultyLevel}
                          </span>
                        )}
                        {item.srsStatus && (
                          <span className="text-xs px-1.5 py-0.2 rounded bg-muted/60 text-muted-foreground">{t("learnSRS")}{" "}{item.srsStatus.state}
                          </span>
                        )}
                      </div>

                      <p className="text-sm text-foreground">
                        {item.vietnameseMeaning}
                      </p>

                      {item.englishDefinition && (
                        <p className="text-xs text-muted-foreground line-clamp-1">
                          {item.englishDefinition}
                        </p>
                      )}
                    </div>

                    <div className="flex items-center gap-2 self-end sm:self-center">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-xs text-muted-foreground hover:text-destructive flex items-center gap-1"
                        onClick={() => setWordToRemove(item)}
                        title={t("learnRemoveFromCollection")}
                      >
                        <X className="h-3.5 w-3.5" />
                        <span>{t("learnRemove")}</span>
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {filteredWords.length === 0 && vocabData && vocabData.content.length > 0 && (
            <div className="p-8 text-center text-sm text-muted-foreground">{t("learnNoWordsInThisCollectionMatch")}{search}&quot;.
            </div>
          )}
        </div>
      )}

      {/* ADD WORDS DIALOG */}
      <Dialog open={isAddWordsOpen} onOpenChange={setIsAddWordsOpen}>
        <DialogContent className="learning-module sm:max-w-lg">
          <div className="space-y-4">
            <DialogHeader>
            <LanguageSwitcher />
              <DialogTitle>{t("learnAddWordsToDeck")}</DialogTitle>
              <DialogDescription>{t("learnSelectWordsFromYourVocabularyLibraryToAssignTo")}{" "}
                <span className="font-semibold text-foreground">&quot;{collection?.title}&quot;</span>.
              </DialogDescription>
            </DialogHeader>

            {addWordsError && (
              <div className="rounded border border-destructive/40 bg-destructive/10 p-2.5 text-xs text-destructive">
                {t(addWordsError)}
              </div>
            )}

            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
              <Input
                value={librarySearch}
                onChange={(e) => setLibrarySearch(e.target.value)}
                placeholder={t("learnFilterLibraryWords")}
                className="pl-8 text-xs h-9"
              />
            </div>

            {/* List of available words */}
            <div className="max-h-60 overflow-y-auto border border-border rounded-md divide-y divide-border">
              {isLoadingLibrary ? (
                <div className="p-6 text-center text-xs text-muted-foreground flex items-center justify-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" />{t("learnLoadingLibrary")}</div>
              ) : availableWordsToAdd.length === 0 ? (
                <div className="p-6 text-center text-xs text-muted-foreground">
                  {librarySearch
                    ? t("learnNoMatchingVocabularyFound")
                    : t("learnAllWordsInYourLibraryAreAlreadyInThisCollectionOrYourLibraryIsEmpty")}
                </div>
              ) : (
                availableWordsToAdd.map((word) => {
                  const isSelected = selectedWordIds.has(word.id);
                  return (
                    <div
                      key={word.id}
                      role="checkbox" tabIndex={0} aria-checked={isSelected}
                      onKeyDown={(event) => { if (event.key === " " || event.key === "Enter") { event.preventDefault(); toggleSelectWord(word.id); } }}
                      onClick={() => toggleSelectWord(word.id)}
                      className={`p-3 flex items-center justify-between cursor-pointer transition-colors ${
                        isSelected ? "bg-muted" : "hover:bg-muted/50"
                      }`}
                    >
                      <div className="text-xs space-y-0.5">
                        <div className="font-medium text-foreground flex items-center gap-1.5">
                          <span>{word.term}</span>
                          <span className="text-[10px] text-muted-foreground font-mono">
                            ({word.partOfSpeech})
                          </span>
                        </div>
                        <div className="text-muted-foreground">{word.vietnameseMeaning}</div>
                      </div>

                      <div
                        className={`h-4 w-4 rounded border flex items-center justify-center transition-colors ${
                          isSelected
                            ? "bg-foreground border-foreground text-background"
                            : "border-border"
                        }`}
                      >
                        {isSelected && <Check className="h-3 w-3" />}
                      </div>
                    </div>
                  );
                })
              )}
            </div>

            <DialogFooter className="gap-2 sm:gap-0">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setIsAddWordsOpen(false)}
                disabled={isAssigning}
              >{t("learnCancel")}</Button>
              <Button
                size="sm"
                onClick={handleAssignWordsSubmit}
                disabled={isAssigning || selectedWordIds.size === 0}
                id="submit-assign-words-btn"
              >
                {isAssigning ? (
                  <>
                    <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />{t("learnAdding")}</>
                ) : (
                  t("learnAddWordCount", { count: selectedWordIds.size })
                )}
              </Button>
            </DialogFooter>
          </div>
        </DialogContent>
      </Dialog>

      {/* REMOVE WORD CONFIRMATION (NON-DESTRUCTIVE) */}
      <Dialog open={!!wordToRemove} onOpenChange={(open) => !open && setWordToRemove(null)}>
        <DialogContent className="learning-module sm:max-w-md">
          <div className="space-y-4">
            <DialogHeader>
            <LanguageSwitcher />
              <DialogTitle className="flex items-center gap-2">
                <RotateCcw className="h-5 w-5 text-muted-foreground" />{t("learnRemoveFromCollection")}</DialogTitle>
              <DialogDescription>{t("learnRemove")}{" "}<span className="font-semibold text-foreground">&quot;{wordToRemove?.term}&quot;</span>{" "}{t("learnFromThisCollection")}</DialogDescription>
            </DialogHeader>

            <div className="rounded-md border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
              <p className="font-medium text-foreground">{t("learnNonDestructiveSemantics")}</p>
              <p className="mt-1">{t("learnThisActionOnlyRemovesTheWordFromThisDeckTheWordWillRemainFullyAvailableInYourMainVocabul")}</p>
            </div>

            <DialogFooter className="gap-2 sm:gap-0">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setWordToRemove(null)}
                disabled={isRemovingWord}
              >{t("learnCancel")}</Button>
              <Button
                variant="destructive"
                size="sm"
                onClick={handleRemoveWord}
                disabled={isRemovingWord}
                id="confirm-remove-word-btn"
              >
                {isRemovingWord ? (
                  <>
                    <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />{t("learnRemoving")}</>
                ) : (
                  t("learnRemoveFromDeck")
                )}
              </Button>
            </DialogFooter>
          </div>
        </DialogContent>
      </Dialog>

      {/* EDIT COLLECTION DIALOG */}
      <Dialog open={isEditOpen} onOpenChange={setIsEditOpen}>
        <DialogContent className="learning-module sm:max-w-md">
          <form noValidate onSubmit={handleEditSubmit} className="space-y-4">
            <DialogHeader>
            <LanguageSwitcher />
              <DialogTitle>{t("learnEditCollection")}</DialogTitle>
              <DialogDescription>{t("learnUpdateCollectionTitleAndDescription")}</DialogDescription>
            </DialogHeader>

            {editError && (
              <div role="alert" id="edit-title-error" className="rounded border border-destructive/40 bg-destructive/10 p-2.5 text-xs text-destructive">
                {t(editError)}
              </div>
            )}

            <div className="space-y-3 py-1">
              <div className="space-y-1.5">
                <Label htmlFor="edit-col-title">{t("learnCollectionTitle")}</Label>
                <Input
                  id="edit-col-title"
                  value={editTitle}
                  onChange={(e) => setEditTitle(e.target.value)}
                  maxLength={100}
                  required
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="edit-col-description">{t("learnDescription")}</Label>
                <Textarea
                  id="edit-col-description"
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
                size="sm"
                onClick={() => setIsEditOpen(false)}
                disabled={isUpdating}
              >{t("learnCancel")}</Button>
              <Button type="submit" size="sm" disabled={isUpdating}>
                {isUpdating ? t("learnSaving") : t("learnSaveChanges")}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* DELETE COLLECTION DIALOG */}
      <Dialog open={isDeleteOpen} onOpenChange={setIsDeleteOpen}>
        <DialogContent className="learning-module sm:max-w-md">
          <div className="space-y-4">
            <DialogHeader>
            <LanguageSwitcher />
              <DialogTitle className="text-destructive flex items-center gap-2">
                <AlertCircle className="h-5 w-5" />{t("learnDeleteCollection")}</DialogTitle>
              <DialogDescription>{t("learnDelete")}{" "}<span className="font-semibold text-foreground">&quot;{collection?.title}&quot;</span>?
              </DialogDescription>
            </DialogHeader>

            <div className="rounded-md border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
              <p className="font-medium text-foreground">{t("learnNonDestructiveDeletionGuarantee")}</p>
              <p className="mt-1">{t("learnDeletingThisCollectionWillNOTDeleteYourVocabularyAllWordsInThisDeckWillRemainAvailableIn")}</p>
            </div>

            {deleteError && (
              <div className="rounded border border-destructive/40 bg-destructive/10 p-2.5 text-xs text-destructive">
                {t(deleteError)}
              </div>
            )}

            <DialogFooter className="gap-2 sm:gap-0">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setIsDeleteOpen(false)}
                disabled={isDeleting}
              >{t("learnCancel")}</Button>
              <Button
                variant="destructive"
                size="sm"
                onClick={handleDeleteSubmit}
                disabled={isDeleting}
                id="confirm-delete-deck-btn"
              >
                {isDeleting ? t("learnDeleting") : t("learnDeleteCollection")}
              </Button>
            </DialogFooter>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
