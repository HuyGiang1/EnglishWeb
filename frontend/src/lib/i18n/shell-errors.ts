import { ApiError } from "@/lib/api/client";
import type { TranslationKey } from "./dictionaries";

export function shellErrorKey(error: unknown, fallback: TranslationKey = "requestFailed"): TranslationKey {
  if (!(error instanceof ApiError)) return fallback;
  switch (error.code) {
    case "UNAUTHORIZED": return "sessionExpired";
    case "FORBIDDEN": return "forbidden";
    case "NETWORK_ERROR": return "networkError";
    case "VALIDATION_FAILED": return "validationFailed";
    case "INVALID_CURRENT_PASSWORD": return "incorrectPassword";
    default: return fallback;
  }
}
