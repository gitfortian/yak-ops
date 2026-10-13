import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { runInNewContext } from "node:vm";
import { createElement, Fragment } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import ts from "typescript";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
const require = createRequire(import.meta.url);
const router = read("apps/web/app/router/AppRouter.tsx");
const loading = read("apps/web/app/router/AppLoading.tsx");
const icon = read("packages/yak-ui/src/icons/YakMarkIcon.tsx");
const css = read("apps/web/app/router/app-loading.css");
const exports = read("packages/yak-ui/src/icons/index.ts");

// Compile the two components with their real React runtime and explicit local imports.
function loadComponent(source, imports = {}) {
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      target: ts.ScriptTarget.ES2022,
      module: ts.ModuleKind.CommonJS,
      jsx: ts.JsxEmit.ReactJSX,
    },
  });
  const module = { exports: {} };
  runInNewContext(outputText, {
    module,
    exports: module.exports,
    require: (name) => imports[name] ?? require(name),
  });
  return module.exports;
}

const { YakMarkIcon } = loadComponent(icon);
const { default: AppLoading } = loadComponent(loading, {
  "@yak-ops/yak-ui": { YakMarkIcon },
  "./app-loading.css": {},
});
const markup = renderToStaticMarkup(
  createElement(Fragment, null, createElement(AppLoading), createElement(AppLoading)),
);
const ids = [...markup.matchAll(/\bid="([^"]+)"/g)].map((match) => match[1]);
const references = [...markup.matchAll(/url\(#([^)]+)\)/g)].map((match) => match[1]);
assert.equal(ids.length, 4, "Each loading instance defines its own mask and gradient");
assert.equal(new Set(ids).size, ids.length, "SVG IDs do not collide across instances");
assert.equal(references.length, 4);
assert.deepEqual(new Set(references), new Set(ids), "Every SVG reference resolves locally");
assert.equal([...markup.matchAll(/role="status"/g)].length, 2);
assert.equal([...markup.matchAll(/<\/defs><svg[^>]+fill="currentColor"/g)].length, 2);
assert.match(markup, /mask-type:alpha/);

const decorative = renderToStaticMarkup(createElement(YakMarkIcon));
assert.match(decorative, /width="24" height="24"/);
assert.match(decorative, /aria-hidden="true"/);
assert.match(decorative, /fill="currentColor" fill-rule="evenodd"/);
assert.doesNotMatch(decorative, /role="img"|<mask|<linearGradient/);
const named = renderToStaticMarkup(
  createElement(YakMarkIcon, { size: 40, "aria-label": "Yak", fill: "#162044" }),
);
assert.match(named, /width="40" height="40"/);
assert.match(named, /role="img"/);
assert.match(named, /aria-label="Yak"/);
assert.match(named, /fill="#162044"/);
assert.doesNotMatch(named, /aria-hidden/);

// Static wiring and motion guards complement rendering; they do not prove browser behavior.
const loadingBranches = [...router.matchAll(/if \(loading\) \{([\s\S]*?)\n  \}/g)];
assert.equal(loadingBranches.length, 2, "Both authentication guards own a loading branch");
for (const [, body] of loadingBranches) {
  assert.equal(body.trim(), "return <AppLoading />;", "Both guards share the same loading view");
}
assert.doesNotMatch(router, /Loading\.\.\./);
assert.match(router, /import AppLoading from "\.\/AppLoading"/);
assert.match(loading, /className="sr-only">正在加载<\/span>/);
for (const className of ["h-dvh", "w-full", "items-center", "justify-center", "bg-white"]) {
  assert(loading.includes(className), `Loading view retains ${className}`);
}
assert.match(loading, /width="48"\s+height="48"/);
assert.match(loading, /<YakMarkIcon size=\{64\}/);
assert.match(loading, /gradientUnits="userSpaceOnUse"/);
assert.match(loading, /motion-safe:animate-\[yak-loading-shimmer_2s_linear_infinite\]/);
assert.match(loading, /motion-reduce:hidden/);
assert.match(css, /@keyframes yak-loading-shimmer/);
assert.match(css, /70%,\s+to\s*\{\s*transform: translateX\(92px\)/);
assert.doesNotMatch(
  loading + css,
  /animate-pulse|animate-spin|setTimeout|setInterval|requestAnimationFrame/,
);
assert.match(exports, /export \* from "\.\/YakMarkIcon"/);
assert.match(icon, /viewBox="0 0 64 64"/);
assert.match(icon, /focusable="false"/);
assert.doesNotMatch(icon, /<image|<text|<script|<animate/);

console.log("App loading rendering and static wiring checks passed.");
