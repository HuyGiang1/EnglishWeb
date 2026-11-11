import "./setup-dom.mjs";
import "./production-loader.mjs";
import { beforeEach, afterEach, describe, it } from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { render, screen, fireEvent, within, waitFor, cleanup } from "@testing-library/react";
import { fixtureApi, collection, page } from "./learning-fixtures.mjs";
import { setParams } from "./production-next-stubs.mjs";
for (const name of ["Element", "Node", "Document", "ShadowRoot", "HTMLDivElement", "FocusEvent", "MouseEvent", "KeyboardEvent"]) globalThis[name] = window[name];
globalThis.getComputedStyle = window.getComputedStyle.bind(window);
globalThis.requestAnimationFrame = window.requestAnimationFrame.bind(window);
globalThis.cancelAnimationFrame = window.cancelAnimationFrame.bind(window);
const { LocaleProvider } = await import("../i18n/locale-provider.tsx");
const { LanguageSwitcher } = await import("../../components/auth/language-switcher.tsx");
const { translate } = await import("../i18n/dictionaries.ts");
const Page = (await import("../../app/(workspace)/collections/page.tsx")).default;
const e = React.createElement;
const originalFetch = globalThis.fetch;
let folders, sets, requests, failures;
const folderA = "folder-a", folderB = "folder-b";
const text = (locale, key, params) => translate(locale, key, params);
function mount(locale) { return render(e(LocaleProvider, { initialLocale: locale }, e(LanguageSwitcher), e(Page))); }
function change(locale) {
  const options = screen.getAllByLabelText(/^(Language|Ngôn ngữ)$/);
  const select = options.find(el => el.closest("[role=dialog]")) ?? options[0];
  fireEvent.change(select, { target: { value: locale } });
}
function respond(status, body) { return new Response(status === 204 ? null : JSON.stringify(body), { status }); }
beforeEach(() => {
  cleanup(); setParams({ id: "set-one" });
  folders = [{ id: folderA, name: "Travel", description: "Original folder text" }, { id: folderB, name: "Empty folder", description: null }];
  sets = [{ ...collection, id: "set-one", title: "Travel words", folderId: folderA }, { ...collection, id: "set-two", title: "Legacy words", folderId: null }];
  requests = []; failures = new Map(); const api = fixtureApi();
  document.cookie = "XSRF-TOKEN=folder-csrf; Path=/";
  globalThis.fetch = async (input, options = {}) => {
    const url = new URL(input, "http://localhost"), method = options.method ?? "GET", payload = options.body ? JSON.parse(options.body) : null;
    requests.push({ path: url.pathname, query: url.searchParams, method, payload, headers: options.headers });
    const failure = failures.get(method + " " + url.pathname);
    if (failure) return respond(failure.status, { code: failure.code, message: "RAW_PRIVATE_FIXTURE_ERROR" });
    if (url.pathname === "/api/v1/folders" && method === "GET") {
      const rows = folders.map(f => ({ ...f, studySetCount: sets.filter(s => s.folderId === f.id).length }));
      const number = Number(url.searchParams.get("page") ?? 0), size = 20;
      return respond(200, { content: rows.slice(number * size, (number + 1) * size), page: number, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) });
    }
    if (url.pathname === "/api/v1/folders" && method === "POST") {
      const result = { ...payload, id: "created-folder" }; folders.push(result); return respond(201, { ...result, studySetCount: 0 });
    }
    if (/\/folders\/[^/]+$/.test(url.pathname)) {
      const id = url.pathname.split("/").at(-1);
      if (method === "PUT") { const f = folders.find(f => f.id === id); Object.assign(f, payload); return respond(200, f); }
      if (method === "DELETE") { folders = folders.filter(f => f.id !== id); sets.forEach(s => { if (s.folderId === id) s.folderId = null; }); return respond(204); }
    }
    if (url.pathname === "/api/v1/collections" && method === "GET") {
      const rows = sets.filter(s => url.searchParams.has("folderId") ? s.folderId === url.searchParams.get("folderId") : url.searchParams.get("unfiled") === "true" ? !s.folderId : true);
      return respond(200, page(rows));
    }
    if (/\/collections\/[^/]+\/folder$/.test(url.pathname) && method === "PUT") {
      const item = sets.find(s => s.id === url.pathname.split("/").at(-2)); item.folderId = payload.folderId; return respond(200, item);
    }
    if (url.pathname === "/api/v1/collections" && method === "POST") { const item = { ...collection, ...payload, id: "new-set" }; sets.push(item); return respond(201, item); }
    const result = api.respond(input, options); return respond(result.status, result.body);
  };
});
afterEach(() => { cleanup(); globalThis.fetch = originalFetch; });
async function ready() { await screen.findByText("Travel words"); await screen.findByRole("button", { name: /^Travel/ }); }
describe("M3-EVO-3B production Folder organization", () => {
  for (const locale of ["vi-VN", "en"]) {
    it(locale + ": shows own folders, counts, empty Folder and virtual Unfiled", async () => {
      mount(locale); await ready();
      assert.ok(screen.getByRole("heading", { name: text(locale, "folderHeading") }));
      assert.ok(screen.getByRole("button", { name: /^Empty folder/ }));
      fireEvent.click(screen.getByRole("button", { name: text(locale, "folderUnfiled"), exact: true }));
      assert.ok(await screen.findByText("Legacy words"));
      assert.ok(screen.queryByText("Travel words") === null);
      assert.equal(requests.filter(r => r.path === "/api/v1/collections").at(-1).query.get("unfiled"), "true");
      assert.ok(!requests.some(r => r.method === "POST"));
    });
    it(locale + ": selecting a Folder requests only its Study Sets and empty state is clear", async () => {
      mount(locale); await ready(); fireEvent.click(screen.getByRole("button", { name: /^Empty folder/ }));
      await screen.findByText(text(locale, "folderEmpty"));
      assert.equal(requests.filter(r => r.path === "/api/v1/collections").at(-1).query.get("folderId"), folderB);
      fireEvent.click(screen.getByRole("button", { name: /^Travel/ }));
      await screen.findByText("Travel words"); assert.ok(screen.queryByText("Legacy words") === null);
      assert.equal(screen.getByRole("link", { name: "Travel words" }).getAttribute("href"), "/collections/set-one");
    });
    it(locale + ": creates normalized Folder, preserving drafts and focus across locale switch", async () => {
      mount(locale); await ready(); fireEvent.click(screen.getByRole("button", { name: text(locale, "folderCreate") }));
      const dialog = await screen.findByRole("dialog");
      await waitFor(() => assert.ok(dialog.contains(document.activeElement)));
      fireEvent.change(within(dialog).getByLabelText(text(locale, "folderName")), { target: { value: "  New   Folder  " } });
      fireEvent.change(within(dialog).getByLabelText(text(locale, "learnDescriptionOptional")), { target: { value: "Keep original user text" } });
      const other = locale === "en" ? "vi-VN" : "en"; change(other);
      assert.equal(document.getElementById("folder-name").value, "  New   Folder  ");
      assert.equal(document.getElementById("folder-description").value, "Keep original user text");
      fireEvent.submit(dialog.querySelector("form"));
      await screen.findByRole("button", { name: /^New Folder/ });
      const request = requests.find(r => r.path === "/api/v1/folders" && r.method === "POST");
      assert.deepEqual(request.payload, { name: "New Folder", description: "Keep original user text" });
      assert.equal(new Headers(request.headers).get("X-XSRF-TOKEN"), "folder-csrf");
    });
    it(locale + ": invalid name and duplicate errors preserve input without leaking server text", async () => {
      mount(locale); await ready(); fireEvent.click(screen.getByRole("button", { name: text(locale, "folderCreate") }));
      const dialog = await screen.findByRole("dialog");
      fireEvent.submit(dialog.querySelector("form")); await within(dialog).findByText(text(locale, "folderInvalidName"));
      fireEvent.change(document.getElementById("folder-name"), { target: { value: "Travel" } });
      failures.set("POST /api/v1/folders", { status: 409, code: "FOLDER_NAME_EXISTS" });
      fireEvent.submit(dialog.querySelector("form")); await within(dialog).findByText(text(locale, "folderNameConflict"));
      assert.equal(document.getElementById("folder-name").value, "Travel"); assert.ok(!document.body.textContent.includes("RAW_PRIVATE"));
    });
    it(locale + ": renames a Folder without translating its name", async () => {
      mount(locale); await ready(); fireEvent.click(screen.getByRole("button", { name: text(locale, "folderRenameNamed", { name: "Travel" }) }));
      const dialog = await screen.findByRole("dialog");
      fireEvent.change(document.getElementById("folder-name"), { target: { value: "Bộ từ riêng" } });
      fireEvent.submit(dialog.querySelector("form")); await screen.findByRole("button", { name: /^Bộ từ riêng/ });
      assert.equal(requests.find(r => r.method === "PUT").payload.name, "Bộ từ riêng");
    });
    it(locale + ": delete confirmation explains preservation and moves surviving Study Sets to Unfiled", async () => {
      mount(locale); await ready(); fireEvent.click(screen.getByRole("button", { name: text(locale, "folderDeleteNamed", { name: "Travel" }) }));
      const dialog = await screen.findByRole("dialog");
      assert.ok(within(dialog).getByText(text(locale, "folderDeleteExplanation")));
      fireEvent.click(within(dialog).getByRole("button", { name: text(locale, "folderDelete"), exact: true }));
      await waitFor(() => assert.ok(screen.queryByRole("dialog") === null));
      await screen.findByText("Travel words");
      assert.equal(sets.find(s => s.id === "set-one").folderId, null);
      assert.ok(screen.getByRole("button", { name: text(locale, "folderUnfiled"), exact: true }).getAttribute("aria-pressed") === "true");
    });
    it(locale + ": move and unassignment send explicit typed bodies", async () => {
      mount(locale); await ready();
      for (const dest of [folderB, ""]) {
        fireEvent.click(screen.getByRole("button", { name: text(locale, "folderMoveNamed", { name: "Travel words" }) }));
        const dialog = await screen.findByRole("dialog"), select = within(dialog).getByLabelText(text(locale, "folderDestination"));
        await waitFor(() => assert.equal(select.disabled, false));
        fireEvent.change(select, { target: { value: dest } });
        fireEvent.submit(dialog.querySelector("form"));
        await waitFor(() => assert.ok(screen.queryByRole("dialog") === null));
        assert.equal(requests.filter(r => r.path.endsWith("/folder")).at(-1).payload.folderId, dest || null);
      }
    });
    it(locale + ": invalid move destination returns safe error and keeps the dialog", async () => {
      mount(locale); await ready();
      fireEvent.click(screen.getByRole("button", { name: text(locale, "folderMoveNamed", { name: "Travel words" }) }));
      const dialog = await screen.findByRole("dialog"); await waitFor(() => assert.equal(document.getElementById("folder-destination").disabled, false));
      failures.set("PUT /api/v1/collections/set-one/folder", { status: 404, code: "NOT_FOUND" });
      fireEvent.submit(dialog.querySelector("form")); await within(dialog).findByText(text(locale, "folderUnavailable"));
      assert.equal(sets[0].folderId, folderA); assert.ok(!document.body.textContent.includes("RAW_PRIVATE"));
    });
    it(locale + ": Folder API failure keeps existing Study Sets and detail links visible", async () => {
      failures.set("GET /api/v1/folders", { status: 500, code: "UNKNOWN" });
      mount(locale); await screen.findByText("Travel words");
      await screen.findByText(text(locale, "folderRequestFailed"));
      assert.ok(screen.getByRole("link", { name: "Legacy words" }));
    });
    it(locale + ": no Folders and no Study Sets render explicit empty states", async () => {
      folders = []; sets = []; mount(locale);
      await screen.findByText(text(locale, "folderNone"));
      await screen.findByText(text(locale, "learnNoCollectionsYet"));
    });
  }
  it("Folder pager and move selector can reach destinations beyond the first bounded page", async () => {
    for (let i = 0; i < 22; i++) folders.push({ id: "extra-" + i, name: "Extra " + i });
    mount("en"); await ready();
    fireEvent.click(screen.getByRole("button", { name: "Next page" }));
    await screen.findByRole("button", { name: /^Extra 21/ });
    fireEvent.click(screen.getByRole("button", { name: text("en", "folderMoveNamed", { name: "Travel words" }) }));
    const dialog = await screen.findByRole("dialog");
    await waitFor(() => assert.equal(document.getElementById("folder-destination").disabled, false));
    fireEvent.click(within(dialog).getByRole("button", { name: "Next page" }));
    await within(dialog).findByRole("option", { name: "Extra 21" });
    fireEvent.change(document.getElementById("folder-destination"), { target: { value: "extra-21" } });
    fireEvent.submit(dialog.querySelector("form")); await waitFor(() => assert.ok(screen.queryByRole("dialog") === null));
    assert.equal(sets[0].folderId, "extra-21");
  });
  it("a Study Set created while viewing a Folder is assigned to that exact Folder", async () => {
    mount("en"); await ready(); fireEvent.click(screen.getByRole("button", { name: /^Travel/ }));
    await waitFor(() => assert.ok(screen.queryByText("Legacy words") === null));
    fireEvent.click(document.getElementById("create-collection-btn"));
    const dialog = await screen.findByRole("dialog");
    fireEvent.change(document.getElementById("create-title"), { target: { value: "New Study Set" } });
    fireEvent.submit(dialog.querySelector("form"));
    await screen.findByText("New Study Set");
    assert.equal(requests.find(r => r.method === "POST" && r.path === "/api/v1/collections").payload.folderId, folderA);
  });
});
