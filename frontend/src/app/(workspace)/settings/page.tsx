"use client";

import { useEffect, useState, useCallback, useId } from "react";
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
import { ApiError } from "@/lib/api/client";

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

  // Active Tab state: general | preferences | security | account
  const [activeTab, setActiveTab] = useState<"general" | "preferences" | "security" | "account">("general");

  // Initial loading & core settings
  const [loading, setLoading] = useState<boolean>(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [settings, setSettings] = useState<UserSettings | null>(null);

  // Profile Form state
  const [displayName, setDisplayName] = useState<string>("");
  const [savingProfile, setSavingProfile] = useState<boolean>(false);
  const [profileSuccess, setProfileSuccess] = useState<string | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);

  // Preferences Form state
  const [dailyGoal, setDailyGoal] = useState<number>(20);
  const [selectedTimezone, setSelectedTimezone] = useState<string>("UTC");
  const [timezoneSearch, setTimezoneSearch] = useState<string>("");
  const [audioSpeed, setAudioSpeed] = useState<number>(1.0);
  const [savingPreferences, setSavingPreferences] = useState<boolean>(false);
  const [preferencesSuccess, setPreferencesSuccess] = useState<string | null>(null);
  const [preferencesError, setPreferencesError] = useState<string | null>(null);

  // Security Form state
  const [currentPassword, setCurrentPassword] = useState<string>("");
  const [newPassword, setNewPassword] = useState<string>("");
  const [confirmPassword, setConfirmPassword] = useState<string>("");
  const [showCurrentPass, setShowCurrentPass] = useState<boolean>(false);
  const [showNewPass, setShowNewPass] = useState<boolean>(false);
  const [savingPassword, setSavingPassword] = useState<boolean>(false);
  const [passwordSuccess, setPasswordSuccess] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  // Danger Zone / Account Deletion Modal
  const [showDeleteModal, setShowDeleteModal] = useState<boolean>(false);
  const [deletePassword, setDeletePassword] = useState<string>("");
  const [deletingAccount, setDeletingAccount] = useState<boolean>(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

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
      const msg = err instanceof Error ? err.message : "Failed to load account settings";
      setLoadError(msg);
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
      setProfileError("Display name must be between 2 and 100 characters.");
      return;
    }

    setSavingProfile(true);
    try {
      const updated = await settingsApi.updateProfile({ displayName: trimmed });
      setSettings(updated);
      setDisplayName(updated.displayName);
      setProfileSuccess("Profile updated successfully.");
    } catch (err: unknown) {
      const msg = err instanceof ApiError ? err.message : "Failed to update profile.";
      setProfileError(msg);
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
      setPreferencesError("Daily goal must be between 1 and 500 items.");
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
      setPreferencesSuccess("Learning preferences saved successfully.");
    } catch (err: unknown) {
      const msg = err instanceof ApiError ? err.message : "Failed to save preferences.";
      setPreferencesError(msg);
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
      setPasswordError("Current password is required.");
      return;
    }

    if (newPassword.length < 8 || newPassword.length > 100) {
      setPasswordError("New password must be between 8 and 100 characters.");
      return;
    }

    const hasNumber = /[0-9]/.test(newPassword);
    const hasSpecial = /[^a-zA-Z0-9]/.test(newPassword);
    if (!hasNumber || !hasSpecial) {
      setPasswordError("New password must contain at least one digit and one special character.");
      return;
    }

    if (newPassword !== confirmPassword) {
      setPasswordError("New password and confirmation do not match.");
      return;
    }

    if (newPassword === currentPassword) {
      setPasswordError("New password cannot be the same as your current password.");
      return;
    }

    setSavingPassword(true);
    try {
      await settingsApi.changePassword({
        currentPassword,
        newPassword,
        confirmPassword,
      });
      setPasswordSuccess("Password changed successfully. Your active session remains authenticated.");
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err: unknown) {
      const msg = err instanceof ApiError ? err.message : "Failed to change password.";
      setPasswordError(msg);
    } finally {
      setSavingPassword(false);
    }
  };

  // Handle Account Deletion
  const handleDeleteAccount = async () => {
    setDeleteError(null);
    if (!deletePassword) {
      setDeleteError("Current password is required to verify account closure.");
      return;
    }

    setDeletingAccount(true);
    try {
      await settingsApi.deleteAccount({ password: deletePassword });
      router.push("/login?message=Account+closed");
    } catch (err: unknown) {
      const msg = err instanceof ApiError ? err.message : "Failed to delete account. Please verify your password.";
      setDeleteError(msg);
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
        <h1 className="text-3xl font-bold tracking-tight text-[#0A0A0A]">Settings</h1>
        <p className="text-sm text-[#737373] mt-1">
          Configure your learner profile, daily study targets, study timezone, and security credentials.
        </p>
      </div>

      {/* Loading Skeleton */}
      {loading ? (
        <div className="space-y-6" data-testid="settings-skeleton">
          <div className="flex gap-2 border-b border-[#E5E5E5] pb-2">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="h-9 w-28 bg-[#F0F0F0] rounded animate-pulse" />
            ))}
          </div>
          <Card className="border-[#E5E5E5]">
            <CardHeader className="space-y-2">
              <div className="h-6 w-48 bg-[#F0F0F0] rounded animate-pulse" />
              <div className="h-4 w-72 bg-[#F0F0F0] rounded animate-pulse" />
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="h-10 w-full bg-[#F0F0F0] rounded animate-pulse" />
              <div className="h-10 w-full bg-[#F0F0F0] rounded animate-pulse" />
            </CardContent>
          </Card>
        </div>
      ) : loadError ? (
        <Card className="border-red-200 bg-red-50 text-red-900">
          <CardContent className="p-6 flex items-start gap-3">
            <AlertCircle className="h-5 w-5 text-red-600 shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold text-sm">Failed to load account settings</p>
              <p className="text-sm mt-1 text-red-700">{loadError}</p>
              <Button
                variant="outline"
                size="sm"
                className="mt-4 border-red-300 text-red-900 hover:bg-red-100"
                onClick={loadSettings}
              >
                Retry
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
            aria-label="Settings navigation tabs"
          >
            <button
              role="tab"
              aria-selected={activeTab === "general"}
              data-testid="tab-general"
              onClick={() => setActiveTab("general")}
              className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                activeTab === "general"
                  ? "border-[#0A0A0A] text-[#0A0A0A]"
                  : "border-transparent text-[#737373] hover:text-[#0A0A0A]"
              }`}
            >
              <User className="h-4 w-4" />
              Profile
            </button>

            <button
              role="tab"
              aria-selected={activeTab === "preferences"}
              data-testid="tab-preferences"
              onClick={() => setActiveTab("preferences")}
              className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                activeTab === "preferences"
                  ? "border-[#0A0A0A] text-[#0A0A0A]"
                  : "border-transparent text-[#737373] hover:text-[#0A0A0A]"
              }`}
            >
              <Sliders className="h-4 w-4" />
              Learning Preferences
            </button>

            <button
              role="tab"
              aria-selected={activeTab === "security"}
              data-testid="tab-security"
              onClick={() => setActiveTab("security")}
              className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                activeTab === "security"
                  ? "border-[#0A0A0A] text-[#0A0A0A]"
                  : "border-transparent text-[#737373] hover:text-[#0A0A0A]"
              }`}
            >
              <Shield className="h-4 w-4" />
              Security
            </button>

            <button
              role="tab"
              aria-selected={activeTab === "account"}
              data-testid="tab-account"
              onClick={() => setActiveTab("account")}
              className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                activeTab === "account"
                  ? "border-[#0A0A0A] text-[#0A0A0A]"
                  : "border-transparent text-[#737373] hover:text-[#0A0A0A]"
              }`}
            >
              <Trash2 className="h-4 w-4" />
              Account
            </button>
          </div>

          {/* TAB 1: General & Profile */}
          {activeTab === "general" && (
            <Card className="border-[#E5E5E5] shadow-sm">
              <form onSubmit={handleSaveProfile} data-testid="profile-form">
                <CardHeader>
                  <CardTitle className="text-lg">Account Profile</CardTitle>
                  <CardDescription>
                    Manage your public learner identity. Your email is your canonical account identifier.
                  </CardDescription>
                </CardHeader>

                <CardContent className="space-y-6">
                  {profileSuccess && (
                    <div
                      className="p-3 bg-emerald-50 border border-emerald-200 rounded-md flex items-center gap-2 text-sm text-emerald-800"
                      data-testid="profile-success-alert"
                    >
                      <CheckCircle2 className="h-4 w-4 text-emerald-600 shrink-0" />
                      <span>{profileSuccess}</span>
                    </div>
                  )}

                  {profileError && (
                    <div
                      className="p-3 bg-red-50 border border-red-200 rounded-md flex items-center gap-2 text-sm text-red-800"
                      data-testid="profile-error-alert"
                    >
                      <AlertCircle className="h-4 w-4 text-red-600 shrink-0" />
                      <span>{profileError}</span>
                    </div>
                  )}

                  {/* Display Name */}
                  <div className="space-y-2">
                    <div className="flex justify-between items-center">
                      <Label htmlFor={displayNameId} className="text-sm font-medium">
                        Display Name
                      </Label>
                      <span className="text-xs text-[#737373]">{displayName.trim().length} / 100</span>
                    </div>
                    <Input
                      id={displayNameId}
                      data-testid="input-display-name"
                      type="text"
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      placeholder="e.g. Minh Tran"
                      maxLength={100}
                      disabled={savingProfile}
                      className="max-w-md border-[#E5E5E5]"
                    />
                    <p className="text-xs text-[#737373]">
                      Used across your dashboard metrics and study activity badges.
                    </p>
                  </div>

                  {/* Account Email (Read-Only) */}
                  <div className="space-y-2">
                    <div className="flex items-center gap-2">
                      <Label htmlFor={emailId} className="text-sm font-medium">
                        Account Email
                      </Label>
                      <Badge variant="outline" className="text-xs font-normal border-[#E5E5E5] text-[#737373]">
                        Verified ID
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
                      Email changes are locked for security integrity. Contact support for identity updates.
                    </p>
                  </div>
                </CardContent>

                <CardFooter className="border-t border-[#E5E5E5] pt-4">
                  <Button
                    type="submit"
                    data-testid="save-profile-btn"
                    disabled={savingProfile || displayName.trim().length < 2}
                    className="bg-[#0A0A0A] hover:bg-[#262626] text-white"
                  >
                    {savingProfile ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        Saving Profile...
                      </>
                    ) : (
                      "Save Profile"
                    )}
                  </Button>
                </CardFooter>
              </form>
            </Card>
          )}

          {/* TAB 2: Learning Preferences */}
          {activeTab === "preferences" && (
            <Card className="border-[#E5E5E5] shadow-sm">
              <form onSubmit={handleSavePreferences} data-testid="preferences-form">
                <CardHeader>
                  <CardTitle className="text-lg">Learning Preferences</CardTitle>
                  <CardDescription>
                    Configure your daily review targets, study timezone for midnight streak rollover, and audio speeds.
                  </CardDescription>
                </CardHeader>

                <CardContent className="space-y-6">
                  {preferencesSuccess && (
                    <div
                      className="p-3 bg-emerald-50 border border-emerald-200 rounded-md flex items-center gap-2 text-sm text-emerald-800"
                      data-testid="preferences-success-alert"
                    >
                      <CheckCircle2 className="h-4 w-4 text-emerald-600 shrink-0" />
                      <span>{preferencesSuccess}</span>
                    </div>
                  )}

                  {preferencesError && (
                    <div
                      className="p-3 bg-red-50 border border-red-200 rounded-md flex items-center gap-2 text-sm text-red-800"
                      data-testid="preferences-error-alert"
                    >
                      <AlertCircle className="h-4 w-4 text-red-600 shrink-0" />
                      <span>{preferencesError}</span>
                    </div>
                  )}

                  {/* Daily Study Goal */}
                  <div className="space-y-3">
                    <div className="flex items-center gap-2">
                      <Target className="h-4 w-4 text-[#0A0A0A]" />
                      <Label htmlFor={dailyGoalId} className="text-sm font-medium">
                        Daily Study Target (Words / Actions)
                      </Label>
                    </div>
                    <div className="flex items-center gap-3 max-w-md">
                      <Input
                        id={dailyGoalId}
                        data-testid="input-daily-goal"
                        type="number"
                        min={1}
                        max={500}
                        value={dailyGoal}
                        onChange={(e) => setDailyGoal(Math.max(1, Math.min(500, Number(e.target.value) || 1)))}
                        disabled={savingPreferences}
                        className="w-32 border-[#E5E5E5]"
                      />
                      <span className="text-sm text-[#737373]">items / day</span>
                    </div>

                    {/* Quick Presets */}
                    <div className="flex gap-2 items-center text-xs">
                      <span className="text-[#737373]">Presets:</span>
                      {[10, 20, 30, 50].map((preset) => (
                        <button
                          key={preset}
                          type="button"
                          data-testid={`preset-goal-${preset}`}
                          onClick={() => setDailyGoal(preset)}
                          className={`px-2 py-1 rounded border transition-colors ${
                            dailyGoal === preset
                              ? "bg-[#0A0A0A] text-white border-[#0A0A0A]"
                              : "bg-[#FAFAFA] text-[#0A0A0A] border-[#E5E5E5] hover:bg-[#F0F0F0]"
                          }`}
                        >
                          {preset} {preset === 20 ? "(Default)" : ""}
                        </button>
                      ))}
                    </div>
                    <p className="text-xs text-[#737373]">
                      Used for your daily goal badge on Dashboard and Heatmap. Changing this never alters past study logs.
                    </p>
                  </div>

                  {/* IANA Timezone Selector */}
                  <div className="space-y-3 pt-4 border-t border-[#E5E5E5]">
                    <div className="flex items-center gap-2">
                      <Clock className="h-4 w-4 text-[#0A0A0A]" />
                      <Label htmlFor={timezoneSearchId} className="text-sm font-medium">
                        Study Timezone (IANA)
                      </Label>
                    </div>

                    <div className="max-w-md space-y-2">
                      {/* Search Filter */}
                      <div className="relative">
                        <Search className="absolute left-3 top-2.5 h-4 w-4 text-[#A3A3A3]" />
                        <Input
                          id={timezoneSearchId}
                          data-testid="input-timezone-search"
                          type="text"
                          placeholder="Search timezone (e.g. Ho_Chi_Minh, London, New_York)..."
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
                            No matching timezones found
                          </div>
                        ) : (
                          filteredTimezones.map((tz) => (
                            <button
                              key={tz}
                              type="button"
                              data-testid={`timezone-opt-${tz}`}
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
                        Current selected timezone: <span data-testid="selected-timezone-badge">{selectedTimezone}</span>
                      </p>
                      <p>
                        Essential: Determines midnight rollover for daily streak calculation. Historical learning events remain permanently anchored to their original local dates.
                      </p>
                    </div>
                  </div>

                  {/* Preferred Audio Playback Speed */}
                  <div className="space-y-3 pt-4 border-t border-[#E5E5E5]">
                    <div className="flex items-center gap-2">
                      <Volume2 className="h-4 w-4 text-[#0A0A0A]" />
                      <Label className="text-sm font-medium">Preferred Audio Playback Speed</Label>
                    </div>

                    <div className="flex gap-3" data-testid="audio-speed-chips">
                      {AUDIO_SPEEDS.map((speed) => (
                        <button
                          key={speed}
                          type="button"
                          data-testid={`speed-chip-${speed}`}
                          onClick={() => setAudioSpeed(speed)}
                          disabled={savingPreferences}
                          className={`px-4 py-2 rounded-md text-sm font-medium border transition-colors ${
                            audioSpeed === speed
                              ? "bg-[#0A0A0A] text-white border-[#0A0A0A]"
                              : "bg-white text-[#0A0A0A] border-[#E5E5E5] hover:bg-[#FAFAFA]"
                          }`}
                        >
                          {speed}x {speed === 0.75 ? "(Slow)" : speed === 1.0 ? "(Standard)" : "(Fast)"}
                        </button>
                      ))}
                    </div>
                    <p className="text-xs text-[#737373]">
                      Initial playback rate for Dictation and Speech Shadowing players. You can still adjust speed during individual sessions.
                    </p>
                  </div>
                </CardContent>

                <CardFooter className="border-t border-[#E5E5E5] pt-4">
                  <Button
                    type="submit"
                    data-testid="save-preferences-btn"
                    disabled={savingPreferences}
                    className="bg-[#0A0A0A] hover:bg-[#262626] text-white"
                  >
                    {savingPreferences ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        Saving Preferences...
                      </>
                    ) : (
                      "Save Preferences"
                    )}
                  </Button>
                </CardFooter>
              </form>
            </Card>
          )}

          {/* TAB 3: Security & Password Change */}
          {activeTab === "security" && (
            <Card className="border-[#E5E5E5] shadow-sm">
              <form onSubmit={handleSavePassword} data-testid="security-form">
                <CardHeader>
                  <CardTitle className="text-lg">Security & Authentication</CardTitle>
                  <CardDescription>
                    Update your password. Requires your active password to verify authorization.
                  </CardDescription>
                </CardHeader>

                <CardContent className="space-y-6">
                  {passwordSuccess && (
                    <div
                      className="p-3 bg-emerald-50 border border-emerald-200 rounded-md flex items-center gap-2 text-sm text-emerald-800"
                      data-testid="password-success-alert"
                    >
                      <CheckCircle2 className="h-4 w-4 text-emerald-600 shrink-0" />
                      <span>{passwordSuccess}</span>
                    </div>
                  )}

                  {passwordError && (
                    <div
                      className="p-3 bg-red-50 border border-red-200 rounded-md flex items-center gap-2 text-sm text-red-800"
                      data-testid="password-error-alert"
                    >
                      <AlertCircle className="h-4 w-4 text-red-600 shrink-0" />
                      <span>{passwordError}</span>
                    </div>
                  )}

                  {/* Current Password */}
                  <div className="space-y-2">
                    <Label htmlFor={currentPassId} className="text-sm font-medium">
                      Current Password
                    </Label>
                    <div className="relative max-w-md">
                      <Input
                        id={currentPassId}
                        data-testid="input-current-password"
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
                        className="absolute right-3 top-2.5 text-[#737373] hover:text-[#0A0A0A]"
                        aria-label="Toggle password visibility"
                      >
                        {showCurrentPass ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                      </button>
                    </div>
                  </div>

                  {/* New Password */}
                  <div className="space-y-2">
                    <Label htmlFor={newPassId} className="text-sm font-medium">
                      New Password
                    </Label>
                    <div className="relative max-w-md">
                      <Input
                        id={newPassId}
                        data-testid="input-new-password"
                        type={showNewPass ? "text" : "password"}
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        placeholder="At least 8 characters with number & symbol"
                        disabled={savingPassword}
                        className="pr-10 border-[#E5E5E5]"
                      />
                      <button
                        type="button"
                        onClick={() => setShowNewPass(!showNewPass)}
                        className="absolute right-3 top-2.5 text-[#737373] hover:text-[#0A0A0A]"
                        aria-label="Toggle password visibility"
                      >
                        {showNewPass ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                      </button>
                    </div>

                    {/* Password Requirements Checklist */}
                    <div className="space-y-1 text-xs text-[#737373] max-w-md pt-1">
                      <div className="flex items-center gap-1.5">
                        <CheckCircle2
                          className={`h-3.5 w-3.5 ${
                            newPassword.length >= 8 ? "text-emerald-600" : "text-[#D4D4D4]"
                          }`}
                        />
                        <span>At least 8 characters</span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <CheckCircle2
                          className={`h-3.5 w-3.5 ${
                            /[0-9]/.test(newPassword) ? "text-emerald-600" : "text-[#D4D4D4]"
                          }`}
                        />
                        <span>At least one digit (0–9)</span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <CheckCircle2
                          className={`h-3.5 w-3.5 ${
                            /[^a-zA-Z0-9]/.test(newPassword) ? "text-emerald-600" : "text-[#D4D4D4]"
                          }`}
                        />
                        <span>At least one special character</span>
                      </div>
                    </div>
                  </div>

                  {/* Confirm New Password */}
                  <div className="space-y-2">
                    <Label htmlFor={confirmPassId} className="text-sm font-medium">
                      Confirm New Password
                    </Label>
                    <div className="max-w-md">
                      <Input
                        id={confirmPassId}
                        data-testid="input-confirm-password"
                        type="password"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        placeholder="Re-enter new password"
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
                    className="bg-[#0A0A0A] hover:bg-[#262626] text-white"
                  >
                    {savingPassword ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        Updating Password...
                      </>
                    ) : (
                      "Change Password"
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
                  <CardTitle className="text-lg">Account Information</CardTitle>
                  <CardDescription>System metadata and active subscription state.</CardDescription>
                </CardHeader>
                <CardContent className="space-y-3 text-sm">
                  <div className="flex justify-between py-2 border-b border-[#F0F0F0]">
                    <span className="text-[#737373]">Account Status</span>
                    <Badge variant="outline" className="border-emerald-300 bg-emerald-50 text-emerald-800">
                      Active
                    </Badge>
                  </div>
                  <div className="flex justify-between py-2 border-b border-[#F0F0F0]">
                    <span className="text-[#737373]">Account Registered</span>
                    <span className="font-mono text-xs text-[#0A0A0A]">
                      {settings?.createdAt ? new Date(settings.createdAt).toLocaleDateString() : "—"}
                    </span>
                  </div>
                  <div className="flex justify-between py-2">
                    <span className="text-[#737373]">Session Security</span>
                    <span className="text-xs text-[#0A0A0A]">Spring Security HttpOnly Session + CSRF</span>
                  </div>
                </CardContent>
              </Card>

              {/* Danger Zone: Account Deletion */}
              <Card className="border-red-200 bg-red-50/30 shadow-sm" data-testid="danger-zone-card">
                <CardHeader>
                  <CardTitle className="text-lg text-red-900 flex items-center gap-2">
                    <Trash2 className="h-5 w-5 text-red-600" />
                    Danger Zone: Permanent Account Erasure
                  </CardTitle>
                  <CardDescription className="text-red-700">
                    Permanently delete your account and all associated learning history in compliance with the EnglishFlow Complete Data Erasure policy.
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-3 text-sm text-red-800">
                  <p>
                    Closing your account is <strong>permanent and irreversible</strong>. The following records will be immediately erased:
                  </p>
                  <ul className="list-disc pl-5 space-y-1 text-xs">
                    <li>All personal vocabulary items and custom collections</li>
                    <li>All Spaced Repetition (SRS) flashcard schedules and review logs</li>
                    <li>All daily dictation attempts and transcript diffs</li>
                    <li>All speech shadowing audio takes and self-assessment scores</li>
                    <li>All diagnostic quiz submissions and answer evaluations</li>
                    <li>Your 52-week activity heatmap and study streak history</li>
                  </ul>
                  <p className="text-xs text-red-600">
                    * Global shared exercises and platform lesson audio will remain intact for the community.
                  </p>
                </CardContent>
                <CardFooter className="border-t border-red-200 pt-4">
                  <Button
                    variant="outline"
                    data-testid="open-delete-modal-btn"
                    onClick={() => {
                      setShowDeleteModal(true);
                      setDeleteError(null);
                      setDeletePassword("");
                    }}
                    className="border-red-600 text-red-600 hover:bg-red-600 hover:text-white"
                  >
                    Delete Account Permanently
                  </Button>
                </CardFooter>
              </Card>

              {/* Confirmation Modal */}
              {showDeleteModal && (
                <div
                  className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4"
                  role="dialog"
                  aria-modal="true"
                  aria-labelledby="delete-dialog-title"
                  data-testid="delete-account-dialog"
                >
                  <Card className="max-w-md w-full border-red-300 shadow-2xl bg-white animate-in fade-in zoom-in-95">
                    <CardHeader>
                      <CardTitle id="delete-dialog-title" className="text-lg text-red-700 flex items-center gap-2">
                        <AlertCircle className="h-5 w-5 text-red-600" />
                        Confirm Account Deletion
                      </CardTitle>
                      <CardDescription>
                        This operation cannot be undone. Enter your current password to authorize permanent account closure.
                      </CardDescription>
                    </CardHeader>

                    <CardContent className="space-y-4">
                      {deleteError && (
                        <div
                          className="p-3 bg-red-50 border border-red-200 rounded-md text-xs text-red-800 flex items-center gap-2"
                          data-testid="delete-error-alert"
                        >
                          <AlertCircle className="h-4 w-4 text-red-600 shrink-0" />
                          <span>{deleteError}</span>
                        </div>
                      )}

                      <div className="space-y-2">
                        <Label htmlFor={deletePassId} className="text-sm font-medium">
                          Verify Current Password
                        </Label>
                        <Input
                          id={deletePassId}
                          data-testid="input-delete-password"
                          type="password"
                          value={deletePassword}
                          onChange={(e) => setDeletePassword(e.target.value)}
                          placeholder="Enter your account password"
                          disabled={deletingAccount}
                          className="border-[#E5E5E5]"
                        />
                      </div>
                    </CardContent>

                    <CardFooter className="border-t border-[#E5E5E5] pt-4 flex justify-end gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        data-testid="cancel-delete-btn"
                        onClick={() => setShowDeleteModal(false)}
                        disabled={deletingAccount}
                      >
                        Cancel
                      </Button>
                      <Button
                        type="button"
                        data-testid="confirm-delete-btn"
                        onClick={handleDeleteAccount}
                        disabled={deletingAccount || !deletePassword}
                        className="bg-red-600 hover:bg-red-700 text-white"
                      >
                        {deletingAccount ? (
                          <>
                            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                            Deleting Account...
                          </>
                        ) : (
                          "Permanently Delete My Account"
                        )}
                      </Button>
                    </CardFooter>
                  </Card>
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
