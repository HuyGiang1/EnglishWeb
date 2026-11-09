"use client";

import { useEffect, useState, useCallback, useId, useRef } from "react";
import { useRouter } from "next/navigation";
import {
  User,
  Sliders,
  Shield,
  Trash2,
  CheckCircle2,
  AlertCircle,
  Clock,
  Volume2,
  Target,
  Eye,
  EyeOff,
  Search,
  Loader2,
} from "lucide-react";
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { settingsApi, type UserSettings } from "@/lib/api/settings";
import { useLocale } from "@/lib/i18n/locale-provider";
import { useAuth } from "@/lib/auth/auth-context";
import type { TranslationKey } from "@/lib/i18n/dictionaries";
import { shellErrorKey } from "@/lib/i18n/shell-errors";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { LanguageSwitcher } from "@/components/auth/language-switcher";

// Standard canonical IANA timezones curated for selection
const POPULAR_TIMEZONES = [
  "Asia/Ho_Chi_Minh",
  "Asia/Bangkok",
  "Asia/Tokyo",
  "Asia/Seoul",
  "Asia/Singapore",
  "Asia/Jakarta",
  "Europe/London",
  "Europe/Paris",
  "Europe/Berlin",
  "America/New_York",
  "America/Chicago",
  "America/Denver",
  "America/Los_Angeles",
  "Australia/Sydney",
  "UTC",
];

const AUDIO_SPEEDS = [0.75, 1.0, 1.25] as const;

export default function SettingsPage() {
  const router = useRouter();
  const { locale, t } = useLocale();
  const { refreshSession } = useAuth();
  const deleteTriggerRef = useRef<HTMLButtonElement>(null);

  // Active Tab state: general | preferences | security | account
  const [activeTab, setActiveTab] = useState<"general" | "preferences" | "security" | "account">("general");

  const tabNames = ["general", "preferences", "security", "account"] as const;
  function navigateTab(event: React.KeyboardEvent<HTMLButtonElement>) {
    const current = tabNames.indexOf(activeTab);
    const index = event.key === "Home" ? 0 : event.key === "End" ? tabNames.length - 1
      : event.key === "ArrowRight" ? (current + 1) % tabNames.length
      : event.key === "ArrowLeft" ? (current + tabNames.length - 1) % tabNames.length : -1;
    if (index < 0) return;
    event.preventDefault();
    setActiveTab(tabNames[index]);
    document.getElementById(`settings-tab-${tabNames[index]}`)?.focus();
  }

  // Initial loading & core settings
  const [loading, setLoading] = useState<boolean>(true);
  const [loadError, setLoadError] = useState<TranslationKey | null>(null);
  const [settings, setSettings] = useState<UserSettings | null>(null);

  // Profile Form state
  const [displayName, setDisplayName] = useState<string>("");
  const [savingProfile, setSavingProfile] = useState<boolean>(false);
  const [profileSuccess, setProfileSuccess] = useState<TranslationKey | null>(null);
  const [profileError, setProfileError] = useState<TranslationKey | null>(null);

  // Preferences Form state
  const [dailyGoal, setDailyGoal] = useState<number>(20);
  const [selectedTimezone, setSelectedTimezone] = useState<string>("UTC");
  const [timezoneSearch, setTimezoneSearch] = useState<string>("");
  const [audioSpeed, setAudioSpeed] = useState<number>(1.0);
  const [savingPreferences, setSavingPreferences] = useState<boolean>(false);
  const [preferencesSuccess, setPreferencesSuccess] = useState<TranslationKey | null>(null);
  const [preferencesError, setPreferencesError] = useState<TranslationKey | null>(null);

  // Security Form state
  const [currentPassword, setCurrentPassword] = useState<string>("");
  const [newPassword, setNewPassword] = useState<string>("");
  const [confirmPassword, setConfirmPassword] = useState<string>("");
  const [showCurrentPass, setShowCurrentPass] = useState<boolean>(false);
  const [showNewPass, setShowNewPass] = useState<boolean>(false);
  const [savingPassword, setSavingPassword] = useState<boolean>(false);
  const [passwordSuccess, setPasswordSuccess] = useState<TranslationKey | null>(null);
  const [passwordError, setPasswordError] = useState<TranslationKey | null>(null);

  // Danger Zone / Account Deletion Modal
  const [showDeleteModal, setShowDeleteModal] = useState<boolean>(false);
  const [deletePassword, setDeletePassword] = useState<string>("");
  const [deletingAccount, setDeletingAccount] = useState<boolean>(false);
  const [deleteError, setDeleteError] = useState<TranslationKey | null>(null);

  // Accessible IDs
  const displayNameId = useId();
  const emailId = useId();
  const dailyGoalId = useId();
  const timezoneSearchId = useId();
  const currentPassId = useId();
  const newPassId = useId();
  const confirmPassId = useId();
  const deletePassId = useId();

  // Load user settings on mount
  const loadSettings = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const data = await settingsApi.getSettings();
      setSettings(data);
      setDisplayName(data.displayName);
      setDailyGoal(data.dailyGoalItems);
      setSelectedTimezone(data.timezone);
      setAudioSpeed(data.preferredAudioSpeed || 1.0);
    } catch (err: unknown) {
      setLoadError(shellErrorKey(err, "settingsLoadFailed"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSettings();
  }, [loadSettings]);

  // Handle Profile Update
  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setProfileError(null);
    setProfileSuccess(null);

    const trimmed = displayName.trim();
    if (trimmed.length < 2 || trimmed.length > 100) {
      setProfileError("profileNameInvalid");
      return;
    }

    setSavingProfile(true);
    try {
      const updated = await settingsApi.updateProfile({ displayName: trimmed });
      setSettings(updated);
      setDisplayName(updated.displayName);
      setProfileSuccess("profileSaved");
    } catch (err: unknown) {
      setProfileError(shellErrorKey(err, "profileSaveFailed"));
    } finally {
      setSavingProfile(false);
    }
  };

  // Handle Preferences Update
  const handleSavePreferences = async (e: React.FormEvent) => {
    e.preventDefault();
    setPreferencesError(null);
    setPreferencesSuccess(null);

    if (dailyGoal < 1 || dailyGoal > 500) {
      setPreferencesError("dailyGoalInvalid");
      return;
    }

    setSavingPreferences(true);
    try {
      const updated = await settingsApi.updatePreferences({
        timezone: selectedTimezone,
        dailyGoalItems: dailyGoal,
        preferredAudioSpeed: audioSpeed,
      });
      setSettings(updated);
      setPreferencesSuccess("preferencesSaved");
    } catch (err: unknown) {
      setPreferencesError(shellErrorKey(err, "preferencesSaveFailed"));
    } finally {
      setSavingPreferences(false);
    }
  };

  // Handle Password Change
  const handleSavePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordError(null);
    setPasswordSuccess(null);

    if (!currentPassword) {
      setPasswordError("currentPasswordRequired");
      return;
    }

    if (newPassword.length < 8 || newPassword.length > 100) {
      setPasswordError("newPasswordLength");
      return;
    }

    const hasNumber = /[0-9]/.test(newPassword);
    const hasSpecial = /[^a-zA-Z0-9]/.test(newPassword);
    if (!hasNumber || !hasSpecial) {
      setPasswordError("newPasswordStrength");
      return;
    }

    if (newPassword !== confirmPassword) {
      setPasswordError("newPasswordMismatch");
      return;
    }

    if (newPassword === currentPassword) {
      setPasswordError("newPasswordSame");
      return;
    }

    setSavingPassword(true);
    try {
      await settingsApi.changePassword({
        currentPassword,
        newPassword,
        confirmPassword,
      });
      setPasswordSuccess("passwordChanged");
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err: unknown) {
      setPasswordError(shellErrorKey(err, "passwordChangeFailed"));
    } finally {
      setSavingPassword(false);
    }
  };

  // Handle Account Deletion
  const handleDeleteAccount = async () => {
    setDeleteError(null);
    if (!deletePassword) {
      setDeleteError("currentPasswordRequired");
      return;
    }

    setDeletingAccount(true);
    try {
      await settingsApi.deleteAccount({ password: deletePassword });
      await refreshSession();
      router.replace("/login");
    } catch (err: unknown) {
      setDeleteError(shellErrorKey(err, "deleteFailed"));
      setDeletingAccount(false);
    }
  };

  // Filtered Timezones
  const filteredTimezones = POPULAR_TIMEZONES.filter((tz) =>
    tz.toLowerCase().includes(timezoneSearch.toLowerCase().trim())
  );

  return (
    <div className="max-w-4xl mx-auto space-y-8 pb-16">
      {/* Page Header */}
      <div>
        <h1 className="text-3xl font-bold tracking-tight text-[#0A0A0A]">{t("navSettings")}</h1>
        <p className="text-sm text-[#737373] mt-1">
          {t("settingsDescription")}
        </p>
      </div>

      <LanguageSwitcher />

      {/* Loading Skeleton */}
      {loading ? (
        <div role="status" className="space-y-6" data-testid="settings-skeleton"><span className="sr-only">{t("settingsLoading")}</span>
          <div className="flex gap-2 border-b border-[#E5E5E5] pb-2">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="h-9 w-28 bg-[#F0F0F0] rounded animate-pulse" />
            ))}
          </div>
          <Card className="border-[#E5E5E5]">
            <CardHeader className="space-y-2">
              <div className="h-6 w-48 bg-[#F0F0F0] rounded animate-pulse" />
              <div className="h-4 w-72 max-w-full bg-[#F0F0F0] rounded animate-pulse" />
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="h-10 w-full bg-[#F0F0F0] rounded animate-pulse" />
              <div className="h-10 w-full bg-[#F0F0F0] rounded animate-pulse" />
            </CardContent>
          </Card>
        </div>
      ) : loadError ? (
        <Card role="alert" className="border-border bg-muted text-foreground">
          <CardContent className="p-6 flex items-start gap-3">
            <AlertCircle className="h-5 w-5 text-foreground shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold text-sm">{t("settingsLoadFailed")}</p>
              <p className="text-sm mt-1 text-foreground">{t(loadError)}</p>
              <Button
                variant="outline"
                size="sm"
                className="mt-4 border-border text-foreground hover:bg-muted whitespace-normal h-auto min-h-9 py-2 max-w-full"
                onClick={loadSettings}
              >
                {t("retry")}
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : (
        <>
          {/* Settings Tabs Bar */}
          <div
            className="flex border-b border-[#E5E5E5] overflow-x-auto gap-1"
            role="tablist"
            aria-label={t("settingsTabs")}
          >
            <button
              role="tab"
              aria-selected={activeTab === "general"}
              id="settings-tab-general"
              aria-controls="settings-panel-general"
              tabIndex={activeTab === "general" ? 0 : -1}
              onKeyDown={navigateTab}
              data-testid="tab-general"
              onClick={() => setActiveTab("general")}
              className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                activeTab === "general"
                  ? "border-[#0A0A0A] text-[#0A0A0A]"
                  : "border-transparent text-[#737373] hover:text-[#0A0A0A]"
              }`}
            >
              <User className="h-4 w-4" />
              {t("settingsProfile")}
            </button>

            <button
              role="tab"
              aria-selected={activeTab === "preferences"}
              id="settings-tab-preferences"
              aria-controls="settings-panel-preferences"
              tabIndex={activeTab === "preferences" ? 0 : -1}
              onKeyDown={navigateTab}
              data-testid="tab-preferences"
              onClick={() => setActiveTab("preferences")}
              className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                activeTab === "preferences"
                  ? "border-[#0A0A0A] text-[#0A0A0A]"
                  : "border-transparent text-[#737373] hover:text-[#0A0A0A]"
              }`}
            >
              <Sliders className="h-4 w-4" />
              {t("settingsPreferences")}
            </button>

            <button
              role="tab"
              aria-selected={activeTab === "security"}
              id="settings-tab-security"
              aria-controls="settings-panel-security"
              tabIndex={activeTab === "security" ? 0 : -1}
              onKeyDown={navigateTab}
              data-testid="tab-security"
              onClick={() => setActiveTab("security")}
              className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                activeTab === "security"
                  ? "border-[#0A0A0A] text-[#0A0A0A]"
                  : "border-transparent text-[#737373] hover:text-[#0A0A0A]"
              }`}
            >
              <Shield className="h-4 w-4" />
              {t("settingsSecurity")}
            </button>

            <button
              role="tab"
              aria-selected={activeTab === "account"}
              id="settings-tab-account"
              aria-controls="settings-panel-account"
              tabIndex={activeTab === "account" ? 0 : -1}
              onKeyDown={navigateTab}
              data-testid="tab-account"
              onClick={() => setActiveTab("account")}
              className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                activeTab === "account"
                  ? "border-[#0A0A0A] text-[#0A0A0A]"
                  : "border-transparent text-[#737373] hover:text-[#0A0A0A]"
              }`}
            >
              <Trash2 className="h-4 w-4" />
              {t("settingsAccount")}
            </button>
          </div>

          <section role="tabpanel" id={`settings-panel-${activeTab}`} aria-labelledby={`settings-tab-${activeTab}`} tabIndex={-1}>
          {/* TAB 1: General & Profile */}
          {activeTab === "general" && (
            <Card className="border-[#E5E5E5] shadow-sm">
              <form noValidate onSubmit={handleSaveProfile} data-testid="profile-form">
                <CardHeader>
                  <CardTitle className="text-lg"><h2 className="flex items-center gap-2">{t("settingsProfileTitle")}</h2></CardTitle>
                  <CardDescription>
                    {t("settingsProfileDescription")}
                  </CardDescription>
                </CardHeader>

                <CardContent className="space-y-6">
                  {profileSuccess && (
                    <div
                      className="p-3 bg-muted border border-border rounded-md flex items-center gap-2 text-sm text-foreground"
                      role="status" data-testid="profile-success-alert"
                    >
                      <CheckCircle2 className="h-4 w-4 text-foreground shrink-0" />
                      <span>{t(profileSuccess)}</span>
                    </div>
                  )}

                  {profileError && (
                    <div
                      className="p-3 bg-muted border border-border rounded-md flex items-center gap-2 text-sm text-foreground"
                      role="alert"
                      id="settings-profile-error"
                      data-testid="profile-error-alert"
                    >
                      <AlertCircle className="h-4 w-4 text-foreground shrink-0" />
                      <span>{t(profileError)}</span>
                    </div>
                  )}

                  {/* Display Name */}
                  <div className="space-y-2">
                    <div className="flex justify-between items-center">
                      <Label htmlFor={displayNameId} className="text-sm font-medium">
                        {t("settingsDisplayName")}
                      </Label>
                      <span className="text-xs text-[#737373]">{displayName.trim().length} / 100</span>
                    </div>
                    <Input
                      id={displayNameId}
                      data-testid="input-display-name"
                        aria-describedby={profileError ? "settings-profile-error" : undefined}
                        aria-invalid={!!profileError}
                      type="text"
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      placeholder={t("settingsNamePlaceholder")}
                      maxLength={100}
                      disabled={savingProfile}
                      className="max-w-md border-[#E5E5E5]"
                    />
                    <p className="text-xs text-[#737373]">
                      {t("settingsNameHint")}
                    </p>
                  </div>

                  {/* Account Email (Read-Only) */}
                  <div className="space-y-2">
                    <div className="flex items-center gap-2">
                      <Label htmlFor={emailId} className="text-sm font-medium">
                        {t("settingsEmail")}
                      </Label>
                      <Badge variant="outline" className="text-xs font-normal border-[#E5E5E5] text-[#737373]">
                        {t("readOnly")}
                      </Badge>
                    </div>
                    <Input
                      id={emailId}
                      type="email"
                      value={settings?.email || ""}
                      readOnly
                      disabled
                      className="max-w-md bg-[#F9F9F9] border-[#E5E5E5] text-[#737373] cursor-not-allowed"
                    />
                    <p className="text-xs text-[#737373]">
                      {t("settingsEmailHint")}
                    </p>
                  </div>
                </CardContent>

                <CardFooter className="border-t border-[#E5E5E5] pt-4">
                  <Button
                    type="submit"
                    data-testid="save-profile-btn"
                    disabled={savingProfile || displayName.trim().length < 2}
                    className="bg-[#0A0A0A] hover:bg-[#262626] text-white whitespace-normal h-auto min-h-9 py-2 max-w-full"
                  >
                    {savingProfile ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        {t("savingProfile")}
                      </>
                    ) : (
                      t("saveProfile")
                    )}
                  </Button>
                </CardFooter>
              </form>
            </Card>
          )}

          {/* TAB 2: Learning Preferences */}
          {activeTab === "preferences" && (
            <Card className="border-[#E5E5E5] shadow-sm">
              <form noValidate onSubmit={handleSavePreferences} data-testid="preferences-form">
                <CardHeader>
                  <CardTitle className="text-lg"><h2 className="flex items-center gap-2">{t("settingsPreferences")}</h2></CardTitle>
                  <CardDescription>
                    {t("preferencesDescription")}
                  </CardDescription>
                </CardHeader>

                <CardContent className="space-y-6">
                  {preferencesSuccess && (
                    <div
                      className="p-3 bg-muted border border-border rounded-md flex items-center gap-2 text-sm text-foreground"
                      role="status" data-testid="preferences-success-alert"
                    >
                      <CheckCircle2 className="h-4 w-4 text-foreground shrink-0" />
                      <span>{t(preferencesSuccess)}</span>
                    </div>
                  )}

                  {preferencesError && (
                    <div
                      className="p-3 bg-muted border border-border rounded-md flex items-center gap-2 text-sm text-foreground"
                      role="alert"
                      id="settings-preferences-error"
                      data-testid="preferences-error-alert"
                    >
                      <AlertCircle className="h-4 w-4 text-foreground shrink-0" />
                      <span>{t(preferencesError)}</span>
                    </div>
                  )}

                  {/* Daily Study Goal */}
                  <div className="space-y-3">
                    <div className="flex items-center gap-2">
                      <Target className="h-4 w-4 text-[#0A0A0A]" />
                      <Label htmlFor={dailyGoalId} className="text-sm font-medium">
                        {t("dailyTarget")}
                      </Label>
                    </div>
                    <div className="flex items-center gap-3 max-w-md">
                      <Input
                        id={dailyGoalId}
                        data-testid="input-daily-goal"
                        aria-describedby={preferencesError ? "settings-preferences-error" : undefined}
                        aria-invalid={!!preferencesError}
                        type="number"
                        min={1}
                        max={500}
                        value={dailyGoal}
                        onChange={(e) => setDailyGoal(Math.max(1, Math.min(500, Number(e.target.value) || 1)))}
                        disabled={savingPreferences}
                        className="w-32 border-[#E5E5E5]"
                      />
                      <span className="text-sm text-[#737373]">{t("itemsPerDay")}</span>
                    </div>

                    {/* Quick Presets */}
                    <div className="flex flex-wrap gap-2 items-center text-xs">
                      <span className="text-[#737373]">{t("presets")}</span>
                      {[10, 20, 30, 50].map((preset) => (
                        <button
                          key={preset}
                          type="button"
                          data-testid={`preset-goal-${preset}`}
                          aria-pressed={dailyGoal === preset}
                          onClick={() => setDailyGoal(preset)}
                          className={`px-2 py-1 rounded border transition-colors ${
                            dailyGoal === preset
                              ? "bg-[#0A0A0A] text-white border-[#0A0A0A]"
                              : "bg-[#FAFAFA] text-[#0A0A0A] border-[#E5E5E5] hover:bg-[#F0F0F0]"
                          }`}
                        >
                          {preset} {preset === 20 ? t("defaultChoice") : ""}
                        </button>
                      ))}
                    </div>
                    <p className="text-xs text-[#737373]">
                      {t("dailyTargetHint")}
                    </p>
                  </div>

                  {/* IANA Timezone Selector */}
                  <div className="space-y-3 pt-4 border-t border-[#E5E5E5]">
                    <div className="flex items-center gap-2">
                      <Clock className="h-4 w-4 text-[#0A0A0A]" />
                      <Label htmlFor={timezoneSearchId} className="text-sm font-medium">
                        {t("studyTimezone")}
                      </Label>
                    </div>

                    <div className="max-w-md space-y-2">
                      {/* Search Filter */}
                      <div className="relative">
                        <Search className="absolute left-3 top-2.5 h-4 w-4 text-[#A3A3A3]" />
                        <Input
                          id={timezoneSearchId}
                          data-testid="input-timezone-search"
                        aria-describedby={preferencesError ? "settings-preferences-error" : undefined}
                        aria-invalid={!!preferencesError}
                          type="text"
                          placeholder={t("timezoneSearch")}
                          value={timezoneSearch}
                          onChange={(e) => setTimezoneSearch(e.target.value)}
                          className="pl-9 border-[#E5E5E5]"
                        />
                      </div>

                      {/* Dropdown Options */}
                      <div
                        className="max-h-48 overflow-y-auto border border-[#E5E5E5] rounded-md divide-y divide-[#F0F0F0] bg-white"
                        data-testid="timezone-options-list"
                      >
                        {filteredTimezones.length === 0 ? (
                          <div className="p-3 text-xs text-[#737373] text-center">
                            {t("timezoneEmpty")}
                          </div>
                        ) : (
                          filteredTimezones.map((tz) => (
                            <button
                              key={tz}
                              type="button"
                              data-testid={`timezone-opt-${tz}`}
                              aria-pressed={selectedTimezone === tz}
                              onClick={() => setSelectedTimezone(tz)}
                              className={`w-full text-left px-3 py-2 text-sm flex items-center justify-between hover:bg-[#F9F9F9] transition-colors ${
                                selectedTimezone === tz ? "bg-[#F5F5F5] font-medium" : ""
                              }`}
                            >
                              <span>{tz}</span>
                              {selectedTimezone === tz && (
                                <CheckCircle2 className="h-4 w-4 text-[#0A0A0A]" />
                              )}
                            </button>
                          ))
                        )}
                      </div>
                    </div>

                    <div className="p-3 bg-[#FAFAFA] border border-[#E5E5E5] rounded-md max-w-md text-xs text-[#525252] space-y-1">
                      <p className="font-semibold text-[#0A0A0A]">
                        {t("selectedTimezone")} <span data-testid="selected-timezone-badge">{selectedTimezone}</span>
                      </p>
                      <p>
                        {t("timezoneHint")}
                      </p>
                    </div>
                  </div>

                  {/* Preferred Audio Playback Speed */}
                  <div className="space-y-3 pt-4 border-t border-[#E5E5E5]">
                    <div className="flex items-center gap-2">
                      <Volume2 className="h-4 w-4 text-[#0A0A0A]" />
                      <Label className="text-sm font-medium">{t("audioSpeed")}</Label>
                    </div>

                    <div className="flex flex-wrap gap-2" data-testid="audio-speed-chips">
                      {AUDIO_SPEEDS.map((speed) => (
                        <button
                          key={speed}
                          type="button"
                          data-testid={`speed-chip-${speed}`}
                          aria-pressed={audioSpeed === speed}
                          onClick={() => setAudioSpeed(speed)}
                          disabled={savingPreferences}
                          className={`px-4 py-2 rounded-md text-sm font-medium border transition-colors ${
                            audioSpeed === speed
                              ? "bg-[#0A0A0A] text-white border-[#0A0A0A]"
                              : "bg-white text-[#0A0A0A] border-[#E5E5E5] hover:bg-[#FAFAFA]"
                          }`}
                        >
                          {speed}x {speed === 0.75 ? t("speedSlow") : speed === 1.0 ? t("speedStandard") : t("speedFast")}
                        </button>
                      ))}
                    </div>
                    <p className="text-xs text-[#737373]">
                      {t("audioSpeedHint")}
                    </p>
                  </div>
                </CardContent>

                <CardFooter className="border-t border-[#E5E5E5] pt-4">
                  <Button
                    type="submit"
                    data-testid="save-preferences-btn"
                    disabled={savingPreferences}
                    className="bg-[#0A0A0A] hover:bg-[#262626] text-white whitespace-normal h-auto min-h-9 py-2 max-w-full"
                  >
                    {savingPreferences ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        {t("savingPreferences")}
                      </>
                    ) : (
                      t("savePreferences")
                    )}
                  </Button>
                </CardFooter>
              </form>
            </Card>
          )}

          {/* TAB 3: Security & Password Change */}
          {activeTab === "security" && (
            <Card className="border-[#E5E5E5] shadow-sm">
              <form noValidate onSubmit={handleSavePassword} data-testid="security-form">
                <CardHeader>
                  <CardTitle className="text-lg"><h2 className="flex items-center gap-2">{t("securityTitle")}</h2></CardTitle>
                  <CardDescription>
                    {t("securityDescription")}
                  </CardDescription>
                </CardHeader>

                <CardContent className="space-y-6">
                  {passwordSuccess && (
                    <div
                      className="p-3 bg-muted border border-border rounded-md flex items-center gap-2 text-sm text-foreground"
                      role="status" data-testid="password-success-alert"
                    >
                      <CheckCircle2 className="h-4 w-4 text-foreground shrink-0" />
                      <span>{t(passwordSuccess)}</span>
                    </div>
                  )}

                  {passwordError && (
                    <div
                      className="p-3 bg-muted border border-border rounded-md flex items-center gap-2 text-sm text-foreground"
                      role="alert"
                      id="settings-password-error"
                      data-testid="password-error-alert"
                    >
                      <AlertCircle className="h-4 w-4 text-foreground shrink-0" />
                      <span>{t(passwordError)}</span>
                    </div>
                  )}

                  {/* Current Password */}
                  <div className="space-y-2">
                    <Label htmlFor={currentPassId} className="text-sm font-medium">
                      {t("currentPassword")}
                    </Label>
                    <div className="relative max-w-md">
                      <Input
                        id={currentPassId}
                        data-testid="input-current-password"
                        aria-describedby={passwordError ? "settings-password-error" : undefined}
                        aria-invalid={!!passwordError}
                        type={showCurrentPass ? "text" : "password"}
                        value={currentPassword}
                        onChange={(e) => setCurrentPassword(e.target.value)}
                        placeholder="••••••••••••"
                        disabled={savingPassword}
                        className="pr-10 border-[#E5E5E5]"
                      />
                      <button
                        type="button"
                        onClick={() => setShowCurrentPass(!showCurrentPass)}
                        className="absolute right-1 top-1/2 -translate-y-1/2 flex size-9 items-center justify-center rounded text-muted-foreground hover:text-foreground focus-visible:ring-2"
                        aria-label={t(showCurrentPass ? "hidePassword" : "showPassword")}
                      >
                        {showCurrentPass ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                      </button>
                    </div>
                  </div>

                  {/* New Password */}
                  <div className="space-y-2">
                    <Label htmlFor={newPassId} className="text-sm font-medium">
                      {t("newPassword")}
                    </Label>
                    <div className="relative max-w-md">
                      <Input
                        id={newPassId}
                        data-testid="input-new-password"
                        aria-describedby={passwordError ? "settings-password-error" : undefined}
                        aria-invalid={!!passwordError}
                        type={showNewPass ? "text" : "password"}
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        placeholder={t("newPasswordPlaceholder")}
                        disabled={savingPassword}
                        className="pr-10 border-[#E5E5E5]"
                      />
                      <button
                        type="button"
                        onClick={() => setShowNewPass(!showNewPass)}
                        className="absolute right-1 top-1/2 -translate-y-1/2 flex size-9 items-center justify-center rounded text-muted-foreground hover:text-foreground focus-visible:ring-2"
                        aria-label={t(showNewPass ? "hidePassword" : "showPassword")}
                      >
                        {showNewPass ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                      </button>
                    </div>

                    {/* Password Requirements Checklist */}
                    <div className="space-y-1 text-xs text-[#737373] max-w-md pt-1">
                      <div className="flex items-center gap-1.5">
                        <CheckCircle2
                          className={`h-3.5 w-3.5 ${
                            newPassword.length >= 8 ? "text-foreground" : "text-[#D4D4D4]"
                          }`}
                        />
                        <span>{t("passwordLengthRule")}</span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <CheckCircle2
                          className={`h-3.5 w-3.5 ${
                            /[0-9]/.test(newPassword) ? "text-foreground" : "text-[#D4D4D4]"
                          }`}
                        />
                        <span>{t("passwordDigitRule")}</span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <CheckCircle2
                          className={`h-3.5 w-3.5 ${
                            /[^a-zA-Z0-9]/.test(newPassword) ? "text-foreground" : "text-[#D4D4D4]"
                          }`}
                        />
                        <span>{t("passwordSymbolRule")}</span>
                      </div>
                    </div>
                  </div>

                  {/* Confirm New Password */}
                  <div className="space-y-2">
                    <Label htmlFor={confirmPassId} className="text-sm font-medium">
                      {t("confirmNewPassword")}
                    </Label>
                    <div className="max-w-md">
                      <Input
                        id={confirmPassId}
                        data-testid="input-confirm-password"
                        aria-describedby={passwordError ? "settings-password-error" : undefined}
                        aria-invalid={!!passwordError}
                        type="password"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        placeholder={t("confirmNewPlaceholder")}
                        disabled={savingPassword}
                        className="border-[#E5E5E5]"
                      />
                    </div>
                  </div>
                </CardContent>

                <CardFooter className="border-t border-[#E5E5E5] pt-4">
                  <Button
                    type="submit"
                    data-testid="change-password-btn"
                    disabled={savingPassword || !currentPassword || !newPassword || !confirmPassword}
                    className="bg-[#0A0A0A] hover:bg-[#262626] text-white whitespace-normal h-auto min-h-9 py-2 max-w-full"
                  >
                    {savingPassword ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        {t("changingPassword")}
                      </>
                    ) : (
                      t("changePassword")
                    )}
                  </Button>
                </CardFooter>
              </form>
            </Card>
          )}

          {/* TAB 4: Account Management & Deletion */}
          {activeTab === "account" && (
            <div className="space-y-6">
              {/* Account Overview */}
              <Card className="border-[#E5E5E5] shadow-sm">
                <CardHeader>
                  <CardTitle className="text-lg"><h2 className="flex items-center gap-2">{t("accountInformation")}</h2></CardTitle>
                  <CardDescription>{t("accountDescription")}</CardDescription>
                </CardHeader>
                <CardContent className="space-y-3 text-sm">
                  <div className="flex justify-between py-2 border-b border-[#F0F0F0]">
                    <span className="text-[#737373]">{t("accountStatus")}</span>
                    <Badge variant="outline" className="border-border bg-muted text-foreground">
                      {t("signedIn")}
                    </Badge>
                  </div>
                  <div className="flex justify-between py-2 border-b border-[#F0F0F0]">
                    <span className="text-[#737373]">{t("accountRegistered")}</span>
                    <span className="font-mono text-xs text-[#0A0A0A]">
                      {settings?.createdAt ? new Date(settings.createdAt).toLocaleDateString(locale, { timeZone: settings.timezone }) : "—"}
                    </span>
                  </div>
                  <div className="flex justify-between py-2">
                    <span className="text-[#737373]">{t("accountProtection")}</span>
                    <span className="text-xs text-[#0A0A0A]">{t("protectedSession")}</span>
                  </div>
                </CardContent>
              </Card>

              {/* Danger Zone: Account Deletion */}
              <Card className="border-2 border-border bg-muted/30 shadow-sm" data-testid="danger-zone-card">
                <CardHeader>
                  <CardTitle className="text-lg text-foreground flex items-center gap-2"><h2 className="flex items-center gap-2">
                    <Trash2 className="h-5 w-5 text-foreground" />
                    {t("accountDangerTitle")}
                  </h2></CardTitle>
                  <CardDescription className="text-foreground">
                    {t("accountDangerDescription")}
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-3 text-sm text-foreground">
                  <p>
                    {t("deleteWarning")}
                  </p>
                  <ul className="list-disc pl-5 space-y-1 text-xs">
                    <li>{t("deleteVocabulary")}</li>
                    <li>{t("deleteReviews")}</li>
                    <li>{t("deleteDictation")}</li>
                    <li>{t("deleteShadowing")}</li>
                    <li>{t("deleteQuiz")}</li>
                    <li>{t("deleteActivity")}</li>
                  </ul>
                  <p className="text-xs text-foreground">
                    {t("sharedContentPreserved")}
                  </p>
                </CardContent>
                <CardFooter className="border-t border-border pt-4">
                  <Button
                    variant="outline"
                    data-testid="open-delete-modal-btn"
                    ref={deleteTriggerRef}
                    onClick={() => {
                      setShowDeleteModal(true);
                      setDeleteError(null);
                      setDeletePassword("");
                    }}
                    className="border-border text-foreground hover:bg-foreground hover:text-background whitespace-normal h-auto min-h-9 py-2 max-w-full"
                  >
                    {t("openDelete")}
                  </Button>
                </CardFooter>
              </Card>

              {/* Confirmation Modal */}
              <Dialog open={showDeleteModal} onOpenChange={(open) => { if (!deletingAccount) setShowDeleteModal(open); }}>
                  <DialogContent showCloseButton={false} finalFocus={deleteTriggerRef} aria-busy={deletingAccount}
                    aria-labelledby="delete-dialog-title" aria-describedby="delete-dialog-description"
                    data-testid="delete-account-dialog" className="w-[calc(100vw-2rem)] max-w-md max-h-[calc(100dvh-2rem)] overflow-y-auto border-border bg-background">
                    <CardHeader className="px-0">
                      <DialogTitle id="delete-dialog-title" className="text-lg text-foreground flex items-center gap-2">
                        <AlertCircle className="h-5 w-5 text-foreground" />
                        {t("confirmDeleteTitle")}
                      </DialogTitle>
                      <CardDescription id="delete-dialog-description">
                        {t("confirmDeleteDescription")}
                      </CardDescription>
                    </CardHeader>

                    <CardContent className="px-0 space-y-4">
                      {deleteError && (
                        <div
                          className="p-3 bg-muted border border-border rounded-md text-xs text-foreground flex items-center gap-2"
                          role="alert"
                      id="settings-delete-error"
                      data-testid="delete-error-alert"
                        >
                          <AlertCircle className="h-4 w-4 text-foreground shrink-0" />
                          <span>{t(deleteError)}</span>
                        </div>
                      )}

                      <div className="space-y-2">
                        <Label htmlFor={deletePassId} className="text-sm font-medium">
                          {t("verifyPassword")}
                        </Label>
                        <Input
                          id={deletePassId}
                          data-testid="input-delete-password"
                        aria-describedby={deleteError ? "settings-delete-error" : undefined}
                        aria-invalid={!!deleteError}
                          type="password"
                          value={deletePassword}
                          onChange={(e) => setDeletePassword(e.target.value)}
                          placeholder={t("deletePasswordPlaceholder")}
                          disabled={deletingAccount}
                          className="border-[#E5E5E5]"
                        />
                      </div>
                    </CardContent>

                    <CardFooter className="px-0 border-t border-border pt-4 flex flex-col items-stretch gap-2 sm:flex-row sm:flex-wrap sm:items-center sm:justify-end">
                      <Button
                        type="button"
                        variant="outline"
                        data-testid="cancel-delete-btn"
                        onClick={() => setShowDeleteModal(false)}
                        disabled={deletingAccount}
                       className="whitespace-normal h-auto min-h-9 py-2 max-w-full">
                        {t("cancel")}
                      </Button>
                      <Button
                        type="button"
                        data-testid="confirm-delete-btn"
                        onClick={handleDeleteAccount}
                        disabled={deletingAccount || !deletePassword}
                        className="bg-foreground hover:bg-foreground/90 text-background whitespace-normal h-auto min-h-9 py-2 max-w-full"
                      >
                        {deletingAccount ? (
                          <>
                            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                            {t("deletingAccount")}
                          </>
                        ) : (
                          t("confirmDelete")
                        )}
                      </Button>
                    </CardFooter>
                  </DialogContent>
              </Dialog>
            </div>
          )}
          </section>
        </>
      )}
    </div>
  );
}
