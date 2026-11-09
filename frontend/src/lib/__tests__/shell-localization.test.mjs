import "./setup-dom.mjs";
import "./production-loader.mjs";
import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { render, screen, within, fireEvent, cleanup, waitFor, act } from "@testing-library/react";
import { renderToString } from "react-dom/server";
import { hydrateRoot } from "react-dom/client";
import { setPathname, navigations } from "./production-next-stubs.mjs";

// DOM globals used by the real Base UI dialog/sheet implementation. Layout and
// physical keyboard behavior are verified separately in Chrome.
for (const name of ["Element", "Node", "Document", "ShadowRoot", "HTMLDivElement", "FocusEvent", "MouseEvent", "KeyboardEvent"])
  globalThis[name] = window[name];
globalThis.getComputedStyle = window.getComputedStyle.bind(window);
globalThis.requestAnimationFrame = window.requestAnimationFrame.bind(window);
globalThis.cancelAnimationFrame = window.cancelAnimationFrame.bind(window);

const { default: Dashboard } = await import("../../app/(workspace)/dashboard/page.tsx");
const { default: Settings } = await import("../../app/(workspace)/settings/page.tsx");
const { default: Workspace } = await import("../../app/(workspace)/layout.tsx");
const { Header } = await import("../../components/layout/header.tsx");
const { SidebarContent } = await import("../../components/layout/sidebar.tsx");
const { HeatmapCalendar } = await import("../../components/dashboard/heatmap-calendar.tsx");
const { FocusHeader } = await import("../../components/layout/focus-header.tsx");
const { LocaleProvider } = await import("../i18n/locale-provider.tsx");
const { AuthProvider } = await import("../auth/auth-context.tsx");
const { en, vi, dictionaries, translate } = await import("../i18n/dictionaries.ts");
const { shellEn, shellVi } = await import("../i18n/shell-dictionaries.ts");
const originalFetch = globalThis.fetch;
let account, summary, heatmap, requests, fail, patch;
function response(body, status = 200) { return new Response(status === 204 ? null : JSON.stringify(body), { status }); }
function fixtureCalendar() {
  const days = {};
  for (let i = 0; i < 364; i++) {
    const date = new Date(Date.UTC(2025, 10, 10 + i)).toISOString().slice(0, 10);
    const active = date === "2026-11-07";
    days[date] = { date, state: date > "2026-11-07" ? "FUTURE_DATE" : active ? "ACTIVE_DAY" : "ZERO_ACTIVITY", actionCount: active ? 5 : 0,
      intensityLevel: active ? 1 : 0, goalCompleted: false, breakdown: { flashcardReviews: active ? 2 : 0, dictationExercises: active ? 1 : 0, shadowingTakes: active ? 1 : 0, quizQuestions: active ? 1 : 0 } };
  }
  return { startDate: "2025-11-10", endDate: "2026-11-08", userTimezone: "Asia/Ho_Chi_Minh", accountCreatedAt: "2025-11-10T00:00:00Z", currentStreakDays: 3, longestStreakDays: 4, totalActiveDays: 1, streakStatus: "ACTIVE_STUDIED_TODAY", days };
}
function tree(Page, locale = "vi-VN", workspace = true) {
  return React.createElement(LocaleProvider, { initialLocale: locale }, React.createElement(AuthProvider, null,
    workspace ? React.createElement(Workspace, null, React.createElement(Page)) : React.createElement(Page)));
}
async function mount(Page = Dashboard, locale = "vi-VN") {
  account.preferredLocale = locale;
  setPathname(Page === Settings ? "/settings" : "/dashboard");
  const view = render(tree(Page, locale));
  await screen.findByTestId(Page === Settings ? "profile-form" : "dashboard-content");
  return view;
}
function main() { return within(screen.getByRole("main")); }
function input(id, value) { fireEvent.change(screen.getByTestId(id), { target: { value } }); }
async function changeLanguage(locale) {
  fireEvent.change(screen.getByRole("combobox"), { target: { value: locale } });
  await waitFor(() => assert.equal(account.preferredLocale, locale));
}

describe("M3-EVO-1C-1 — production shell, Dashboard and Settings", () => {
  beforeEach(() => {
    cleanup();
    for (const cookie of document.cookie.split(";")) document.cookie = `${cookie.split("=")[0].trim()}=; Max-Age=0; Path=/`;
    account = { id: "learner", email: "learner@example.com", displayName: "Minh Nguyễn", timezone: "Asia/Ho_Chi_Minh", dailyGoalItems: 20, preferredAudioSpeed: 1, preferredLocale: "vi-VN", createdAt: "2025-11-10T00:00:00Z", updatedAt: "2026-11-07T00:00:00Z" };
    summary = { greeting: "Welcome back, Minh Nguyễn", currentDate: "2026-11-07", vocabularyTotal: 57, dueReviewCount: 3, dailyGoalTarget: 20, dailyGoalCompletedCount: 5, goalProgressPercentage: 25, currentStreakDays: 3, streakStatus: "ACTIVE_STUDIED_TODAY", recentSessions: ["FLASHCARD_REVIEW", "DICTATION_SENTENCE", "SHADOWING_TAKE", "QUIZ_COMPLETION"].map((type, index) => ({ type, count: index ? 1 : 2, completedAt: "2026-11-07T08:00:00Z" })) };
    heatmap = fixtureCalendar(); requests = []; fail = {}; navigations.length = 0;
    patch = async (body) => { account = { ...account, ...body }; return response(account); };
    globalThis.fetch = async (url, options = {}) => {
      requests.push({ url, ...options });
      if (fail[url]) return response({ code: fail[url], message: "RAW_PRIVATE_SERVER_EXCEPTION" }, 500);
      if (url.endsWith("/auth/me")) {
        if (account) document.cookie = "XSRF-TOKEN=shell-csrf; Path=/";
        return account ? response(account) : response({ code: "UNAUTHORIZED" }, 401);
      }
      if (url.endsWith("/auth/csrf")) { document.cookie = "XSRF-TOKEN=shell-csrf; Path=/"; return response(null, 204); }
      if (url.endsWith("/dashboard/summary")) return response(summary);
      if (url.endsWith("/activity-heatmap")) return response(heatmap);
      if (url.endsWith("/settings/account") && options.method === "DELETE") { account = null; return response({ status: "DELETED" }); }
      if (url.endsWith("/settings") && options.method !== "PUT") return response(account);
      if (url.endsWith("/settings/password")) return patch(JSON.parse(options.body));
      if (url.endsWith("/settings/profile") || url.endsWith("/settings/preferences")) return patch(JSON.parse(options.body));
      throw new Error(`Unexpected URL ${url}`);
    };
  });
  afterEach(() => { cleanup(); globalThis.fetch = originalFetch; });

  for (const [locale, copy] of [["vi-VN", vi], ["en", en]]) {
    it(`${locale}: localizes all eight navigation destinations without milestone/debug metadata`, async () => {
      await mount(Dashboard, locale);
      const navs = screen.getAllByRole("navigation");
      for (const key of ["navDashboard", "navVocabulary", "navCollections", "navReview", "navDictation", "navShadowing", "navQuiz", "navSettings"]) assert.ok(navs.some((nav) => within(nav).queryByRole("link", { name: copy[key] })));
      assert.equal(document.querySelector('a[aria-current="page"]').getAttribute("href"), "/dashboard");
      assert.ok(!/M1|M2|8080|Proxy|Palette/.test(document.body.textContent));
      assert.equal(screen.getAllByRole("heading", { level: 1 }).length, 1);
    });

    it(`${locale}: Dashboard retains authoritative counts, goal, streak, heatmap and all activity types`, async () => {
      await mount(Dashboard, locale);
      for (const [id, value] of [["vocab", "57"], ["due", "3"], ["actions", "5"]]) assert.equal(screen.getByTestId(`kpi-${id}-count`).textContent, value);
      assert.equal(screen.getByTestId("goal-percent").textContent, "25%");
      assert.equal(main().getByRole("progressbar", { name: copy.dailyGoal }).getAttribute("aria-valuenow"), "25");
      assert.equal(main().getByTestId("cta-review-btn").getAttribute("href"), "/review");
      assert.equal(screen.getAllByRole("gridcell").length, 364);
      assert.equal(screen.getByTestId("heatmap-cell-2026-11-07").dataset.actions, "5");
      assert.equal(screen.getByTestId("heatmap-cell-2026-11-08").disabled, true);
      const recent = within(screen.getByTestId("recent-activity-section"));
      for (const key of ["navReview", "navDictation", "navShadowing", "navQuiz"]) assert.ok(recent.getByText(new RegExp(copy[key])));
      assert.ok(!document.body.textContent.includes("FLASHCARD_REVIEW"));
      assert.ok(main().getByRole("link", { name: copy.navVocabulary }));
    });

    it(`${locale}: Settings sections, placeholders and dangerous actions are localized`, async () => {
      await mount(Settings, locale);
      assert.ok(main().getByRole("heading", { name: copy.navSettings, level: 1 }));
      for (const key of ["settingsProfile", "settingsPreferences", "settingsSecurity", "settingsAccount"]) assert.ok(main().getByRole("tab", { name: copy[key] }));
      assert.equal(main().getByLabelText(copy.settingsDisplayName).value, "Minh Nguyễn");
      fireEvent.click(screen.getByTestId("tab-preferences"));
      assert.ok(main().getByLabelText(copy.dailyTarget));
      assert.ok(main().getByText(copy.audioSpeed));
      fireEvent.click(screen.getByTestId("tab-account"));
      assert.ok(main().getByText(copy.deleteWarning));
      assert.ok(main().getByRole("button", { name: copy.openDelete }));
      assert.ok(!/Spring Security|HttpOnly|CSRF|subscription/.test(screen.getByRole("main").textContent));
    });
  }

  it("Dashboard empty and caught-up states provide useful routes without invented progress", async () => {
    summary = { ...summary, vocabularyTotal: 0, dueReviewCount: 0, dailyGoalCompletedCount: 0, goalProgressPercentage: 0, currentStreakDays: 0, streakStatus: "BROKEN_RESET", recentSessions: [] };
    await mount();
    assert.equal(screen.getByTestId("cta-vocab-btn").getAttribute("href"), "/vocabulary");
    assert.equal(screen.getByTestId("cta-vocab-btn").textContent, vi.addFirstWords);
    assert.ok(main().getByText(vi.recentEmpty));
    assert.equal(screen.getByTestId("kpi-vocab-count").textContent, "0");
    cleanup(); summary.vocabularyTotal = 12;
    await mount();
    assert.ok(main().getByText(vi.allCaughtUp));
    assert.equal(screen.getByTestId("cta-vocab-btn").textContent, vi.exploreWords);
  });

  it("Dashboard load failure is safe/localized and retry restores the real populated view", async () => {
    fail["/api/v1/dashboard/summary"] = "INTERNAL_ERROR";
    render(tree(Dashboard));
    await screen.findByTestId("dashboard-error");
    assert.ok(!document.body.textContent.includes("RAW_PRIVATE"));
    assert.ok(main().getByText(vi.dashboardLoadFailed, { selector: "h1" }));
    fail = {};
    fireEvent.click(main().getByRole("button", { name: vi.retry }));
    await screen.findByTestId("dashboard-content");
    assert.equal(screen.getByTestId("kpi-vocab-count").textContent, "57");
  });

  it("heatmap failure leaves Dashboard statistics available and localizes its error", async () => {
    fail["/api/v1/analytics/activity-heatmap"] = "INTERNAL_ERROR";
    await mount();
    assert.ok(screen.getByTestId("heatmap-error"));
    assert.ok(main().getByText(vi.heatmapLoadFailed, { selector: "h3" }));
    assert.equal(screen.getByTestId("kpi-actions-count").textContent, "5");
    assert.ok(!document.body.textContent.includes("RAW_PRIVATE"));
  });

  it("heatmap keyboard starts at the last non-future day and exposes localized details", async () => {
    await mount();
    const cell = screen.getByTestId("heatmap-cell-2026-11-07");
    assert.equal(cell.tabIndex, 0);
    cell.focus(); fireEvent.keyDown(cell, { key: "ArrowUp" });
    const previous = screen.getByTestId("heatmap-cell-2026-11-06");
    assert.equal(document.activeElement, previous);
    fireEvent.keyDown(previous, { key: "Enter" });
    assert.ok(screen.getByRole("region", { name: /2026/ }));
    assert.ok(screen.getByRole("button", { name: vi.closeDayDetails }));
    fireEvent.click(screen.getByRole("button", { name: vi.closeDayDetails }));
    assert.equal(document.activeElement, previous);
  });

  it("recent-week heatmap navigation stays inside the visible window without changing activity data", async () => {
    render(React.createElement(LocaleProvider, { initialLocale: "vi-VN" }, React.createElement(HeatmapCalendar, { data: heatmap, isLoading: false, isError: false })));
    assert.equal(screen.getAllByRole("gridcell").length, 364);
    fireEvent.click(screen.getByRole("button", { name: vi.recentWeeks }));
    assert.equal(screen.getAllByRole("gridcell").length, 98);
    const cell = screen.getByTestId("heatmap-cell-2026-11-07");
    assert.equal(cell.dataset.actions, "5");
    fireEvent.keyDown(cell, { key: "Home" });
    assert.equal(document.activeElement.dataset.date, Object.keys(heatmap.days).sort().slice(-98)[0]);
    assert.equal(heatmap.days["2026-11-07"].actionCount, 5);
    fireEvent.click(screen.getByRole("button", { name: vi.fullCalendar }));
    assert.equal(screen.getAllByRole("gridcell").length, 364);
  });

  it("Settings language changes persist without losing draft profile or selected tab", async () => {
    await mount(Settings);
    input("input-display-name", "Tên đang sửa");
    await changeLanguage("en");
    assert.equal(screen.getByTestId("input-display-name").value, "Tên đang sửa");
    assert.ok(main().getByRole("tab", { name: en.settingsProfile, selected: true }));
    assert.match(document.cookie, /englishflow_locale=en/);
    assert.equal(document.documentElement.lang, "en");
    assert.ok(screen.getByRole("navigation", { name: en.navWorkspace }));
  });

  it("profile success and failure messages retranslate without exposing server text", async () => {
    await mount(Settings);
    input("input-display-name", "New Name");
    fireEvent.submit(screen.getByTestId("profile-form"));
    await screen.findByText(vi.profileSaved);
    await changeLanguage("en");
    assert.ok(screen.getByText(en.profileSaved));
    patch = async () => response({ code: "UNKNOWN", message: "RAW_PRIVATE_SERVER_EXCEPTION" }, 500);
    fireEvent.submit(screen.getByTestId("profile-form"));
    await screen.findByText(en.profileSaveFailed);
    assert.equal(screen.getByTestId("input-display-name").getAttribute("aria-describedby"), "settings-profile-error");
    assert.ok(!document.body.textContent.includes("RAW_PRIVATE"));
  });

  it("Settings loading errors are safe and localized, and retry keeps the account context", async () => {
    fail["/api/v1/settings"] = "INTERNAL_ERROR";
    render(tree(Settings));
    await screen.findByText(vi.settingsLoadFailed, { selector: "p.font-semibold" });
    assert.ok(!document.body.textContent.includes("RAW_PRIVATE"));
    fail = {};
    fireEvent.click(main().getByRole("button", { name: vi.retry }));
    await screen.findByTestId("profile-form");
    assert.equal(screen.getByTestId("input-display-name").value, "Minh Nguyễn");
  });

  it("profile validation and preference drafts survive language switching and tab navigation", async () => {
    await mount(Settings);
    input("input-display-name", "A");
    fireEvent.submit(screen.getByTestId("profile-form"));
    await screen.findByText(vi.profileNameInvalid);
    fireEvent.click(screen.getByTestId("tab-preferences"));
    fireEvent.click(screen.getByTestId("preset-goal-50"));
    await changeLanguage("en");
    assert.ok(main().getByRole("tab", { name: en.settingsPreferences, selected: true }));
    assert.equal(screen.getByTestId("input-daily-goal").value, "50");
    fireEvent.click(screen.getByTestId("tab-general"));
    assert.equal(screen.getByTestId("input-display-name").value, "A");
    assert.ok(screen.getByText(en.profileNameInvalid));
  });

  it("learning preferences keep the existing payload and CSRF behavior", async () => {
    await mount(Settings);
    fireEvent.click(screen.getByTestId("tab-preferences"));
    fireEvent.click(screen.getByTestId("preset-goal-30"));
    fireEvent.click(screen.getByTestId("timezone-opt-Asia/Tokyo"));
    fireEvent.click(screen.getByTestId("speed-chip-0.75"));
    fireEvent.submit(screen.getByTestId("preferences-form"));
    await screen.findByText(vi.preferencesSaved);
    const request = requests.find((r) => r.url.endsWith("/settings/preferences"));
    assert.deepEqual(JSON.parse(request.body), { timezone: "Asia/Tokyo", dailyGoalItems: 30, preferredAudioSpeed: 0.75 });
    assert.equal(request.headers.get("X-XSRF-TOKEN"), "shell-csrf");
    assert.equal(request.credentials, "same-origin");
  });

  it("password validation and stable-code server errors remain localized and associated", async () => {
    await mount(Settings);
    fireEvent.click(screen.getByTestId("tab-security"));
    input("input-current-password", "Current123!"); input("input-new-password", "short"); input("input-confirm-password", "short");
    fireEvent.submit(screen.getByTestId("security-form"));
    await screen.findByText(vi.newPasswordLength);
    input("input-new-password", "Different123!"); input("input-confirm-password", "Different123!");
    patch = async () => response({ code: "INVALID_CURRENT_PASSWORD", message: "RAW_PRIVATE" }, 400);
    fireEvent.submit(screen.getByTestId("security-form"));
    await screen.findByText(vi.incorrectPassword);
    assert.equal(screen.getByTestId("input-current-password").getAttribute("aria-describedby"), "settings-password-error");
    assert.ok(!document.body.textContent.includes("RAW_PRIVATE"));
  });

  it("Settings tabs support arrow/Home/End navigation with one tab stop", async () => {
    await mount(Settings);
    const profile = screen.getByRole("tab", { name: vi.settingsProfile });
    profile.focus(); fireEvent.keyDown(profile, { key: "ArrowRight" });
    const preferences = screen.getByRole("tab", { name: vi.settingsPreferences });
    assert.equal(document.activeElement, preferences);
    assert.equal(preferences.getAttribute("aria-selected"), "true");
    assert.equal(screen.getAllByRole("tab").filter((tab) => tab.tabIndex === 0).length, 1);
    fireEvent.keyDown(preferences, { key: "End" });
    assert.equal(screen.getByRole("tab", { name: vi.settingsAccount }).getAttribute("aria-selected"), "true");
    fireEvent.keyDown(document.activeElement, { key: "Home" });
    assert.equal(document.activeElement, profile);
  });

  it("account deletion keeps password confirmation, uses the shared modal and clears stale auth", async () => {
    await mount(Settings);
    fireEvent.click(screen.getByTestId("tab-account"));
    fireEvent.click(screen.getByTestId("open-delete-modal-btn"));
    const dialog = await screen.findByRole("dialog", { name: vi.confirmDeleteTitle });
    assert.ok(within(dialog).getByText(vi.confirmDeleteDescription));
    assert.equal(within(dialog).getByRole("button", { name: vi.confirmDelete }).disabled, true);
    input("input-delete-password", "Current123!");
    fireEvent.click(within(dialog).getByRole("button", { name: vi.confirmDelete }));
    await waitFor(() => assert.ok(navigations.some(([method, url]) => method === "replace" && url === "/login")));
    const request = requests.find((r) => r.method === "DELETE");
    assert.deepEqual(JSON.parse(request.body), { password: "Current123!" });
    assert.equal(request.headers.get("X-XSRF-TOKEN"), "shell-csrf");
    assert.ok(requests.filter((r) => r.url.endsWith("/auth/me")).length >= 2);
  });

  it("failed deletion leaves the confirmation open and translates the password error safely", async () => {
    await mount(Settings);
    fireEvent.click(screen.getByTestId("tab-account"));
    fireEvent.click(screen.getByTestId("open-delete-modal-btn"));
    await screen.findByRole("dialog");
    input("input-delete-password", "Wrong123!");
    fail["/api/v1/settings/account"] = "INVALID_CURRENT_PASSWORD";
    fireEvent.click(screen.getByTestId("confirm-delete-btn"));
    await screen.findByText(vi.incorrectPassword);
    assert.ok(screen.getByRole("dialog"));
    assert.ok(account);
    assert.ok(!document.body.textContent.includes("RAW_PRIVATE"));
    assert.equal(screen.getByTestId("input-delete-password").getAttribute("aria-describedby"), "settings-delete-error");
  });

  it("mobile navigation has localized names, closes on navigation and preserves routes", async () => {
    account.preferredLocale = "vi-VN";
    render(tree(() => React.createElement(Header), "vi-VN", false));
    fireEvent.click(screen.getByRole("button", { name: vi.openNavigation }));
    const dialog = await screen.findByRole("dialog", { name: vi.navigationMenu });
    const link = within(dialog).getByRole("link", { name: vi.navCollections });
    assert.equal(link.getAttribute("href"), "/collections");
    fireEvent.click(link);
    await waitFor(() => assert.equal(screen.queryByRole("dialog"), null));
  });

  it("nested routes keep navigation context and focus header has no unimplemented shortcut badge", async () => {
    setPathname("/collections/collection-a");
    render(tree(() => React.createElement(React.Fragment, null, React.createElement(SidebarContent), React.createElement(FocusHeader)), "vi-VN", false));
    assert.equal(screen.getByRole("link", { name: vi.navCollections }).getAttribute("aria-current"), "page");
    assert.equal(screen.getByRole("link", { name: vi.exitWorkspace }).getAttribute("href"), "/dashboard");
    assert.ok(!document.body.textContent.includes("Esc"));
  });

  it("all dictionary keys and interpolation parameters exist in both languages, with safe fallback", () => {
    assert.deepEqual(Object.keys(en).sort(), Object.keys(vi).sort());
    for (const key of Object.keys(shellEn)) {
      assert.ok(shellEn[key].trim()); assert.ok(shellVi[key].trim());
      assert.deepEqual([...shellEn[key].matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort(), [...shellVi[key].matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort());
    }
    assert.equal(translate("vi-VN", "reviewDue", { count: 3 }), "Ôn 3 từ đến hạn");
    const original = dictionaries["vi-VN"];
    try { dictionaries["vi-VN"] = {}; assert.equal(translate("vi-VN", "navDashboard"), en.navDashboard); }
    finally { dictionaries["vi-VN"] = original; }
  });

  it("the real workspace hydrates consistently before async Dashboard/account data arrives", async () => {
    const element = tree(Dashboard, "vi-VN");
    const container = document.createElement("div"); container.innerHTML = renderToString(element); document.body.append(container);
    const errors = []; let root;
    try {
      await act(async () => { root = hydrateRoot(container, element, { onRecoverableError: (error) => errors.push(error) }); });
      await screen.findByTestId("dashboard-content");
      assert.deepEqual(errors, []);
      assert.equal(document.documentElement.lang, "vi-VN");
    } finally { await act(async () => root?.unmount()); container.remove(); }
  });
});
