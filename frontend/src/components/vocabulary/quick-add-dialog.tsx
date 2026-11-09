"use client";

import { LanguageSwitcher } from "@/components/auth/language-switcher";
import { useLocale } from "@/lib/i18n/locale-provider";
import { translate, type TranslationKey } from "@/lib/i18n/dictionaries";
import { learningErrorKey } from "@/lib/i18n/learning-errors";

import * as React from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, AlertCircle } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { vocabularyApi, VocabularyItem } from "@/lib/api/vocabulary";
import { collectionsApi, CollectionItem } from "@/lib/api/collections";
import {
  vocabularySchema,
  type VocabularyFormData,
} from "@/lib/vocabulary/vocabulary-schema";

interface QuickAddDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: (item: VocabularyItem) => void;
}

export function QuickAddDialog({
  open,
  onOpenChange,
  onSuccess,
}: QuickAddDialogProps) {
  const { t, locale } = useLocale();
  const [duplicateWarning, setDuplicateWarning] = React.useState<{ term: string; pos: string } | null>(null);
  const [serverError, setServerError] = React.useState<TranslationKey | null>(null);
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const [collections, setCollections] = React.useState<CollectionItem[]>([]);
  const [selectedCollectionId, setSelectedCollectionId] = React.useState<string>("");

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<VocabularyFormData>({
    resolver: zodResolver(vocabularySchema),
    defaultValues: {
      term: "",
      partOfSpeech: "noun",
      phoneticIpa: "",
      vietnameseMeaning: "",
      englishDefinition: "",
      exampleSentence: "",
      exampleTranslation: "",
      notes: "",
      difficultyLevel: "",
    },
  });

  const watchedTerm = watch("term");
  const watchedPos = watch("partOfSpeech");

  // Advisory duplicate check on debounced term changes
  React.useEffect(() => {
    if (!watchedTerm || watchedTerm.trim().length < 2) {
      setDuplicateWarning(null);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        const res = await vocabularyApi.checkDuplicates(
          watchedTerm.trim(),
          watchedPos || undefined
        );
        if (res.exists && res.existingEntries.length > 0) {
          const first = res.existingEntries[0];
          setDuplicateWarning(
            { term: first.term, pos: first.partOfSpeech }
          );
        } else {
          setDuplicateWarning(null);
        }
      } catch {
        // Silently ignore advisory check failures
      }
    }, 400);

    return () => clearTimeout(timer);
  }, [watchedTerm, watchedPos]);

  // Reset form and fetch user collections when dialog opens/closes
  React.useEffect(() => {
    if (open) {
      setServerError(null);
      setDuplicateWarning(null);
      setSelectedCollectionId("");
      collectionsApi
        .list({ size: 100 })
        .then((res) => setCollections(res.content))
        .catch(() => setCollections([]));
    } else {
      reset();
      setSelectedCollectionId("");
    }
  }, [open, reset]);

  // Handle Mod+Enter shortcut to submit form
  const handleKeyDown = (e: React.KeyboardEvent<HTMLFormElement>) => {
    if ((e.metaKey || e.ctrlKey) && e.key === "Enter") {
      e.preventDefault();
      handleSubmit(onSubmit)();
    }
  };

  const onSubmit = async (data: VocabularyFormData) => {
    setServerError(null);
    setIsSubmitting(true);

    try {
      const created = await vocabularyApi.create({
        term: data.term.trim(),
        partOfSpeech: data.partOfSpeech,
        phoneticIpa: data.phoneticIpa?.trim() || null,
        vietnameseMeaning: data.vietnameseMeaning.trim(),
        englishDefinition: data.englishDefinition?.trim() || null,
        exampleSentence: data.exampleSentence?.trim() || null,
        exampleTranslation: data.exampleTranslation?.trim() || null,
        notes: data.notes?.trim() || null,
        difficultyLevel: data.difficultyLevel || null,
        collectionId: selectedCollectionId || null,
      });

      reset();
      setSelectedCollectionId("");
      onOpenChange(false);
      if (onSuccess) {
        onSuccess(created);
      }
    } catch (err: unknown) {
      const errorMsg = learningErrorKey(err, "learnFailedToCreateVocabulary");
      setServerError(errorMsg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="learning-module max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
            <LanguageSwitcher />
          <DialogTitle>{t("learnQuickAddVocabulary")}</DialogTitle>
          <DialogDescription>{t("learnAddANewWordOrPhraseToYourLibraryPress")}{" "}
            <kbd className="rounded border border-border bg-muted px-1.5 py-0.5 text-xs font-mono font-medium text-foreground">
              Mod+Enter
            </kbd>{" "}{t("learnToSave")}</DialogDescription>
        </DialogHeader>

        {serverError && (
          <div
            role="alert"
            className="flex items-center gap-2 rounded-md border border-neutral-300 bg-neutral-100 p-3 text-sm text-neutral-900"
          >
            <AlertCircle className="h-4 w-4 shrink-0 text-neutral-700" />
            <span>{t(serverError)}</span>
          </div>
        )}

        {duplicateWarning && (
          <div
            role="status"
            className="flex items-center gap-2 rounded-md border border-neutral-300 bg-neutral-50 p-2.5 text-xs text-neutral-800"
          >
            <AlertCircle className="h-3.5 w-3.5 shrink-0 text-neutral-600" />
            <span>{t("learnAdvisory")}{" "}{t("learnDuplicateWarning", duplicateWarning)}</span>
          </div>
        )}

        <form noValidate onSubmit={handleSubmit(onSubmit)} onKeyDown={handleKeyDown} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="quick-add-term">{t("learnWordPhrase")}{" "}<span className="text-neutral-500">*</span>
            </Label>
            <Input
              id="quick-add-term" aria-invalid={!!errors.term} aria-describedby={errors.term ? "quick-add-term-error" : undefined}
              autoFocus
              placeholder="e.g. resilience"
              {...register("term")}
              disabled={isSubmitting}
            />
            {errors.term && (
              <p id="quick-add-term-error" role="alert" className="text-xs text-neutral-600 font-medium">{translate(locale, errors.term.message ?? "validationFailed")}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="quick-add-meaning">{t("learnVietnameseMeaning")}{" "}<span className="text-neutral-500">*</span>
            </Label>
            <Input
              id="quick-add-meaning" aria-invalid={!!errors.vietnameseMeaning} aria-describedby={errors.vietnameseMeaning ? "quick-add-meaning-error" : undefined}
              placeholder="khả năng phục hồi, sự kiên cường"
              {...register("vietnameseMeaning")}
              disabled={isSubmitting}
            />
            {errors.vietnameseMeaning && (
              <p id="quick-add-meaning-error" role="alert" className="text-xs text-neutral-600 font-medium">{translate(locale, errors.vietnameseMeaning.message ?? "validationFailed")}</p>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="quick-add-pos">{t("learnPartOfSpeech")}{" "}<span className="text-neutral-500">*</span>
              </Label>
              <select
                id="quick-add-pos" aria-invalid={!!errors.partOfSpeech} aria-describedby={errors.partOfSpeech ? "quick-add-pos-error" : undefined}
                className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                {...register("partOfSpeech")}
                disabled={isSubmitting}
              >
                <option value="noun">{t("learnNoun")}</option>
                <option value="verb">{t("learnVerb")}</option>
                <option value="adjective">{t("learnAdjective")}</option>
                <option value="adverb">{t("learnAdverb")}</option>
                <option value="idiom">{t("learnIdiom")}</option>
                <option value="phrase">{t("learnPhrase")}</option>
                <option value="preposition">{t("learnPreposition")}</option>
                <option value="conjunction">{t("learnConjunction")}</option>
                <option value="other">{t("learnOther")}</option>
              </select>
              {errors.partOfSpeech && (
                <p id="quick-add-pos-error" role="alert" className="text-xs text-neutral-600 font-medium">{translate(locale, errors.partOfSpeech.message ?? "validationFailed")}</p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="quick-add-ipa">{t("learnPhoneticIPA")}</Label>
              <Input
                id="quick-add-ipa" aria-invalid={!!errors.phoneticIpa} aria-describedby={errors.phoneticIpa ? "quick-add-ipa-error" : undefined}
                placeholder="/rɪˈzɪl.jəns/"
                {...register("phoneticIpa")}
                disabled={isSubmitting}
              />
              {errors.phoneticIpa && (
                <p id="quick-add-ipa-error" role="alert" className="text-xs text-neutral-600 font-medium">{translate(locale, errors.phoneticIpa.message ?? "validationFailed")}</p>
              )}
            </div>
          </div>

          <details className="space-y-4">
            <summary className="cursor-pointer rounded text-sm font-medium focus-visible:outline-2">{t("learnOptionalDetails")}</summary>
          <div className="space-y-1.5">
            <Label htmlFor="quick-add-definition">{t("learnEnglishDefinition")}</Label>
            <Textarea
              id="quick-add-definition"
              rows={2}
              placeholder="The capacity to recover quickly from difficulties."
              {...register("englishDefinition")}
              disabled={isSubmitting}
            />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="quick-add-example">{t("learnExampleSentence")}</Label>
              <Textarea
                id="quick-add-example"
                rows={2}
                placeholder="Her resilience in the face of adversity was remarkable."
                {...register("exampleSentence")}
                disabled={isSubmitting}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="quick-add-difficulty">{t("learnCEFRLevel")}</Label>
              <select
                id="quick-add-difficulty"
                className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                {...register("difficultyLevel")}
                disabled={isSubmitting}
              >
                <option value="">{t("learnNoneNotSet")}</option>
                <option value="A1">{t("learnA1Beginner")}</option>
                <option value="A2">{t("learnA2Elementary")}</option>
                <option value="B1">{t("learnB1Intermediate")}</option>
                <option value="B2">{t("learnB2UpperIntermediate")}</option>
                <option value="C1">{t("learnC1Advanced")}</option>
                <option value="C2">{t("learnC2Proficiency")}</option>
              </select>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="quick-add-collection">{t("learnAssignToCollectionOptional")}</Label>
            <select
              id="quick-add-collection"
              value={selectedCollectionId}
              onChange={(e) => setSelectedCollectionId(e.target.value)}
              className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
              disabled={isSubmitting}
            >
              <option value="">{t("learnNoneLibraryOnly")}</option>
              {collections.map((col) => (
                <option key={col.id} value={col.id}>
                  {col.title} ({col.wordCount} {col.wordCount === 1 ? t("learnWord") : t("learnWords")})
                </option>
              ))}
            </select>
          </div>

          </details>

          <DialogFooter className="gap-2 sm:gap-0 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isSubmitting}
            >{t("learnCancel")}</Button>
            <Button type="submit" disabled={isSubmitting} className="min-w-28">
              {isSubmitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />{t("learnSaving")}</>
              ) : (
                t("learnSaveWord")
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
