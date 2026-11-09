import "./setup-dom.mjs";
import "./production-loader.mjs";
import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { render, screen, fireEvent, cleanup, waitFor, act } from "@testing-library/react";
import { renderToString } from "react-dom/server";
import { hydrateRoot } from "react-dom/client";
import { navigations } from "./production-next-stubs.mjs";

const { default: Login } = await import("../../app/(auth)/login/page.tsx");
const { default: Register } = await import("../../app/(auth)/register/page.tsx");
const { default: RootLayout } = await import("../../app/layout.tsx");
const { default: AuthLayout } = await import("../../app/(auth)/layout.tsx");
const { LocaleProvider } = await import("../i18n/locale-provider.tsx");
const { AuthProvider } = await import("../auth/auth-context.tsx");
const { resolveLocale, localeCookie, localeCookieName } = await import("../i18n/config.ts");
const { translate, dictionaries, en, vi } = await import("../i18n/dictionaries.ts");
const { authErrorKey } = await import("../auth/auth-errors.ts");
const { ApiError } = await import("../api/client.ts");

const originalFetch = globalThis.fetch;
let requests;
let result;
const user = { id: "test-user", email: "learner@example.com", displayName: "Learner", timezone: "UTC", dailyGoalItems: 20 };
function tree(Page = Login, locale = resolveLocale(document.cookie.split("; ").find((c) => c.startsWith(`${localeCookieName}=`))?.split("=")[1])) {
  return React.createElement(LocaleProvider, { initialLocale: locale },
    React.createElement(AuthProvider, null, React.createElement(AuthLayout, null, React.createElement(Page))));
}
async function mount(Page = Login, locale) {
  const view = render(tree(Page, locale));
  await waitFor(() => assert.ok(requests.some((r) => r.url.endsWith("/auth/me"))));
  return view;
}
function fillLogin() {
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "learner@example.com" } });
  fireEvent.change(document.getElementById("password"), { target: { value: "Secret123" } });
}
function switchTo(locale) { fireEvent.change(screen.getByRole("combobox"), { target: { value: locale } }); }

describe("M3-EVO-1A — production authentication localization", () => {
  beforeEach(() => {
    cleanup();
    for (const cookie of document.cookie.split(";")) document.cookie = `${cookie.split("=")[0].trim()}=; Max-Age=0; Path=/`;
    navigations.length = 0;
    requests = [];
    result = { status: 401, code: "INVALID_CREDENTIALS", message: "RAW_SECRET_BACKEND_EXCEPTION" };
    globalThis.fetch = async (url, options = {}) => {
      requests.push({ url, ...options });
      if (url.endsWith("/auth/me")) return new Response(JSON.stringify({ code: "UNAUTHORIZED" }), { status: 401 });
      if (url.endsWith("/auth/csrf")) {
        document.cookie = "XSRF-TOKEN=csrf-test; Path=/";
        return new Response(null, { status: 204 });
      }
      const body = result.status === 200 ? user : result;
      return new Response(JSON.stringify(body), { status: result.status });
    };
  });
  afterEach(() => { cleanup(); globalThis.fetch = originalFetch; });

  it("renders Vietnamese by default, with localized auth chrome and form labels", async () => {
    await mount();
    assert.ok(screen.getByText("Tiếp tục luyện tiếng Anh mỗi ngày."));
    assert.ok(screen.getByText("Tập trung học tiếng Anh"));
    assert.ok(screen.getByLabelText("Mật khẩu"));
    assert.equal(document.documentElement.lang, "vi-VN");
  });

  it("renders English from a valid preference and persists switching without submission or value loss", async () => {
    document.cookie = "englishflow_locale=en; Path=/";
    await mount();
    fillLogin();
    fireEvent.click(screen.getByRole("button", { name: "Show password" }));
    switchTo("vi-VN");
    assert.equal(document.getElementById("password").value, "Secret123");
    assert.equal(document.getElementById("password").type, "text");
    assert.equal(document.getElementById("email").value, "learner@example.com");
    assert.equal(document.documentElement.lang, "vi-VN");
    assert.match(document.cookie, /englishflow_locale=vi-VN/);
    assert.equal(requests.filter((r) => r.method === "POST").length, 0);
    cleanup();
    await mount();
    assert.equal(screen.getByRole("combobox").value, "vi-VN");
  });

  it("keeps validation state and re-translates messages after switching", async () => {
    await mount(Login, "en");
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "bad" } });
    fireEvent.submit(document.querySelector("form"));
    await screen.findByText("Enter a valid email address.");
    switchTo("vi-VN");
    assert.ok(screen.getByText("Nhập địa chỉ email hợp lệ."));
    assert.ok(screen.getByText("Nhập mật khẩu của bạn."));
    assert.equal(document.getElementById("email").value, "bad");
    assert.equal(document.getElementById("email").getAttribute("aria-invalid"), "true");
  });

  it("localizes API errors by stable code and re-translates an existing alert", async () => {
    await mount(); fillLogin();
    fireEvent.submit(document.querySelector("form"));
    assert.match((await screen.findByRole("alert")).textContent, /Email hoặc mật khẩu không đúng/);
    switchTo("en");
    assert.equal(screen.getByRole("alert").textContent, "Email or password is incorrect.");
    assert.ok(!document.body.textContent.includes("RAW_SECRET"));
  });

  it("maps all known auth codes and masks unknown codes and non-API exceptions", () => {
    for (const [code, key] of Object.entries({ INVALID_CREDENTIALS: "invalidCredentials", UNAUTHORIZED: "invalidCredentials", EMAIL_ALREADY_REGISTERED: "emailRegistered", VALIDATION_FAILED: "validationFailed", FORBIDDEN: "forbidden", NETWORK_ERROR: "networkError" })) {
      assert.equal(authErrorKey(new ApiError(400, "sensitive", code)), key);
    }
    assert.equal(authErrorKey(new ApiError(500, "sensitive", "NEW_UNKNOWN_CODE")), "authFallback");
    assert.equal(authErrorKey(new Error("sensitive")), "authFallback");
  });

  it("shows a safe localized fallback for unknown server errors in the production page", async () => {
    result = { status: 500, code: "UNEXPECTED", message: "RAW_SECRET_BACKEND_EXCEPTION" };
    await mount(); fillLogin(); fireEvent.submit(document.querySelector("form"));
    assert.equal((await screen.findByRole("alert")).textContent, vi.authFallback);
    assert.ok(!document.body.textContent.includes("RAW_SECRET"));
  });

  it("preserves register fields, confirmation, visibility and validation when changing language", async () => {
    await mount(Register);
    for (const [id, value] of Object.entries({ displayName: "Learner", email: "learner@example.com", password: "Secret123", confirmPassword: "different" })) {
      fireEvent.change(document.getElementById(id), { target: { value } });
    }
    fireEvent.submit(document.querySelector("form"));
    await screen.findByText(vi.passwordMismatch);
    fireEvent.click(screen.getByRole("button", { name: vi.showPassword }));
    switchTo("en");
    assert.ok(screen.getByText(en.passwordMismatch));
    assert.equal(document.getElementById("displayName").value, "Learner");
    assert.equal(document.getElementById("confirmPassword").value, "different");
    assert.equal(document.getElementById("confirmPassword").type, "text");
    assert.equal(document.getElementById("password").value, "Secret123");
    assert.equal(requests.filter((r) => r.method === "POST").length, 0);
  });

  it("registers using the additive locale payload, handles duplicate email and keeps CSRF", async () => {
    result = { status: 409, code: "EMAIL_ALREADY_REGISTERED", message: "RAW_SECRET" };
    await mount(Register, "en");
    for (const [id, value] of Object.entries({ displayName: "Learner", email: "learner@example.com", password: "Secret123", confirmPassword: "Secret123" })) {
      fireEvent.change(document.getElementById(id), { target: { value } });
    }
    fireEvent.submit(document.querySelector("form"));
    assert.equal((await screen.findByRole("alert")).textContent, en.emailRegistered);
    const request = requests.find((r) => r.url.endsWith("/auth/register"));
    assert.equal(request.headers.get("X-XSRF-TOKEN"), "csrf-test");
    assert.equal(request.credentials, "same-origin");
    assert.deepEqual(Object.keys(JSON.parse(request.body)).sort(), ["displayName", "email", "password", "preferredLocale", "timezone"]);
    result = { status: 200 };
    fireEvent.submit(document.querySelector("form"));
    await waitFor(() => assert.ok(navigations.some(([method, url]) => method === "push" && url === "/dashboard")));
  });

  it("logs in successfully with unchanged payload, CSRF header and dashboard redirect", async () => {
    result = { status: 200 };
    await mount(); fillLogin(); fireEvent.submit(document.querySelector("form"));
    await waitFor(() => assert.ok(navigations.some(([method, url]) => method === "push" && url === "/dashboard")));
    const request = requests.find((r) => r.url.endsWith("/auth/login"));
    assert.equal(request.headers.get("X-XSRF-TOKEN"), "csrf-test");
    assert.equal(request.credentials, "same-origin");
    assert.deepEqual(JSON.parse(request.body), { email: "learner@example.com", password: "Secret123" });
  });

  it("translates the loading state while preserving an in-flight request", async () => {
    const fetchResponse = globalThis.fetch;
    let finish;
    globalThis.fetch = (url, options) => url.endsWith("/auth/login")
      ? new Promise((resolve) => { finish = resolve; }) : fetchResponse(url, options);
    await mount(); fillLogin(); fireEvent.submit(document.querySelector("form"));
    await screen.findByRole("button", { name: vi.signingIn });
    switchTo("en");
    assert.ok(screen.getByRole("button", { name: en.signingIn }).disabled);
    assert.equal(document.getElementById("password").value, "Secret123");
    await act(async () => finish(new Response(JSON.stringify({ code: "INVALID_CREDENTIALS" }), { status: 401 })));
    assert.equal((await screen.findByRole("alert")).textContent, en.invalidCredentials);
  });

  it("uses a labelled native select and keyboard-focusable password control", async () => {
    await mount();
    const select = screen.getByRole("combobox", { name: vi.language });
    select.focus();
    fireEvent.keyDown(select, { key: "ArrowDown" });
    // JSDOM has no native select keyboard default action; simulate the resulting change.
    switchTo("en");
    assert.equal(document.activeElement, select);
    const toggle = screen.getByRole("button", { name: en.showPassword });
    assert.equal(toggle.tabIndex, 0);
    assert.equal(toggle.type, "button");
    assert.equal(screen.getByRole("link", { name: en.registerTitle }).getAttribute("href"), "/register");
  });

  it("resolves unsupported values and emits scoped, non-sensitive cookie attributes", () => {
    for (const value of [undefined, null, "fr", "en-US", "", "toString"]) assert.equal(resolveLocale(value), "vi-VN");
    assert.equal(resolveLocale("en"), "en");
    assert.equal(resolveLocale("vi-VN"), "vi-VN");
    assert.equal(localeCookie("en", true), "englishflow_locale=en; Path=/; Max-Age=31536000; SameSite=Lax; Secure");
    assert.ok(!localeCookie("en", false).includes("Secure"));
  });

  it("falls back to English for missing dictionary entries, and never exposes unknown keys", () => {
    assert.deepEqual(Object.keys(vi).sort(), Object.keys(en).sort());
    const original = dictionaries["vi-VN"];
    try {
      dictionaries["vi-VN"] = {};
      assert.equal(translate("vi-VN", "loginTitle"), en.loginTitle);
      assert.equal(translate("en", "arbitrary_backend_text"), en.authFallback);
      assert.equal(translate("en", "constructor"), en.authFallback);
    } finally { dictionaries["vi-VN"] = original; }
  });

  it("server renders root HTML lang and the same initial auth locale from cookies", async () => {
    for (const [cookie, expected] of [["", "vi-VN"], ["en", "en"], ["fr", "vi-VN"]]) {
      document.cookie = `englishflow_locale=${cookie}; Path=/`;
      const html = renderToString(await RootLayout({ children: React.createElement(Login) }));
      assert.ok(html.includes(`<html lang="${expected}"`));
      assert.ok(html.includes(translate(expected, "loginDescription")));
    }
  });

  it("hydrates the real auth tree without recoverable mismatch for both locales", async () => {
    for (const locale of ["vi-VN", "en"]) {
      const element = tree(Login, locale);
      const container = document.createElement("div");
      container.innerHTML = renderToString(element);
      document.body.append(container);
      const errors = [];
      let root;
      await act(async () => {
        root = hydrateRoot(container, element, { onRecoverableError: (error) => errors.push(error) });
      });
      assert.deepEqual(errors, []);
      assert.equal(document.documentElement.lang, locale);
      await act(async () => root.unmount());
      container.remove();
    }
  });
});
