import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { z } from "zod";

const vocabularySchema = z.object({
  term: z.string().trim().min(1, "Word or phrase is required").max(150),
  partOfSpeech: z.enum([
    "noun",
    "verb",
    "adjective",
    "adverb",
    "idiom",
    "phrase",
    "preposition",
    "conjunction",
    "other",
  ]),
  phoneticIpa: z.string().trim().max(100).optional().or(z.literal("")),
  vietnameseMeaning: z.string().trim().min(1, "Vietnamese meaning is required"),
  englishDefinition: z.string().trim().optional().or(z.literal("")),
  exampleSentence: z.string().trim().optional().or(z.literal("")),
  exampleTranslation: z.string().trim().optional().or(z.literal("")),
  notes: z.string().trim().optional().or(z.literal("")),
  source: z.string().trim().max(100).optional().or(z.literal("")),
  difficultyLevel: z.enum(["A1", "A2", "B1", "B2", "C1", "C2"]).optional().or(z.literal("")),
});

describe("Frontend Vocabulary Schema Validation", () => {
  it("Valid vocabulary payload parses successfully", () => {
    const valid = {
      term: "resilience",
      partOfSpeech: "noun",
      phoneticIpa: "/rɪˈzɪl.jəns/",
      vietnameseMeaning: "khả năng phục hồi",
      englishDefinition: "The capacity to recover quickly from difficulties.",
      exampleSentence: "Her resilience in the face of adversity was remarkable.",
      exampleTranslation: "Khả năng phục hồi của cô ấy thật đáng kinh ngạc.",
      notes: "Academic Word",
      difficultyLevel: "B2",
    };

    const result = vocabularySchema.safeParse(valid);
    assert.equal(result.success, true);
  });

  it("Blank term fails validation", () => {
    const invalid = {
      term: "   ",
      partOfSpeech: "noun",
      vietnameseMeaning: "khả năng phục hồi",
    };

    const result = vocabularySchema.safeParse(invalid);
    assert.equal(result.success, false);
    assert.equal(result.error?.issues[0].path[0], "term");
  });

  it("Invalid part of speech fails validation", () => {
    const invalid = {
      term: "resilience",
      partOfSpeech: "invented_pos",
      vietnameseMeaning: "khả năng phục hồi",
    };

    const result = vocabularySchema.safeParse(invalid);
    assert.equal(result.success, false);
    assert.equal(result.error?.issues[0].path[0], "partOfSpeech");
  });

  it("Blank Vietnamese meaning fails validation", () => {
    const invalid = {
      term: "resilience",
      partOfSpeech: "noun",
      vietnameseMeaning: "",
    };

    const result = vocabularySchema.safeParse(invalid);
    assert.equal(result.success, false);
    assert.equal(result.error?.issues[0].path[0], "vietnameseMeaning");
  });

  it("Invalid CEFR difficulty level fails validation", () => {
    const invalid = {
      term: "resilience",
      partOfSpeech: "noun",
      vietnameseMeaning: "khả năng phục hồi",
      difficultyLevel: "D1",
    };

    const result = vocabularySchema.safeParse(invalid);
    assert.equal(result.success, false);
    assert.equal(result.error?.issues[0].path[0], "difficultyLevel");
  });

  it("All valid CEFR levels (A1..C2) parse cleanly", () => {
    const levels = ["A1", "A2", "B1", "B2", "C1", "C2"];
    for (const level of levels) {
      const data = {
        term: "test",
        partOfSpeech: "verb",
        vietnameseMeaning: "kiểm tra",
        difficultyLevel: level,
      };
      const result = vocabularySchema.safeParse(data);
      assert.equal(result.success, true);
    }
  });
});
