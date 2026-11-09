// Test-only TSX loader. Use the project's installed TypeScript; no extra runtime dependency.
// Only Next's routing/font/cookie adapters are replaced. UI, forms, auth, API and i18n are real.
import { registerHooks } from "node:module";
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";
import ts from "typescript";

const source = fileURLToPath(new URL("../../", import.meta.url));
const nextStub = new URL("./production-next-stubs.mjs", import.meta.url).href;
registerHooks({
  resolve(specifier, context, nextResolve) {
    if (["next/navigation", "next/link", "next/headers", "next/font/google"].includes(specifier)) {
      return { url: nextStub, shortCircuit: true };
    }
    const path = specifier.startsWith("@/") ? source + specifier.slice(2)
      : specifier.startsWith(".") && /\.tsx?$/.test(context.parentURL ?? "")
        ? fileURLToPath(new URL(specifier, context.parentURL)) : null;
    if (path) {
      for (const suffix of ["", ".ts", ".tsx"]) {
        if (existsSync(path + suffix) && /\.tsx?$/.test(path + suffix)) {
          return { url: pathToFileURL(path + suffix).href, shortCircuit: true };
        }
      }
    }
    return nextResolve(specifier, context);
  },
  load(url, context, nextLoad) {
    if (/\.tsx?$/.test(url)) {
      return { format: "module", shortCircuit: true, source: ts.transpileModule(readFileSync(new URL(url), "utf8"), {
        compilerOptions: { module: ts.ModuleKind.ESNext, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022 },
        fileName: fileURLToPath(url),
      }).outputText };
    }
    if (url.endsWith(".css")) return { format: "module", source: "", shortCircuit: true };
    return nextLoad(url, context);
  },
});
