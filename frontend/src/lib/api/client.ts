/**
 * EnglishFlow Typed HTTP API Client Foundation.
 * Single Source of Truth: docs/api/API_CONTRACT.md and AGENTS.md
 *
 * Characteristics:
 * - Uses relative /api/v1 base path routed through Next.js same-origin proxy
 * - credentials: 'same-origin' (ensures Spring Security JSESSIONID cookies are passed)
 * - Automatic XSRF-TOKEN cookie extraction for mutating requests
 * - Standard ApiError envelope conforming to RFC 7807 contract
 * - Zero stored tokens (no localStorage/sessionStorage credentials)
 */

import { ApiErrorResponse, ApiValidationError, RequestOptions } from "./types";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly correlationId?: string;
  readonly validationErrors?: ApiValidationError[];
  readonly rawResponse?: ApiErrorResponse;

  constructor(
    status: number,
    message: string,
    code: string = "UNKNOWN_ERROR",
    payload?: ApiErrorResponse
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.correlationId = payload?.correlationId;
    this.validationErrors = payload?.validationErrors;
    this.rawResponse = payload;
  }
}

function getXsrfTokenFromCookie(): string | null {
  if (typeof document === "undefined") {
    return null;
  }
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

const BASE_API_PATH = "/api/v1";

export async function apiRequest<T>(
  endpoint: string,
  options: RequestOptions = {}
): Promise<T> {
  const { params, body, headers = {}, method = "GET", ...rest } = options;

  let url = endpoint.startsWith("/api/v1")
    ? endpoint
    : `${BASE_API_PATH}${endpoint.startsWith("/") ? endpoint : `/${endpoint}`}`;

  if (params) {
    const searchParams = new URLSearchParams();
    for (const [key, val] of Object.entries(params)) {
      if (val !== undefined && val !== null) {
        searchParams.append(key, String(val));
      }
    }
    const queryString = searchParams.toString();
    if (queryString) {
      url += (url.includes("?") ? "&" : "?") + queryString;
    }
  }

  const requestHeaders = new Headers(headers);

  // Set JSON Content-Type if body is a standard plain object or array
  let requestBody: BodyInit | null = null;
  if (body !== undefined && body !== null) {
    if (
      typeof body === "string" ||
      body instanceof FormData ||
      body instanceof Blob
    ) {
      requestBody = body;
    } else {
      if (!requestHeaders.has("Content-Type")) {
        requestHeaders.set("Content-Type", "application/json; charset=UTF-8");
      }
      requestBody = JSON.stringify(body);
    }
  }

  // Attach synchronized CSRF token for mutating HTTP verbs if available in browser
  const isMutating = ["POST", "PUT", "PATCH", "DELETE"].includes(
    method.toUpperCase()
  );
  if (isMutating && !requestHeaders.has("X-XSRF-TOKEN")) {
    const xsrfToken = getXsrfTokenFromCookie();
    if (xsrfToken) {
      requestHeaders.set("X-XSRF-TOKEN", xsrfToken);
    }
  }

  let response: Response;
  try {
    response = await fetch(url, {
      ...rest,
      method,
      headers: requestHeaders,
      credentials: "same-origin",
      body: requestBody,
    });
  } catch (error) {
    throw new ApiError(
      0,
      error instanceof Error ? error.message : "Network error occurred",
      "NETWORK_ERROR"
    );
  }

  if (!response.ok) {
    let errorPayload: ApiErrorResponse | undefined;
    try {
      errorPayload = (await response.json()) as ApiErrorResponse;
    } catch {
      // Non-JSON error response fallback
    }

    const status = response.status;
    const defaultMessage =
      status === 401
        ? "Authentication session expired or missing."
        : status === 403
        ? "Access forbidden or CSRF validation failed."
        : status === 404
        ? "Requested resource was not found."
        : status === 409
        ? "Conflict occurred during resource modification."
        : `Request failed with status ${status}`;

    throw new ApiError(
      status,
      errorPayload?.message || defaultMessage,
      errorPayload?.code || (status === 401 ? "UNAUTHORIZED" : status === 403 ? "FORBIDDEN" : "API_ERROR"),
      errorPayload
    );
  }

  if (response.status === 204) {
    return undefined as unknown as T;
  }

  return (await response.json()) as T;
}

export const apiClient = {
  get: <T>(endpoint: string, options?: RequestOptions) =>
    apiRequest<T>(endpoint, { ...options, method: "GET" }),

  post: <T>(endpoint: string, body?: unknown, options?: RequestOptions) =>
    apiRequest<T>(endpoint, { ...options, method: "POST", body }),

  put: <T>(endpoint: string, body?: unknown, options?: RequestOptions) =>
    apiRequest<T>(endpoint, { ...options, method: "PUT", body }),

  patch: <T>(endpoint: string, body?: unknown, options?: RequestOptions) =>
    apiRequest<T>(endpoint, { ...options, method: "PATCH", body }),

  delete: <T>(endpoint: string, options?: RequestOptions) =>
    apiRequest<T>(endpoint, { ...options, method: "DELETE" }),
};
