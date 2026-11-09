import "./setup-dom.mjs";
import "./production-loader.mjs";
import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { render, screen, within, fireEvent, cleanup, waitFor, act } from "@testing-library/react";
import { fixtureApi, word, lesson, questions } from "./learning-fixtures.mjs";
import { setParams } from "./production-next-stubs.mjs";
for (const name of ["Element", "Node", "Document", "ShadowRoot", "HTMLDivElement", "FocusEvent", "MouseEvent", "KeyboardEvent", "HTMLMediaElement"]) globalThis[name] = window[name];
globalThis.getComputedStyle = window.getComputedStyle.bind(window);
globalThis.requestAnimationFrame = window.requestAnimationFrame.bind(window);
globalThis.cancelAnimationFrame = window.cancelAnimationFrame.bind(window);
const { LocaleProvider } = await import("../i18n/locale-provider.tsx");
const { LanguageSwitcher } = await import("../../components/auth/language-switcher.tsx");
const { translate, dictionaries } = await import("../i18n/dictionaries.ts");
const { learningEn, learningVi } = await import("../i18n/learning-dictionaries.ts");
const { learningErrorKey } = await import("../i18n/learning-errors.ts");
const { ApiError } = await import("../api/client.ts");
const modules = {};
for (const [name, path] of Object.entries({ Vocabulary: "(workspace)/vocabulary", Detail: "(workspace)/vocabulary/[id]", Collections: "(workspace)/collections", SetDetail: "(workspace)/collections/[id]", Import: "(workspace)/vocabulary/import", Review: "(focus)/review", Dictation: "(focus)/dictation", Shadowing: "(focus)/shadowing", Quiz: "(focus)/quiz" })) modules[name] = (await import(`../../app/${path}/page.tsx`)).default;
const { QuickAddDialog } = await import("../../components/vocabulary/quick-add-dialog.tsx");
let api;
const originalFetch = globalThis.fetch;
const e = React.createElement;
function mount(name, locale, props = {}) { return render(e(LocaleProvider, { initialLocale: locale }, e(LanguageSwitcher), e(name === "QuickAdd" ? QuickAddDialog : modules[name], props))); }
function change(locale) { const switches = screen.getAllByLabelText(/^(Language|Ngôn ngữ)$/); const target = switches.find(el => el.closest("[role=dialog]")) ?? switches[0]; fireEvent.change(target, { target: { value: locale } }); }
function text(locale, key) { return translate(locale, key); }
const selectors = { Vocabulary: "learnVocabularyLibrary", Detail: "learnContextExamples", Collections: "learnCollectionsDecks", SetDetail: "learnAddWords", Import: "learnImportVocabulary", Review: "learnRevealAnswer", Dictation: "learnDailyDictation", Shadowing: "learnSelectShadowingExercise", Quiz: "learnDiagnosticQuizzes" };
async function ready(name, locale) { await screen.findAllByText(text(locale, selectors[name])); }

beforeEach(() => {
 cleanup(); setParams({ id: "fixture-id" }); api = fixtureApi();
 document.cookie = "XSRF-TOKEN=component-csrf; Path=/";
 globalThis.fetch = async (url, options = {}) => { const result = api.respond(url, options); return new Response(result.status === 204 ? null : JSON.stringify(result.body), { status: result.status }); };
 window.HTMLMediaElement.prototype.pause = () => {};
 window.HTMLMediaElement.prototype.play = () => Promise.resolve();
 Object.defineProperty(navigator, "mediaDevices", { configurable: true, value: { getUserMedia: async () => { throw new DOMException("fixture denial", "NotAllowedError"); } } });
 window.MediaRecorder = class {}; globalThis.MediaRecorder = window.MediaRecorder;
});
afterEach(() => { cleanup(); globalThis.fetch = originalFetch; });

describe("M3-EVO-1C-2 production learning module localization", () => {
 for (const locale of ["vi-VN", "en"]) {
  for (const name of Object.keys(selectors)) it(`${locale}: ${name} renders localized chrome and preserves learning content`, async () => {
   mount(name, locale); await ready(name, locale);
   assert.ok(!document.body.textContent.includes("RAW_PRIVATE_SERVER_EXCEPTION"));
   if (["Vocabulary", "Detail", "SetDetail", "Review"].includes(name)) await screen.findAllByText(word.term);
   if (["Dictation", "Shadowing"].includes(name)) await screen.findByText(lesson.title);
  });
  it(`${locale}: vocabulary validation, optional values, draft and errors survive language switching`, async () => {
   mount("QuickAdd", locale, { open: true, onOpenChange: () => {} });
   const dialog = within(await screen.findByRole("dialog"));
   fireEvent.click(dialog.getByRole("button", { name: text(locale, "learnSaveWord") }));
   await dialog.findByText(text(locale, "learnWordOrPhraseIsRequired"));
   assert.equal(dialog.getByLabelText(/^(Word \/ Phrase|Từ \/ Cụm từ)/).getAttribute("aria-invalid"), "true");
   fireEvent.change(document.getElementById("quick-add-term"), { target: { value: "resilience" } });
   fireEvent.change(document.getElementById("quick-add-meaning"), { target: { value: "sự kiên cường" } });
   const disclosure = document.querySelector("details"); disclosure.open = true;
   fireEvent.change(document.getElementById("quick-add-definition"), { target: { value: "Keep this English definition." } });
   disclosure.open = false;
   const other = locale === "en" ? "vi-VN" : "en"; change(other);
   assert.equal(document.getElementById("quick-add-term").value, "resilience");
   assert.equal(document.getElementById("quick-add-definition").value, "Keep this English definition.");
   fireEvent.click(dialog.getByRole("button", { name: text(other, "learnSaveWord") }));
   await waitFor(() => assert.ok(api.requests.some(r => r.path === "/api/v1/vocabulary" && r.method === "POST")));
   const request = api.requests.find(r => r.path === "/api/v1/vocabulary" && r.method === "POST");
   assert.equal(request.payload.englishDefinition, "Keep this English definition.");
   assert.equal(new Headers(request.headers).get("X-XSRF-TOKEN"), "component-csrf");
  });
  it(`${locale}: edit keeps original optional learning content and drafts across locale changes`, async () => {
   mount("Detail", locale); await ready("Detail", locale);
   fireEvent.click(screen.getByRole("button", { name: text(locale, "learnEdit"), exact: true }));
   const term = document.getElementById("edit-term");
   fireEvent.change(term, { target: { value: "perseverance" } });
   const other = locale === "en" ? "vi-VN" : "en"; change(other);
   assert.equal(term.value, "perseverance");
   fireEvent.click(screen.getByRole("button", { name: text(other, "learnSaveChanges") }));
   await waitFor(() => assert.ok(api.requests.some(r => r.path === "/api/v1/vocabulary/fixture-id" && r.method === "PUT")));
   const request = api.requests.find(r => r.path === "/api/v1/vocabulary/fixture-id" && r.method === "PUT");
   assert.equal(request.payload.term, "perseverance"); assert.equal(request.payload.exampleTranslation, word.exampleTranslation);
   assert.equal(request.payload.notes, word.notes); assert.equal(request.payload.englishDefinition, word.englishDefinition);
  });
  it(`${locale}: review shortcut ignores the language select`, async () => {
   mount("Review", locale); await ready("Review", locale);
   fireEvent.keyDown(screen.getByLabelText(/^(Language|Ngôn ngữ)$/), { key: "Enter", code: "Enter" });
   assert.equal(screen.queryByTestId("revealed-content"), null);
   fireEvent.keyDown(window, { key: " ", code: "Space" }); assert.ok(screen.getByTestId("revealed-content"));
  });
  it(`${locale}: Collections validates, preserves a draft and maps duplicate errors safely`, async () => {
   mount("Collections", locale); await ready("Collections", locale);
   fireEvent.click(screen.getByRole("button", { name: text(locale, "learnCreateCollection") }));
   const dialog = within(await screen.findByRole("dialog"));
   fireEvent.submit(dialog.getByRole("button", { name: text(locale, "learnCreateCollection") }).closest("form"));
   await dialog.findByText(text(locale, "learnCollectionTitleIsRequired"));
   const title = dialog.getByLabelText(text(locale, "learnCollectionTitle"));
   fireEvent.change(title, { target: { value: "My new study set" } });
   const other = locale === "en" ? "vi-VN" : "en"; change(other); assert.equal(title.value, "My new study set");
   api.failures.set("/api/v1/collections", "TITLE_ALREADY_EXISTS");
   fireEvent.submit(title.closest("form"));
   await dialog.findByText(text(other, "learnACollectionWithThisTitleAlreadyExists"));
   assert.ok(!document.body.textContent.includes("RAW_PRIVATE"));
  });
  it(`${locale}: import previews invalid and duplicate rows safely, confirms unchanged items and policy`, async () => {
   mount("Import", locale); await ready("Import", locale);
   const file = new File(["term,vietnameseMeaning\nresilience,sự kiên cường"], "words.csv", { type: "text/csv" });
   fireEvent.change(document.querySelector('input[type="file"]'), { target: { files: [file] } });
   await screen.findByText(text(locale, "learnReadyToImport"));
   assert.ok(!document.body.textContent.includes("RAW_PRIVATE"));
   fireEvent.click(screen.getByRole("button", { name: new RegExp(text(locale, "learnConfirmImport").replace(/[()]/g, "\\$&")) }));
   await screen.findByText(text(locale, "learnVocabularyImportComplete"));
   const request = api.requests.find(r => r.path.endsWith("/confirm"));
   assert.equal(request.payload.items.length, 2); assert.equal(request.payload.duplicatePolicy, "SKIP");
   assert.equal(request.payload.items[0].englishDefinition, word.englishDefinition);
  });
  it(`${locale}: review reveal, safe failure, language switch and retry retain attempt id and grade`, async () => {
   mount("Review", locale); await ready("Review", locale);
   fireEvent.keyDown(window, { code: "Space", key: " " });
   await screen.findByTestId("revealed-content");
   api.failures.set("/api/v1/review/grade", "UNKNOWN_ERROR");
   fireEvent.click(screen.getByTestId("grade-good-btn")); await screen.findByTestId("submit-error");
   const first = api.requests.find(r => r.path.endsWith("/review/grade"));
   const other = locale === "en" ? "vi-VN" : "en"; change(other);
   assert.ok(screen.getByTestId("revealed-content")); assert.equal(screen.getByTestId("card-term").textContent, word.term);
   api.failures.clear(); fireEvent.click(screen.getByTestId("retry-submit-btn"));
   await screen.findByTestId("review-complete");
   const attempts = api.requests.filter(r => r.path.endsWith("/review/grade"));
   assert.equal(attempts[1].payload.attemptId, first.payload.attemptId); assert.equal(attempts[1].payload.grade, "GOOD");
   assert.ok(!document.body.textContent.includes("RAW_PRIVATE"));
  });
  it(`${locale}: dictation draft survives switching and reference/scoring data stay untouched`, async () => {
   mount("Dictation", locale); await ready("Dictation", locale);
   fireEvent.click(await screen.findByTestId("exercise-card-0"));
   const input = await screen.findByRole("textbox"); fireEvent.change(input, { target: { value: "I go for a walk" } });
   const other = locale === "en" ? "vi-VN" : "en"; change(other); assert.equal(input.value, "I go for a walk");
   fireEvent.submit(input.closest("form"));
   await screen.findByText(lesson.transcriptText, { exact: false });
   assert.equal(api.requests.find(r => r.path.endsWith("/submit")).payload.submittedTranscript, "I go for a walk");
   assert.ok(document.body.textContent.includes("80"));
  });
  it(`${locale}: microphone denial is understandable and translates without resetting exercise`, async () => {
   mount("Shadowing", locale); await ready("Shadowing", locale);
   fireEvent.click(await screen.findByTestId(`exercise-card-${lesson.contentId}`));
   await screen.findByText(lesson.transcriptText, { exact: false });
   fireEvent.click(screen.getByRole("button", { name: text(locale, "learnStartRecording") }));
   await screen.findByText(text(locale, "learnMicrophonePermissionRequired"));
   const other = locale === "en" ? "vi-VN" : "en"; change(other);
   await screen.findByText(text(other, "learnMicrophonePermissionRequired"));
   assert.ok(screen.getByText(lesson.transcriptText, { exact: false }));
  });
  it(`${locale}: recording, self-rating, cleanup and completion survive a locale change`, async () => {
   let stopped = 0; const revoked = [];
   navigator.mediaDevices.getUserMedia = async () => ({ getTracks: () => [{ stop: () => { stopped++; } }] });
   class Recorder {
    static isTypeSupported() { return true; }
    state = "inactive"; mimeType = "audio/webm";
    start() { this.state = "recording"; }
    stop() { this.state = "inactive"; this.ondataavailable?.({ data: new Blob(["fixture audio"]) }); this.onstop?.(); }
   }
   window.MediaRecorder = Recorder; globalThis.MediaRecorder = Recorder;
   const create = URL.createObjectURL, revoke = URL.revokeObjectURL;
   URL.createObjectURL = () => "blob:fixture-recording"; URL.revokeObjectURL = url => revoked.push(url);
   try {
    const view = mount("Shadowing", locale); await ready("Shadowing", locale);
    fireEvent.click(await screen.findByTestId(`exercise-card-${lesson.contentId}`));
    const recordButton = await screen.findByRole("button", { name: text(locale, "learnStartRecording") });
    await act(async () => { fireEvent.click(recordButton); });
    await screen.findByRole("button", { name: text(locale, "learnStopRecording") });
    const other = locale === "en" ? "vi-VN" : "en"; change(other);
    await act(async () => { await new Promise(resolve => setTimeout(resolve, 1100)); });
    fireEvent.click(await screen.findByRole("button", { name: text(other, "learnStopRecording") }));
    await screen.findByRole("button", { name: text(other, "learnPlayTake") });
    fireEvent.click(screen.getByRole("button", { name: new RegExp(text(other, "learnSelfGood")) }));
    fireEvent.click(screen.getByRole("button", { name: text(other, "learnCompletePractice") }));
    await screen.findByText(text(other, "learnPracticeTakeCompleted"));
    const request = api.requests.find(r => r.path.endsWith("/complete"));
    assert.equal(request.payload.selfRating, "GOOD"); assert.ok(request.payload.attemptId);
    assert.ok(!Object.keys(request.payload).some(k => /blob|file|audio/i.test(k)));
    assert.ok(stopped > 0); view.unmount(); assert.ok(revoked.includes("blob:fixture-recording"));
   } finally { URL.createObjectURL = create; URL.revokeObjectURL = revoke; }
  });
  it(`${locale}: import rejects unsupported files with localized guidance before any request`, async () => {
   mount("Import", locale); await ready("Import", locale);
   fireEvent.change(document.querySelector('input[type="file"]'), { target: { files: [new File(["no"], "wrong.pdf")] } });
   await screen.findByText(text(locale, "learnPleaseSelectACsvOrTsvFile"));
   assert.ok(!api.requests.some(r => r.path.endsWith("/preview")));
  });
  it(`${locale}: quiz answers, question position, final payload and English questions survive switching`, async () => {
   mount("Quiz", locale); await ready("Quiz", locale);
   fireEvent.click(await screen.findByRole("button", { name: text(locale, "learnStartDiagnosticQuiz") }));
   await screen.findByText(questions[0].prompt);
   fireEvent.click(screen.getByRole("button", { name: /resilience/ }));
   fireEvent.click(screen.getByRole("button", { name: text(locale, "learnNextQuestion") }));
   const input = await screen.findByRole("textbox"); fireEvent.change(input, { target: { value: "walk" } });
   const other = locale === "en" ? "vi-VN" : "en"; change(other);
   assert.equal(input.value, "walk"); assert.ok(screen.getByText(questions[1].prompt));
   fireEvent.click(screen.getByRole("button", { name: text(other, "learnReviewSubmit") }));
   const dialog = within(await screen.findByRole("dialog"));
   assert.ok(dialog.getByRole("heading", { name: text(other, "learnConfirmSubmission") }));
   fireEvent.click(dialog.getByRole("button", { name: text(other, "learnSubmitQuiz") }));
   await screen.findByText(text(other, "learnDiagnosticScorecard"));
   const request = api.requests.find(r => r.path.endsWith("/complete"));
   assert.deepEqual(request.payload.answers.map(a => a.selectedAnswer), ["resilience", "walk"]);
   assert.ok(screen.getAllByText("This is the English explanation.").length);
  });
 }
 it("learning dictionaries have key/parameter parity and English fallback", () => {
  assert.deepEqual(Object.keys(learningVi).sort(), Object.keys(learningEn).sort());
  for (const [key, value] of Object.entries(learningEn)) assert.deepEqual([...learningVi[key].matchAll(/\{(\w+)\}/g)].map(m=>m[1]).sort(), [...value.matchAll(/\{(\w+)\}/g)].map(m=>m[1]).sort(), key);
  const key = "learnRevealAnswer", value = dictionaries["vi-VN"][key];
  try { delete dictionaries["vi-VN"][key]; assert.equal(translate("vi-VN", key), learningEn[key]); } finally { dictionaries["vi-VN"][key] = value; }
  assert.equal(translate("vi-VN", "UNKNOWN_INTERNAL_KEY"), translate("vi-VN", "authFallback"));
 });
 it("unknown errors never expose exceptions or API codes", () => {
  assert.equal(learningErrorKey(new Error("RAW_PRIVATE_SERVER_EXCEPTION")), "requestFailed");
  assert.equal(learningErrorKey(new ApiError("RAW_PRIVATE", 409, "TITLE_ALREADY_EXISTS")), "learnACollectionWithThisTitleAlreadyExists");
 });
 it("empty states remain localized", async () => {
  api.overrides.set("/api/v1/review/queue", { items: [], totalDue: 0 }); mount("Review", "vi-VN");
  await screen.findByTestId("review-empty"); assert.ok(screen.getByText(text("vi-VN", "learnAllCaughtUp")));
 });
 it("loading state is visible while the queue is pending", async () => {
  globalThis.fetch = () => new Promise(() => {}); mount("Review", "vi-VN");
  assert.ok(screen.getByText(text("vi-VN", "learnLoadingReviewQueue")));
 });
 it("list errors are safe and localized", async () => {
  api.failures.set("/api/v1/dictation", "UNKNOWN_ERROR"); mount("Dictation", "vi-VN");
  await screen.findByText(text("vi-VN", "learnFailedToLoadDictationExercises"));
  assert.ok(!document.body.textContent.includes("RAW_PRIVATE"));
 });
});
