import { z } from "zod";

export const vocabularySchema = z.object({
  term: z
    .string()
    .trim()
    .min(1, "Word or phrase is required")
    .max(150, "Must be at most 150 characters"),
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
      errorMap: () => ({ message: "Please select a valid part of speech" }),
    }
  ),
  phoneticIpa: z
    .string()
    .trim()
    .max(100, "Phonetic IPA must not exceed 100 characters")
    .optional()
    .or(z.literal("")),
  vietnameseMeaning: z
    .string()
    .trim()
    .min(1, "Vietnamese meaning is required"),
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
