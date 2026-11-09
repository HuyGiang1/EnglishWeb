import { ApiError } from "@/lib/api/client";
import type { TranslationKey } from "@/lib/i18n/dictionaries";

export function authErrorKey(error: unknown): TranslationKey {
  if (!(error instanceof ApiError)) return "authFallback";
  switch (error.code) {
    case "INVALID_CREDENTIALS":
    case "UNAUTHORIZED": return "invalidCredentials";
    case "EMAIL_ALREADY_REGISTERED": return "emailRegistered";
    case "VALIDATION_FAILED": return "validationFailed";
    case "FORBIDDEN": return "forbidden";
    case "NETWORK_ERROR": return "networkError";
    default: return "authFallback";
  }
}
