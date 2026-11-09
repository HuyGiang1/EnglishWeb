import { apiClient } from "./client";
import type { Locale } from "@/lib/i18n/config";

export interface User {
  id: string;
  email: string;
  displayName: string;
  timezone: string;
  dailyGoalItems: number;
  preferredLocale?: Locale | null;
  createdAt?: string;
}

export interface RegisterPayload {
  email: string;
  password: string;
  displayName: string;
  timezone?: string;
  preferredLocale?: Locale;
}

export interface LoginPayload {
  email: string;
  password: string;
}

function hasCsrfCookie(): boolean {
  if (typeof document === "undefined") return false;
  return /(?:^|;\s*)XSRF-TOKEN=/.test(document.cookie);
}

export const authApi = {
  /**
   * Initializes or refreshes the XSRF-TOKEN cookie from Spring Security.
   */
  async ensureCsrfToken(): Promise<void> {
    if (!hasCsrfCookie()) {
      await apiClient.get<void>("/auth/csrf");
    }
  },

  async forceCsrfToken(): Promise<void> {
    await apiClient.get<void>("/auth/csrf");
  },

  async register(payload: RegisterPayload): Promise<User> {
    await this.ensureCsrfToken();
    return apiClient.post<User>("/auth/register", {
      ...payload,
      timezone: payload.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
    });
  },

  async login(payload: LoginPayload): Promise<User> {
    await this.ensureCsrfToken();
    return apiClient.post<User>("/auth/login", payload);
  },

  async getMe(): Promise<User> {
    return apiClient.get<User>("/auth/me");
  },

  async logout(): Promise<void> {
    await this.ensureCsrfToken();
    await apiClient.post<void>("/auth/logout");
  },
};
