/**
 * Standard RFC 7807 / EnglishFlow API Contract types.
 * Single Source of Truth: docs/api/API_CONTRACT.md
 */

export interface ApiValidationError {
  field: string;
  rejectedValue?: unknown;
  message: string;
}

export interface ApiErrorResponse {
  code: string;
  message: string;
  status: number;
  correlationId?: string;
  timestamp?: string;
  validationErrors?: ApiValidationError[];
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  params?: Record<string, string | number | boolean | undefined | null>;
  body?: unknown;
}
