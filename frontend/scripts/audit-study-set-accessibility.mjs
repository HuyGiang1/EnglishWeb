// Read-only real-browser WCAG and dialog-layout regression. Requires an authenticated local
// Chrome test tab; starts no services and never submits grades or mutates data.
// Usage: node scripts/audit-study-set-accessibility.mjs <local-study-set-url> <local-cdp-origin>
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import assert from "node:assert/strict";
const require = createRequire(import.meta.url);
const [target, debug] = process.argv.slice(2).map(value => new URL(value));
assert(target && debug, "Provide both local URLs.");
for (const url of [target, debug]) {
  assert(url.protocol === "http:" && ["127.0.0.1", "localhost"].includes(url.hostname) && !url.username && !url.password, "Only local test services are supported.");
}
assert(/^\/collections\/[0-9a-f-]{36}$/i.test(target.pathname), "Target one existing Study Set.");
const tabs = await (await fetch(new URL("/json/list", debug))).json();
const tab = tabs.find(item => item.type === "page" && item.url.startsWith(target.origin));
assert(tab, "Open an authenticated local test tab first.");
const address = new URL(tab.webSocketDebuggerUrl);
assert(["127.0.0.1", "localhost"].includes(address.hostname));
const ws = new WebSocket(address);
await new Promise((resolve, reject) => { ws.addEventListener("open", resolve, { once: true }); ws.addEventListener("error", reject, { once: true }); });
let id = 0;
const pending = new Map();
ws.addEventListener("message", event => {
  const message = JSON.parse(event.data), entry = pending.get(message.id);
  if (!entry) return;
  pending.delete(message.id); clearTimeout(entry.timeout);
  if (message.error) entry.reject(new Error("Chrome protocol request failed."));
  else entry.resolve(message.result);
});
function call(method, params = {}) {
  return new Promise((resolve, reject) => {
    const number = ++id, timeout = setTimeout(() => { pending.delete(number); reject(new Error("Chrome request timed out.")); }, 30000);
    pending.set(number, { resolve, reject, timeout }); ws.send(JSON.stringify({ id: number, method, params }));
  });
}
async function evaluate(expression) {
  const result = await call("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
  assert(!result.exceptionDetails, "Browser evaluation failed."); return result.result.value;
}
try {
  await call("Page.enable"); await call("Runtime.enable");
  await call("Page.navigate", { url: target.href });
  let ready = false;
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    if (await evaluate("!!document.querySelector('[data-testid=study-total]') && !document.querySelector('[data-testid=study-due]')?.textContent.includes('—')")) { ready = true; break; }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  assert(ready, "Authenticated Study Set did not become ready.");
  await evaluate("document.fonts.ready.then(() => true)");
  await evaluate(readFileSync(require.resolve("axe-core/axe.min.js"), "utf8"));
  const result = await evaluate("axe.run(document,{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa']}}).then(r=>({violations:r.violations.map(v=>({id:v.id,impact:v.impact,targets:v.nodes.map(n=>n.target)})),passes:r.passes.length,incomplete:r.incomplete.length}))");
  assert.equal(result.violations.length, 0, "Study Set must pass the audited WCAG rules, including badge contrast.");
  await call("Page.navigate", { url: target.origin + "/collections" });
  const dialogDeadline = Date.now() + 30000;
  let dialogReady = false;
  while (Date.now() < dialogDeadline) {
    if (await evaluate("!!document.querySelector('section[aria-labelledby=folder-heading] button')")) { dialogReady = true; break; }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  assert(dialogReady, "Folder controls did not become ready.");
  await evaluate("document.querySelector('section[aria-labelledby=folder-heading] button').click()");
  await call("Emulation.setDeviceMetricsOverride", { width: 375, height: 900, deviceScaleFactor: 1, mobile: false });
  await evaluate("document.documentElement.style.fontSize='200%'");
  await evaluate("new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))");
  // Wait for actual finite CSS transitions after zoom, rather than sampling mid-animation.
  await evaluate(`(async () => {
    const popup=document.querySelector('[role=dialog]');
    popup?.getBoundingClientRect();
    const animations=popup?.getAnimations({subtree:true}).filter(a=>a.effect?.getTiming().iterations!==Infinity)??[];
    await Promise.all(animations.map(a=>a.finished.catch(()=>{})));
    return true;
  })()`);
  const geometry = await evaluate(`(() => {
    const popup=document.querySelector('[role=dialog]');
    const close=popup?.querySelector('button[aria-label="Close dialog"]');
    const label=popup?.querySelector('[data-slot=dialog-header] label');
    if(!close||!label)return null;
    const a=close.getBoundingClientRect(),b=label.getBoundingClientRect();
    return {overlap:a.left<b.right&&a.right>b.left&&a.top<b.bottom&&a.bottom>b.top, viewport:innerWidth, close:{left:a.left,right:a.right,top:a.top,bottom:a.bottom}, label:{left:b.left,right:b.right,top:b.top,bottom:b.bottom}, headerPadding:getComputedStyle(popup.querySelector("[data-slot=dialog-header]").firstElementChild).paddingRight};
  })()`);
  assert(geometry, "Dialog label/close controls must exist.");
  if (geometry.overlap) console.error(JSON.stringify(geometry));
  assert.equal(geometry.overlap, false, "Dialog close button must not overlap the language label at 375px/200% text.");
  console.log(JSON.stringify({ ...result, dialogOverlap: geometry.overlap }));
  await evaluate("document.documentElement.style.fontSize='100%'");
  await call("Emulation.clearDeviceMetricsOverride");
  await call("Input.dispatchKeyEvent", { type: "keyDown", key: "Escape", code: "Escape", windowsVirtualKeyCode: 27 });
  await call("Input.dispatchKeyEvent", { type: "keyUp", key: "Escape", code: "Escape", windowsVirtualKeyCode: 27 });

} finally {
  for (const entry of pending.values()) clearTimeout(entry.timeout);
  ws.close();
}
