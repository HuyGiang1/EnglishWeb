/**
 * Learner Profile & Settings API Client.
 * Single Source of Truth: docs/api/API_CONTRACT.md Section 10 & docs/api/API_COVERAGE.md Screen 13.
 */

import { apiRequest } from "./client";

export interface UserSettings {
  id: string;
  email: string;
  displayName: string;
  timezone: string;
  dailyGoalItems: number;
  preferredAudioSpeed: number;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateProfilePayload {
  displayName: string;
}

export interface UpdatePreferencesPayload {
  timezone?: string;
  dailyGoalItems?: number;
  preferredAudioSpeed?: number;
}

export interface UpdateSettingsPayload {
  displayName?: string;
  timezone?: string;
  dailyGoalItems?: number;
  preferredAudioSpeed?: number;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
  confirmPassword?: string;
}

export interface DeleteAccountPayload {
  password: string;
}

export interface SettingsActionResponse {
  status: string;
  message: string;
}

export const settingsApi = {
  getSettings(): Promise<UserSettings> {
    return apiRequest<UserSettings>("/settings");
  },

  updateSettings(data: UpdateSettingsPayload): Promise<UserSettings> {
    return apiRequest<UserSettings>("/settings", {
      method: "PUT",
      body: data,
    });
  },

  updateProfile(data: UpdateProfilePayload): Promise<UserSettings> {
    return apiRequest<UserSettings>("/settings/profile", {
      method: "PUT",
      body: data,
    });
  },

  updatePreferences(data: UpdatePreferencesPayload): Promise<UserSettings> {
    return apiRequest<UserSettings>("/settings/preferences", {
      method: "PUT",
      body: data,
    });
  },

  changePassword(data: ChangePasswordPayload): Promise<SettingsActionResponse> {
    return apiRequest<SettingsActionResponse>("/settings/password", {
      method: "PUT",
      body: data,
    });
  },

  deleteAccount(data: DeleteAccountPayload): Promise<SettingsActionResponse> {
    return apiRequest<SettingsActionResponse>("/settings/account", {
      method: "DELETE",
      body: data,
    });
  },
};
