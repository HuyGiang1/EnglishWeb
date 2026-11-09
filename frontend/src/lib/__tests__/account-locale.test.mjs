import "./setup-dom.mjs";
import "./production-loader.mjs";
import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { render, screen, fireEvent, cleanup, waitFor, act } from "@testing-library/react";
import { renderToString } from "react-dom/server";
import { hydrateRoot } from "react-dom/client";

const { default: Login } = await import("../../app/(auth)/login/page.tsx");
const { default: Register } = await import("../../app/(auth)/register/page.tsx");
const { default: Settings } = await import("../../app/(workspace)/settings/page.tsx");
const { SidebarContent } = await import("../../components/layout/sidebar.tsx");
const { LocaleProvider } = await import("../i18n/locale-provider.tsx");
const { AuthProvider } = await import("../auth/auth-context.tsx");
const { vi, en } = await import("../i18n/dictionaries.ts");
const originalFetch = globalThis.fetch;
let account, requests, update, bootstrap;
function response(body, status = 200) { return new Response(status === 204 ? null : JSON.stringify(body), { status }); }
function tree(Page = Settings, locale = "vi-VN") {
  return React.createElement(LocaleProvider, { initialLocale: locale },
    React.createElement(AuthProvider, null, React.createElement(Page), Page === Settings ? React.createElement(SidebarContent) : null));
}
async function mount(Page = Settings, locale) {
  render(tree(Page, locale));
  await waitFor(() => assert.ok(requests.some((r) => r.url.endsWith("/auth/me"))));
  if (Page === Settings) await screen.findByDisplayValue("Learner");
}
function choose(locale) { fireEvent.change(screen.getByRole("combobox"), { target: { value: locale } }); }
function fillLogin() {
  fireEvent.change(document.getElementById("email"), { target: { value: "learner@example.com" } });
  fireEvent.change(document.getElementById("password"), { target: { value: "Secret123!" } });
}
function updates() { return requests.filter((r) => r.url.endsWith("/settings/preferences")); }

describe("M3-EVO-1B — actual Settings/auth account locale flows", () => {
  beforeEach(() => {
    cleanup();
    for (const cookie of document.cookie.split(";")) document.cookie = `${cookie.split("=")[0].trim()}=; Max-Age=0; Path=/`;
    account = { id: "account-a", email: "learner@example.com", displayName: "Learner", timezone: "UTC", dailyGoalItems: 20, preferredAudioSpeed: 1, preferredLocale: "en", createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z" };
    requests = [];
    bootstrap = null;
    update = async (body) => { account = { ...account, ...body }; return response(account); };
    globalThis.fetch = async (url, options = {}) => {
      requests.push({ url, ...options });
      if (url.endsWith("/auth/me")) return bootstrap ? bootstrap() : account ? response(account) : response({ code: "UNAUTHORIZED" }, 401);
      if (url.endsWith("/auth/csrf")) { document.cookie = "XSRF-TOKEN=account-csrf; Path=/"; return response(null, 204); }
      if (url.endsWith("/auth/logout")) { account = null; return response(null, 204); }
      if (url.endsWith("/auth/login")) return response(account);
      if (url.endsWith("/auth/register")) {
        account = { ...JSON.parse(options.body), id: "new-account", dailyGoalItems: 20 };
        return response(account, 201);
      }
      if (url.endsWith("/settings/preferences")) return update(JSON.parse(options.body));
      if (url.endsWith("/settings")) return response(account);
      throw new Error(`Unmocked URL ${url}`);
    };
  });
  afterEach(() => { cleanup(); globalThis.fetch = originalFetch; });

  it("guest selection remains cookie-only with no preference requests", async () => {
    account = null;
    await mount(Login);
    choose("en");
    assert.match(document.cookie, /englishflow_locale=en/);
    assert.equal(document.documentElement.lang, "en");
    assert.equal(updates().length, 0);
  });

  it("bootstrap account preference wins over guest cookie, once, without a write", async () => {
    await mount();
    await waitFor(() => assert.equal(document.documentElement.lang, "en"));
    assert.match(document.cookie, /englishflow_locale=en/);
    assert.equal(updates().length, 0);
    assert.equal(requests.filter((r) => r.url.endsWith("/auth/me")).length, 1);
  });

  it("login with explicit account preference overrides guest selection before redirect", async () => {
    bootstrap = () => response({ code: "UNAUTHORIZED" }, 401);
    account.preferredLocale = "en";
    await mount(Login, "vi-VN"); fillLogin();
    fireEvent.submit(document.querySelector("form"));
    await waitFor(() => assert.equal(document.documentElement.lang, "en"));
    assert.equal(updates().length, 0);
  });

  it("login with legacy null preserves English guest preference without automatic writes", async () => {
    bootstrap = () => response({ code: "UNAUTHORIZED" }, 401);
    account.preferredLocale = null;
    await mount(Login, "en"); fillLogin();
    fireEvent.submit(document.querySelector("form"));
    await waitFor(() => assert.ok(requests.some((r) => r.url.endsWith("/auth/login"))));
    assert.equal(document.documentElement.lang, "en");
    assert.equal(updates().length, 0);
  });

  it("registration inherits selected English locale and keeps entered form values", async () => {
    account = null;
    await mount(Register);
    for (const [id, value] of Object.entries({ displayName: "Learner", email: "learner@example.com", password: "Secret123!", confirmPassword: "Secret123!" })) fireEvent.change(document.getElementById(id), { target: { value } });
    choose("en");
    assert.equal(document.getElementById("displayName").value, "Learner");
    fireEvent.submit(document.querySelector("form"));
    await waitFor(() => assert.ok(account));
    assert.equal(account.preferredLocale, "en");
    assert.equal(document.documentElement.lang, "en");
    assert.equal(updates().length, 0);
  });

  it("Settings persists authenticated changes with CSRF and reload reads account preference", async () => {
    await mount();
    choose("vi-VN");
    await waitFor(() => assert.equal(account.preferredLocale, "vi-VN"));
    assert.equal(updates().length, 1);
    assert.equal(updates()[0].headers.get("X-XSRF-TOKEN"), "account-csrf");
    assert.deepEqual(JSON.parse(updates()[0].body), { preferredLocale: "vi-VN" });
    cleanup();
    await mount(Settings, "en");
    await waitFor(() => assert.equal(document.documentElement.lang, "vi-VN"));
    assert.equal(updates().length, 1);
  });

  it("logout retains current locale cookie and late preference response cannot change guest UI", async () => {
    await mount();
    let finish;
    update = () => new Promise((resolve) => { finish = resolve; });
    choose("vi-VN");
    await waitFor(() => assert.ok(finish));
    fireEvent.click(screen.getByRole("button", { name: vi.signOut }));
    await waitFor(() => assert.equal(account, null));
    choose("en");
    await act(async () => finish(response({ preferredLocale: "vi-VN" })));
    assert.match(document.cookie, /englishflow_locale=en/);
    assert.equal(document.documentElement.lang, "en");
  });

  it("failed save rolls back UI/cookie and shows a safe retryable message", async () => {
    await mount();
    update = async () => response({ code: "UNAVAILABLE", message: "RAW_PRIVATE_EXCEPTION" }, 503);
    choose("vi-VN");
    await screen.findByText(en.localeSaveFailed);
    assert.equal(document.documentElement.lang, "en");
    assert.match(document.cookie, /englishflow_locale=en/);
    assert.ok(!document.body.textContent.includes("RAW_PRIVATE_EXCEPTION"));
    update = async (body) => { account = { ...account, ...body }; return response(account); };
    choose("vi-VN");
    await waitFor(() => assert.equal(account.preferredLocale, "vi-VN"));
    await waitFor(() => assert.equal(screen.queryByText(vi.localeSaveFailed), null));
  });

  it("rapid changes are serialized; stale success cannot overwrite newer UI", async () => {
    account.preferredLocale = "vi-VN";
    await mount();
    const completions = [];
    update = (body) => new Promise((resolve) => completions.push(() => { account = { ...account, ...body }; resolve(response(account)); }));
    choose("en"); choose("vi-VN"); choose("en");
    await waitFor(() => assert.equal(completions.length, 1));
    assert.equal(updates().length, 1);
    await act(async () => completions[0]());
    await waitFor(() => assert.equal(completions.length, 2));
    await act(async () => completions[1]());
    assert.equal(document.documentElement.lang, "en");
    await waitFor(() => assert.equal(completions.length, 3));
    await act(async () => completions[2]());
    assert.equal(account.preferredLocale, "en");
    assert.match(document.cookie, /englishflow_locale=en/);
    assert.deepEqual(updates().map((r) => JSON.parse(r.body).preferredLocale), ["en", "vi-VN", "en"]);
  });

  it("newer failure rolls back to last confirmed save, not the initial preference", async () => {
    account.preferredLocale = "vi-VN";
    await mount();
    let finish;
    update = () => new Promise((resolve) => { finish = resolve; });
    choose("en"); choose("vi-VN");
    await waitFor(() => assert.ok(finish));
    const first = finish;
    await act(async () => first(response({ preferredLocale: "en" })));
    await waitFor(() => assert.notEqual(finish, first));
    await act(async () => finish(response({ code: "FAILED" }, 500)));
    await screen.findByText(en.localeSaveFailed);
    assert.equal(document.documentElement.lang, "en");
  });

  it("an older failed save cannot roll back a newer choice or display a stale error", async () => {
    await mount();
    const completions = [];
    update = () => new Promise((resolve) => completions.push(resolve));
    choose("vi-VN"); choose("en");
    await waitFor(() => assert.equal(completions.length, 1));
    await act(async () => completions[0](response({ code: "FAILED" }, 503)));
    await waitFor(() => assert.equal(completions.length, 2));
    assert.equal(document.documentElement.lang, "en");
    assert.equal(screen.queryByText(en.localeSaveFailed), null);
    await act(async () => completions[1](response({ preferredLocale: "en" })));
    assert.equal(screen.queryByText(en.localeSaveFailed), null);
  });

  it("invalid account locale preserves current guest language rather than forcing a fallback", async () => {
    account.preferredLocale = "fr";
    await mount(Settings, "en");
    assert.equal(document.documentElement.lang, "en");
    assert.equal(updates().length, 0);
  });

  it("late bootstrap cannot overwrite a more recent successful login", async () => {
    let finish;
    bootstrap = () => new Promise((resolve) => { finish = resolve; });
    await mount(Login, "vi-VN"); fillLogin();
    fireEvent.submit(document.querySelector("form"));
    await waitFor(() => assert.equal(document.documentElement.lang, "en"));
    await act(async () => finish(response({ ...account, preferredLocale: "vi-VN" })));
    assert.equal(document.documentElement.lang, "en");
  });

  it("unmount invalidates a pending bootstrap before it can change the cookie", async () => {
    let finish;
    bootstrap = () => new Promise((resolve) => { finish = resolve; });
    await mount(Login, "vi-VN");
    cleanup();
    const cookie = document.cookie;
    await act(async () => finish(response(account)));
    assert.equal(document.cookie, cookie);
  });

  it("hydrates with the SSR guest locale then reconciles account preference without mismatch", async () => {
    const element = tree(Settings, "vi-VN");
    const container = document.createElement("div");
    container.innerHTML = renderToString(element);
    document.body.append(container);
    const errors = [];
    let root;
    try {
      await act(async () => { root = hydrateRoot(container, element, { onRecoverableError: (error) => errors.push(error) }); });
      await waitFor(() => assert.equal(document.documentElement.lang, "en"));
      assert.deepEqual(errors, []);
      assert.equal(updates().length, 0);
    } finally {
      await act(async () => root?.unmount());
      container.remove();
    }
  });
});
