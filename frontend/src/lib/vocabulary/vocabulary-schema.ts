import { z } from "zod";

export const vocabularySchema = z.object({
  term: z
    .string()
    .trim()
    .min(1, "learnWordOrPhraseIsRequired")
    .max(150, "learnMustBeAtMost150Characters"),
  partOfSpeech: z.enum(
    [
      "noun",
      "verb",
      "adjective",
      "adverb",
      "idiom",
      "phrase",
      "preposition",
      "conjunction",
      "other",
    ],
    {
      errorMap: () => ({ message: "learnPleaseSelectAValidPartOfSpeech" }),
    }
  ),
  phoneticIpa: z
    .string()
    .trim()
    .max(100, "learnPhoneticIPAMustNotExceed100Characters")
    .optional()
    .or(z.literal("")),
  vietnameseMeaning: z
    .string()
    .trim()
    .min(1, "learnVietnameseMeaningIsRequired"),
  englishDefinition: z.string().trim().optional().or(z.literal("")),
  exampleSentence: z.string().trim().optional().or(z.literal("")),
  exampleTranslation: z.string().trim().optional().or(z.literal("")),
  notes: z.string().trim().optional().or(z.literal("")),
  source: z.string().trim().max(100).optional().or(z.literal("")),
  difficultyLevel: z
    .enum(["A1", "A2", "B1", "B2", "C1", "C2"])
    .optional()
    .or(z.literal("")),
});

export type VocabularyFormData = z.infer<typeof vocabularySchema>;
