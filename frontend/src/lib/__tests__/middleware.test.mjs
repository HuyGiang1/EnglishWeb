import "./production-loader.mjs";
import { registerHooks, createRequire } from "node:module";
import { pathToFileURL } from "node:url";
import { it } from "node:test";
import assert from "node:assert/strict";

const require = createRequire(import.meta.url);
const nextServerUrl = pathToFileURL(require.resolve("next/server")).href;
// Resolve the installed Next server module; use its real request/response classes.
registerHooks({
  resolve(specifier, context, nextResolve) {
    if (specifier === "next/server") return { url: nextServerUrl, shortCircuit: true };
    return nextResolve(specifier, context);
  },
});
const { NextRequest } = require("next/server");
const { middleware, config } = await import("../../middleware.ts");
for (const path of ["/flashcards?collectionId=42e08d83-fec4-4e15-9419-7e8da0c798f5", "/flashcards/nested"]) {
  it("anonymous Flashcards use the existing login redirect: " + path, () => {
    const response = middleware(new NextRequest("http://localhost" + path));
    assert.equal(response.status, 307);
    const destination = new URL(response.headers.get("location"));
    assert.equal(destination.pathname, "/login");
    assert.equal(destination.searchParams.get("from"), path.split("?")[0]);
  });
}
it("Flashcards route is included in the operational middleware matcher", () => {
  assert.ok(config.matcher.includes("/flashcards/:path*"));
});
it("session-cookie presence passes the edge guard; backend remains authentication authority", () => {
  const response = middleware(new NextRequest("http://localhost/flashcards", { headers: { cookie: "JSESSIONID=synthetic-guard-fixture" } }));
  assert.equal(response.headers.get("x-middleware-next"), "1");
});
it("existing Review protection remains", () => {
  assert.equal(middleware(new NextRequest("http://localhost/review")).status, 307);
});
it("public login remains available", () => {
  assert.equal(middleware(new NextRequest("http://localhost/login")).headers.get("x-middleware-next"), "1");
});
