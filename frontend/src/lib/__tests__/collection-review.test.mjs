import "./setup-dom.mjs";
import "./production-loader.mjs";
import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { render, screen, fireEvent, cleanup, waitFor, act } from "@testing-library/react";
import { fixtureApi, word } from "./learning-fixtures.mjs";
import { setParams, setSearchParams } from "./production-next-stubs.mjs";
for (const name of ["Element", "Node", "Document", "ShadowRoot", "HTMLDivElement", "FocusEvent", "MouseEvent", "KeyboardEvent"]) globalThis[name] = window[name];
globalThis.getComputedStyle = window.getComputedStyle.bind(window);
globalThis.requestAnimationFrame = window.requestAnimationFrame.bind(window);
globalThis.cancelAnimationFrame = window.cancelAnimationFrame.bind(window);
const { LocaleProvider } = await import("../i18n/locale-provider.tsx");
const { LanguageSwitcher } = await import("../../components/auth/language-switcher.tsx");
const { translate } = await import("../i18n/dictionaries.ts");
const Review = (await import("../../app/(focus)/review/page.tsx")).default;
const Detail = (await import("../../app/(workspace)/collections/[id]/page.tsx")).default;
const e = React.createElement;
const collectionId = "04e08d83-fec4-4e15-9419-7e8da0c798f5";
const otherId = "fbf8a83e-9e0c-43ee-aad0-2aed75b1923a";
const card = { ...word, vocabularyId: word.id, srsItemId: "srs-id", intervalDays: 3, easeFactor: 2.5 };
const originalFetch = globalThis.fetch;
const originalNow = Date.now;
let api, requests, queue, queueError, failGrade;
function mount(locale = "en", Component = Review) {
  return render(e(LocaleProvider, { initialLocale: locale }, e(LanguageSwitcher), e(Component)));
}
function change(locale) { fireEvent.change(screen.getByLabelText(/^(Language|Ngôn ngữ)$/), { target: { value: locale } }); }
beforeEach(() => {
  cleanup(); setParams({ id: collectionId }); setSearchParams();
  api = fixtureApi(); requests = []; queue = { totalDue: 23, items: [card] }; queueError = false; failGrade = false;
  document.cookie = "XSRF-TOKEN=scoped-csrf; Path=/";
  globalThis.fetch = async (url, options = {}) => {
    const parsed = new URL(url, "http://localhost");
    requests.push({ url: parsed, method: options.method ?? "GET", payload: options.body ? JSON.parse(options.body) : null, headers: options.headers });
    if (parsed.pathname.endsWith("/review/queue")) {
      return new Response(JSON.stringify(queueError ? { code: "NOT_FOUND", message: "RAW_PRIVATE_ERROR" } : queue), { status: queueError ? 404 : 200 });
    }
    if (parsed.pathname.endsWith("/review/grade") && failGrade) throw new Error("uncertain response");
    const result = api.respond(url, options);
    return new Response(result.status === 204 ? null : JSON.stringify(result.body), { status: result.status });
  };
});
afterEach(() => { cleanup(); globalThis.fetch = originalFetch; Date.now = originalNow; });
const queues = () => requests.filter(r => r.url.pathname.endsWith("/review/queue"));
const grades = () => requests.filter(r => r.url.pathname.endsWith("/review/grade"));
describe("M3-EVO-3A production collection-scoped review", () => {
  for (const locale of ["vi-VN", "en"]) {
    it(locale + ": global queue stays unscoped and uses the API total", async () => {
      mount(locale); await screen.findByTestId("reveal-btn");
      assert.equal(queues()[0].url.searchParams.has("collectionId"), false);
      assert.equal(screen.getByTestId("review-scope").textContent, translate(locale, "learnReviewAll"));
      assert.equal(screen.getByTestId("review-due-count").textContent, translate(locale, "learnDueAtQueueLoad", { count: 23 }));
    });
    it(locale + ": scoped queue forwards the exact ID and preserves scope on refresh/completion", async () => {
      setSearchParams({ collectionId }); mount(locale); await screen.findByTestId("reveal-btn");
      assert.equal(queues()[0].url.searchParams.get("collectionId"), collectionId);
      assert.equal(queues()[0].url.searchParams.get("limit"), "50");
      assert.equal(screen.getByTestId("review-scope").textContent, translate(locale, "learnReviewStudySet"));
      assert.equal(screen.getByRole("link", { name: translate(locale, "learnBackToStudySet") }).getAttribute("href"), "/collections/" + collectionId);
      fireEvent.click(screen.getByTestId("reveal-btn")); fireEvent.click(screen.getByTestId("grade-good-btn"));
      await screen.findByTestId("review-complete");
      assert.equal(grades()[0].payload.collectionId, undefined); // submission contract stays unchanged
      queue = { totalDue: 0, items: [] };
      fireEvent.click(screen.getByRole("button", { name: translate(locale, "learnCheckDueQueue") }));
      await screen.findByTestId("review-empty");
      assert.equal(queues().at(-1).url.searchParams.get("collectionId"), collectionId);
      assert.ok(screen.getByText(translate(locale, "learnNoStudySetVocabularyDue")));
    });
    it(locale + ": Collection detail links directly to the exact review scope", async () => {
      mount(locale, Detail);
      const link = await screen.findByRole("link", { name: translate(locale, "learnReviewStudySet") });
      assert.equal(link.getAttribute("href"), "/review?collectionId=" + collectionId);
    });
    it(locale + ": focused return link keeps native Enter and canvas shortcut still reveals", async () => {
      setSearchParams({ collectionId });
      await act(async () => { mount(locale); });
      await screen.findByTestId("reveal-btn");
      const link = screen.getByRole("link", { name: translate(locale, "learnBackToStudySet") });
      link.focus();
      const event = new window.KeyboardEvent("keydown", { key: "Enter", code: "Enter", bubbles: true, cancelable: true });
      fireEvent(link, event);
      assert.equal(event.defaultPrevented, false, "Review must not cancel native link activation");
      assert.ok(screen.queryByTestId("revealed-content") === null);
      assert.equal(link.getAttribute("href"), "/collections/" + collectionId);
      link.blur();
      fireEvent.keyDown(window, { key: " ", code: "Space" });
      assert.ok(screen.getByTestId("revealed-content"));
    });
    it(locale + ": empty scoped queue keeps context and back navigation", async () => {
      queue = { totalDue: 0, items: [] }; setSearchParams({ collectionId }); mount(locale);
      await screen.findByTestId("review-empty");
      assert.ok(screen.getByText(translate(locale, "learnNoStudySetVocabularyDue")));
      assert.ok(screen.getByRole("link", { name: translate(locale, "learnBackToStudySet") }));
    });
    it(locale + ": inaccessible scope shows a safe error and retry stays scoped", async () => {
      queueError = true; setSearchParams({ collectionId }); mount(locale);
      await screen.findByText(translate(locale, "learnStudySetUnavailable"));
      assert.ok(!document.body.textContent.includes("RAW_PRIVATE_ERROR"));
      queueError = false; fireEvent.click(screen.getByRole("button", { name: translate(locale, "learnTryAgain") }));
      await screen.findByTestId("reveal-btn");
      assert.equal(queues().at(-1).url.searchParams.get("collectionId"), collectionId);
    });
    it(locale + ": language switch and delayed retry preserve the entire attempt payload", async () => {
      let now = 100000; Date.now = () => now;
      failGrade = true; setSearchParams({ collectionId }); mount(locale);
      await screen.findByTestId("reveal-btn");
      fireEvent.click(screen.getByTestId("reveal-btn"));
      fireEvent.click(screen.getByTestId("grade-good-btn")); await screen.findByTestId("submit-error");
      const first = structuredClone(grades()[0].payload);
      const other = locale === "en" ? "vi-VN" : "en";
      change(other); now += 17000;
      assert.ok(screen.getByTestId("revealed-content"));
      assert.equal(queues().length, 1);
      assert.equal(screen.getByTestId("grade-easy-btn").disabled, true);
      failGrade = false; fireEvent.click(screen.getByTestId("retry-submit-btn"));
      await screen.findByTestId("review-complete");
      assert.deepEqual(grades()[1].payload, first);
      assert.equal(new Headers(grades()[1].headers).get("X-XSRF-TOKEN"), "scoped-csrf");
      assert.equal(screen.getByTestId("review-scope").textContent, translate(other, "learnReviewStudySet"));
    });
  }
  it("changing collection scope reloads the queue without reusing a previous card attempt", async () => {
    setSearchParams({ collectionId }); const view = mount(); await screen.findByTestId("reveal-btn");
    fireEvent.click(screen.getByTestId("reveal-btn")); failGrade = true;
    fireEvent.click(screen.getByTestId("grade-good-btn")); await screen.findByTestId("submit-error");
    const firstId = grades()[0].payload.attemptId;
    setSearchParams({ collectionId: otherId });
    view.rerender(e(LocaleProvider, { initialLocale: "en" }, e(LanguageSwitcher), e(Review)));
    await screen.findByTestId("reveal-btn"); failGrade = false;
    fireEvent.click(screen.getByTestId("reveal-btn")); fireEvent.click(screen.getByTestId("grade-good-btn"));
    await screen.findByTestId("review-complete");
    assert.equal(queues().at(-1).url.searchParams.get("collectionId"), otherId);
    assert.notEqual(grades().at(-1).payload.attemptId, firstId);
  });
  it("blank and repeated scope parameters never silently request a global queue", async () => {
    for (const query of ["collectionId=", "collectionId=" + collectionId + "&collectionId=" + otherId]) {
      setSearchParams(query); mount(); await screen.findByTestId("reveal-btn");
      assert.equal(queues().at(-1).url.searchParams.has("collectionId"), true);
      cleanup();
    }
  });
  it("a late queue response from an earlier scope cannot replace the current scope", async () => {
    let resolveOld;
    const fetch = globalThis.fetch;
    globalThis.fetch = (url, options) => new URL(url, "http://localhost").searchParams.get("collectionId") === collectionId
      ? new Promise(resolve => { resolveOld = resolve; }) : fetch(url, options);
    setSearchParams({ collectionId }); const view = mount();
    await waitFor(() => assert.equal(typeof resolveOld, "function"));
    setSearchParams({ collectionId: otherId });
    view.rerender(e(LocaleProvider, { initialLocale: "en" }, e(LanguageSwitcher), e(Review)));
    await screen.findByTestId("reveal-btn");
    await act(async () => resolveOld(new Response(JSON.stringify({ totalDue: 0, items: [] }))));
    assert.ok(screen.getByTestId("reveal-btn"));
    assert.equal(screen.queryByTestId("review-empty"), null);
  });
});
