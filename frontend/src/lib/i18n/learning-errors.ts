import { ApiError } from "@/lib/api/client";
import type { TranslationKey } from "./dictionaries";
import { shellErrorKey } from "./shell-errors";

// Preserve machine codes; never render backend messages or rejected values.
export function learningErrorKey(error: unknown, fallback: TranslationKey = "requestFailed"): TranslationKey {
  if (error instanceof ApiError && error.code === "TITLE_ALREADY_EXISTS") {
    return "learnACollectionWithThisTitleAlreadyExists";
  }
  return shellErrorKey(error, fallback);
}
