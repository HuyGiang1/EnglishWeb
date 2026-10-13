"use client";

import * as React from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  ArrowLeft,
  Edit2,
  Trash2,
  AlertCircle,
  Loader2,
  Save,
  X,
  Sparkles,
  Layers,
  Plus,
} from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { cn } from "cn";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { vocabularyApi, VocabularyItem } from "@/lib/api/vocabulary";
import {
  collectionsApi,
  CollectionSummaryItem,
  CollectionItem,
} from "@/lib/api/collections";
import {
  vocabularySchema,
  type VocabularyFormData,
} from "@/lib/vocabulary/vocabulary-schema";

export default function VocabularyDetailPage() {
  const params = useParams();
  const router = useRouter();
  const id = params?.id as string;

  const [vocabulary, setVocabulary] = React.useState<VocabularyItem | null>(null);
  const [assignedCollections, setAssignedCollections] = React.useState<CollectionSummaryItem[]>([]);
  const [allCollections, setAllCollections] = React.useState<CollectionItem[]>([]);
  const [isAssignModalOpen, setIsAssignModalOpen] = React.useState(false);
  const [selectedDeckToAssign, setSelectedDeckToAssign] = React.useState<string>("");
  const [isAssigningDeck, setIsAssigningDeck] = React.useState(false);
  const [assignError, setAssignError] = React.useState<string | null>(null);

  const [isLoading, setIsLoading] = React.useState(true);
  const [loadError, setLoadError] = React.useState<string | null>(null);
  const [isEditing, setIsEditing] = React.useState(false);
  const [isSaving, setIsSaving] = React.useState(false);
  const [saveError, setSaveError] = React.useState<string | null>(null);
  const [isDeleteDialogOpen, setIsDeleteDialogOpen] = React.useState(false);
  const [isDeleting, setIsDeleting] = React.useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<VocabularyFormData>({
    resolver: zodResolver(vocabularySchema),
  });

  const fetchItem = React.useCallback(async () => {
    if (!id) return;
    setIsLoading(true);
    setLoadError(null);
    try {
      const [data, cols] = await Promise.all([
        vocabularyApi.getById(id),
        collectionsApi.getCollectionsForVocabulary(id).catch(() => []),
      ]);
      setVocabulary(data);
      setAssignedCollections(cols);
      reset({
        term: data.term,
        partOfSpeech: data.partOfSpeech as VocabularyFormData["partOfSpeech"],
        phoneticIpa: data.phoneticIpa || "",
        vietnameseMeaning: data.vietnameseMeaning,
        englishDefinition: data.englishDefinition || "",
        exampleSentence: data.exampleSentence || "",
        exampleTranslation: data.exampleTranslation || "",
        notes: data.notes || "",
        difficultyLevel: (data.difficultyLevel as VocabularyFormData["difficultyLevel"]) || "",
      });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Vocabulary item not found";
      setLoadError(msg);
    } finally {
      setIsLoading(false);
    }
  }, [id, reset]);

  React.useEffect(() => {
    fetchItem();
  }, [fetchItem]);

  const handleOpenAssignModal = async () => {
    setIsAssignModalOpen(true);
    setSelectedDeckToAssign("");
    setAssignError(null);
    try {
      const res = await collectionsApi.list({ size: 100 });
      setAllCollections(res.content);
    } catch {
      setAssignError("Failed to load user collections.");
    }
  };

  const handleAssignToDeck = async () => {
    if (!selectedDeckToAssign || !vocabulary) return;
    setIsAssigningDeck(true);
    setAssignError(null);
    try {
      await collectionsApi.assignVocabularies(selectedDeckToAssign, [vocabulary.id]);
      setIsAssignModalOpen(false);
      const cols = await collectionsApi.getCollectionsForVocabulary(vocabulary.id);
      setAssignedCollections(cols);
    } catch (err: unknown) {
      setAssignError(err instanceof Error ? err.message : "Failed to assign to deck");
    } finally {
      setIsAssigningDeck(false);
    }
  };

  const onSave = React.useCallback(
    async (data: VocabularyFormData) => {
      if (!vocabulary) return;
      setIsSaving(true);
      setSaveError(null);
      try {
        const updated = await vocabularyApi.update(vocabulary.id, {
          term: data.term.trim(),
          partOfSpeech: data.partOfSpeech,
          phoneticIpa: data.phoneticIpa?.trim() || null,
          vietnameseMeaning: data.vietnameseMeaning.trim(),
          englishDefinition: data.englishDefinition?.trim() || null,
          exampleSentence: data.exampleSentence?.trim() || null,
          exampleTranslation: data.exampleTranslation?.trim() || null,
          notes: data.notes?.trim() || null,
          difficultyLevel: data.difficultyLevel || null,
        });
        setVocabulary(updated);
        setIsEditing(false);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : "Failed to update vocabulary";
        setSaveError(msg);
      } finally {
        setIsSaving(false);
      }
    },
    [vocabulary]
  );

  // Keyboard shortcut: Mod+S to save when editing
  React.useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (isEditing && (e.metaKey || e.ctrlKey) && e.key === "s") {
        e.preventDefault();
        handleSubmit(onSave)();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isEditing, handleSubmit, onSave]);

  const onDeleteConfirm = async () => {
    if (!vocabulary) return;
    setIsDeleting(true);
    try {
      await vocabularyApi.delete(vocabulary.id);
      setIsDeleteDialogOpen(false);
      router.push("/vocabulary");
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to delete vocabulary";
      setSaveError(msg);
      setIsDeleting(false);
      setIsDeleteDialogOpen(false);
    }
  };

  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="h-6 w-32 animate-pulse rounded bg-muted" />
        <div className="h-48 w-full animate-pulse rounded-lg border border-border bg-card" />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="h-40 animate-pulse rounded-lg border border-border bg-card" />
          <div className="h-40 animate-pulse rounded-lg border border-border bg-card" />
        </div>
      </div>
    );
  }

  if (loadError || !vocabulary) {
    return (
      <div className="rounded-lg border border-border bg-card p-12 text-center">
        <AlertCircle className="mx-auto h-8 w-8 text-neutral-500 mb-3" />
        <h2 className="text-lg font-semibold text-foreground mb-1">
          Vocabulary Not Found
        </h2>
        <p className="text-sm text-muted-foreground mb-6">
          {loadError || "The requested vocabulary word does not exist or has been removed."}
        </p>
        <Link href="/vocabulary" className={cn(buttonVariants({ variant: "outline" }))}>
          <ArrowLeft className="mr-2 h-4 w-4" />
          Back to Library
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Navigation & Breadcrumbs */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <nav aria-label="Breadcrumb" className="flex items-center text-sm text-muted-foreground">
          <Link href="/vocabulary" className="hover:text-foreground transition-colors">
            Vocabulary
          </Link>
          <span className="mx-2">/</span>
          <span className="font-medium text-foreground truncate max-w-xs">{vocabulary.term}</span>
        </nav>

        <div className="flex items-center gap-2">
          {!isEditing ? (
            <>
              <Button variant="outline" size="sm" onClick={() => setIsEditing(true)}>
                <Edit2 className="mr-1.5 h-3.5 w-3.5" />
                Edit
              </Button>
              <Button
                variant="outline"
                size="sm"
                className="text-neutral-700 hover:text-black hover:bg-neutral-100"
                onClick={() => setIsDeleteDialogOpen(true)}
              >
                <Trash2 className="mr-1.5 h-3.5 w-3.5" />
                Delete
              </Button>
            </>
          ) : (
            <>
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  reset();
                  setIsEditing(false);
                  setSaveError(null);
                }}
                disabled={isSaving}
              >
                <X className="mr-1.5 h-3.5 w-3.5" />
                Cancel
              </Button>
              <Button
                size="sm"
                onClick={handleSubmit(onSave)}
                disabled={isSaving}
              >
                {isSaving ? (
                  <>
                    <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                    Saving...
                  </>
                ) : (
                  <>
                    <Save className="mr-1.5 h-3.5 w-3.5" />
                    Save (Mod+S)
                  </>
                )}
              </Button>
            </>
          )}
        </div>
      </div>

      {saveError && (
        <div
          role="alert"
          className="flex items-center gap-2 rounded-md border border-neutral-300 bg-neutral-100 p-3 text-sm text-neutral-900"
        >
          <AlertCircle className="h-4 w-4 shrink-0 text-neutral-700" />
          <span>{saveError}</span>
        </div>
      )}

      {/* Main Content Area */}
      {!isEditing ? (
        /* View Mode */
        <div className="space-y-6">
          {/* Word Hero Card */}
          <div className="rounded-lg border border-border bg-card p-6 sm:p-8 space-y-4">
            <div className="flex flex-wrap items-baseline justify-between gap-3">
              <div className="flex items-baseline gap-3">
                <h1 className="text-3xl font-bold tracking-tight text-foreground">
                  {vocabulary.term}
                </h1>
                {vocabulary.phoneticIpa && (
                  <span className="font-mono text-sm text-muted-foreground">
                    {vocabulary.phoneticIpa}
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2">
                <span className="inline-flex items-center rounded-md border border-border px-2 py-0.5 text-xs font-mono font-medium text-foreground bg-muted">
                  {vocabulary.partOfSpeech}
                </span>
                {vocabulary.difficultyLevel && (
                  <span className="inline-flex items-center rounded-md border border-foreground px-2 py-0.5 text-xs font-semibold text-foreground">
                    {vocabulary.difficultyLevel}
                  </span>
                )}
              </div>
            </div>

            <div className="pt-2 border-t border-border">
              <p className="text-sm font-medium text-muted-foreground">Vietnamese Meaning</p>
              <p className="text-lg font-medium text-foreground mt-0.5">
                {vocabulary.vietnameseMeaning}
              </p>
            </div>

            {vocabulary.englishDefinition && (
              <div className="pt-2">
                <p className="text-sm font-medium text-muted-foreground">English Definition</p>
                <p className="text-sm text-foreground mt-0.5 leading-relaxed">
                  {vocabulary.englishDefinition}
                </p>
              </div>
            )}
          </div>

          {/* Details Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="rounded-lg border border-border bg-card p-6 space-y-4">
              <h2 className="text-sm font-semibold tracking-wide uppercase text-muted-foreground">
                Context & Examples
              </h2>

              <div className="space-y-3">
                <div>
                  <p className="text-xs text-muted-foreground">Example Sentence</p>
                  <p className="text-sm text-foreground mt-0.5 italic">
                    {vocabulary.exampleSentence ? `"${vocabulary.exampleSentence}"` : "None recorded"}
                  </p>
                </div>

                {vocabulary.exampleTranslation && (
                  <div>
                    <p className="text-xs text-muted-foreground">Example Translation</p>
                    <p className="text-sm text-foreground mt-0.5">
                      {vocabulary.exampleTranslation}
                    </p>
                  </div>
                )}

                {vocabulary.notes && (
                  <div>
                    <p className="text-xs text-muted-foreground">Notes</p>
                    <p className="text-sm text-foreground mt-0.5">{vocabulary.notes}</p>
                  </div>
                )}
              </div>
            </div>

            <div className="rounded-lg border border-border bg-card p-6 space-y-4">
              <h2 className="text-sm font-semibold tracking-wide uppercase text-muted-foreground">
                Metadata & Learning Status
              </h2>

              <div className="space-y-3 text-sm">
                <div className="flex justify-between py-1 border-b border-border">
                  <span className="text-muted-foreground">Added On</span>
                  <span className="font-mono text-xs">
                    {new Date(vocabulary.createdAt).toLocaleDateString()}
                  </span>
                </div>

                <div className="flex justify-between py-1 border-b border-border">
                  <span className="text-muted-foreground">Last Updated</span>
                  <span className="font-mono text-xs">
                    {new Date(vocabulary.updatedAt).toLocaleDateString()}
                  </span>
                </div>

                <div className="rounded-md border border-dashed border-border p-3 bg-muted/30">
                  <div className="flex items-center gap-2 text-xs font-medium text-foreground">
                    <Sparkles className="h-3.5 w-3.5" />
                    <span>SRS Flashcard Status</span>
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">
                    State: <strong>NEW</strong> · Spaced repetition review queue active.
                  </p>
                </div>
              </div>
            </div>

            {/* Collection Memberships Card */}
            <div className="rounded-lg border border-border bg-card p-6 space-y-4 md:col-span-2">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold tracking-wide uppercase text-muted-foreground flex items-center gap-1.5">
                  <Layers className="h-4 w-4" />
                  Collection Memberships
                </h2>
                <Button
                  variant="outline"
                  size="sm"
                  className="text-xs h-8 px-2.5"
                  onClick={handleOpenAssignModal}
                  id="assign-to-collection-btn"
                >
                  <Plus className="h-3 w-3 mr-1" />
                  Assign to Deck
                </Button>
              </div>

              {assignedCollections.length > 0 ? (
                <div className="flex flex-wrap gap-2 pt-1">
                  {assignedCollections.map((col) => (
                    <Link
                      key={col.id}
                      href={`/collections/${col.id}`}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium border border-border bg-muted hover:border-foreground/50 transition-colors"
                    >
                      <Layers className="h-3 w-3" />
                      <span>{col.title}</span>
                    </Link>
                  ))}
                </div>
              ) : (
                <p className="text-xs text-muted-foreground pt-1">
                  This word is not assigned to any custom collections. Assign it to thematic decks to review it in targeted sessions.
                </p>
              )}
            </div>
          </div>
        </div>
      ) : (
        /* Edit Mode Form */
        <form onSubmit={handleSubmit(onSave)} className="rounded-lg border border-border bg-card p-6 space-y-6">
          <div className="space-y-1.5">
            <Label htmlFor="edit-term">
              Word / Phrase <span className="text-neutral-500">*</span>
            </Label>
            <Input id="edit-term" {...register("term")} disabled={isSaving} />
            {errors.term && (
              <p className="text-xs text-neutral-600 font-medium">{errors.term.message}</p>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="edit-pos">
                Part of Speech <span className="text-neutral-500">*</span>
              </Label>
              <select
                id="edit-pos"
                className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                {...register("partOfSpeech")}
                disabled={isSaving}
              >
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
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="edit-ipa">Phonetic (IPA)</Label>
              <Input id="edit-ipa" {...register("phoneticIpa")} disabled={isSaving} />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="edit-difficulty">CEFR Level</Label>
              <select
                id="edit-difficulty"
                className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                {...register("difficultyLevel")}
                disabled={isSaving}
              >
                <option value="">None</option>
                <option value="A1">A1</option>
                <option value="A2">A2</option>
                <option value="B1">B1</option>
                <option value="B2">B2</option>
                <option value="C1">C1</option>
                <option value="C2">C2</option>
              </select>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="edit-meaning">
              Vietnamese Meaning <span className="text-neutral-500">*</span>
            </Label>
            <Input id="edit-meaning" {...register("vietnameseMeaning")} disabled={isSaving} />
            {errors.vietnameseMeaning && (
              <p className="text-xs text-neutral-600 font-medium">
                {errors.vietnameseMeaning.message}
              </p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="edit-definition">English Definition</Label>
            <Textarea id="edit-definition" rows={2} {...register("englishDefinition")} disabled={isSaving} />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="edit-example">Example Sentence</Label>
              <Textarea id="edit-example" rows={2} {...register("exampleSentence")} disabled={isSaving} />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="edit-example-trans">Example Translation</Label>
              <Textarea
                id="edit-example-trans"
                rows={2}
                {...register("exampleTranslation")}
                disabled={isSaving}
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="edit-notes">Notes</Label>
            <Textarea id="edit-notes" rows={2} {...register("notes")} disabled={isSaving} />
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-border">
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                reset();
                setIsEditing(false);
              }}
              disabled={isSaving}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSaving}>
              {isSaving ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Saving...
                </>
              ) : (
                "Save Changes"
              )}
            </Button>
          </div>
        </form>
      )}

      {/* Delete Confirmation Dialog */}
      <Dialog open={isDeleteDialogOpen} onOpenChange={setIsDeleteDialogOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Delete Vocabulary Word?</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete <strong>&quot;{vocabulary.term}&quot;</strong>? This
              word will be soft-deleted and removed from your active review list. Historical study
              records remain preserved.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="gap-2 sm:gap-0 pt-4">
            <Button
              variant="outline"
              onClick={() => setIsDeleteDialogOpen(false)}
              disabled={isDeleting}
            >
              Cancel
            </Button>
            <Button
              variant="outline"
              className="border-neutral-300 text-neutral-900 hover:bg-neutral-100"
              onClick={onDeleteConfirm}
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

      {/* Assign to Deck Dialog */}
      <Dialog open={isAssignModalOpen} onOpenChange={setIsAssignModalOpen}>
        <DialogContent className="sm:max-w-md">
          <div className="space-y-4">
            <DialogHeader>
              <DialogTitle>Assign to Collection</DialogTitle>
              <DialogDescription>
                Assign <span className="font-semibold text-foreground">&quot;{vocabulary?.term}&quot;</span> to a thematic study deck.
              </DialogDescription>
            </DialogHeader>

            {assignError && (
              <div className="rounded border border-destructive/40 bg-destructive/10 p-2.5 text-xs text-destructive">
                {assignError}
              </div>
            )}

            <div className="space-y-2 py-1">
              <Label htmlFor="select-deck-to-assign">Choose Collection</Label>
              <select
                id="select-deck-to-assign"
                value={selectedDeckToAssign}
                onChange={(e) => setSelectedDeckToAssign(e.target.value)}
                className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              >
                <option value="">-- Select a collection --</option>
                {allCollections.map((col) => {
                  const alreadyIn = assignedCollections.some((c) => c.id === col.id);
                  return (
                    <option key={col.id} value={col.id} disabled={alreadyIn}>
                      {col.title} {alreadyIn ? "(Already added)" : `(${col.wordCount} words)`}
                    </option>
                  );
                })}
              </select>
            </div>

            <DialogFooter className="gap-2 sm:gap-0">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setIsAssignModalOpen(false)}
                disabled={isAssigningDeck}
              >
                Cancel
              </Button>
              <Button
                size="sm"
                onClick={handleAssignToDeck}
                disabled={isAssigningDeck || !selectedDeckToAssign}
                id="confirm-assign-deck-btn"
              >
                {isAssigningDeck ? (
                  <>
                    <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
                    Assigning...
                  </>
                ) : (
                  "Assign to Deck"
                )}
              </Button>
            </DialogFooter>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
