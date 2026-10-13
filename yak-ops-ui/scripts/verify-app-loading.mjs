import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
const router = read("apps/web/app/router/AppRouter.tsx");
const loading = read("apps/web/app/router/AppLoading.tsx");
const icon = read("packages/yak-ui/src/icons/YakMarkIcon.tsx");
const exports = read("packages/yak-ui/src/icons/index.ts");

// Static wiring checks; browser layout and authentication are verified separately.
const loadingBranches = [...router.matchAll(/if \(loading\) \{([\s\S]*?)\n  \}/g)];
assert.equal(loadingBranches.length, 2, "Both authentication guards own a loading branch");
for (const [, body] of loadingBranches) {
  assert.equal(body.trim(), "return <AppLoading />;", "Both guards share the same loading view");
}
assert.doesNotMatch(router, /Loading\.\.\./);
assert.match(router, /import AppLoading from "\.\/AppLoading"/);
assert.match(loading, /import \{ YakMarkIcon \} from "@yak-ops\/yak-ui"/);
assert.match(loading, /role="status"/);
assert.match(loading, /aria-label="正在加载"/);
assert.match(loading, /className="sr-only">正在加载<\/span>/);
for (const className of ["h-dvh", "w-full", "items-center", "justify-center", "bg-white"]) {
  assert(loading.includes(className), `Loading view retains ${className}`);
}
assert.match(loading, /<YakMarkIcon size=\{48\}/);
assert.match(loading, /motion-safe:animate-pulse/);
assert.doesNotMatch(loading, /setTimeout|setInterval|requestAnimationFrame/);
assert.match(exports, /export \* from "\.\/YakMarkIcon"/);
assert.match(icon, /stroke="currentColor"/);
assert.match(icon, /viewBox="0 0 64 64"/);
assert.match(icon, /aria-hidden=\{hasAccessibleName \? undefined : true\}/);
assert.match(icon, /focusable="false"/);
assert.doesNotMatch(icon, /<image|<text|<script/);

console.log("App loading static wiring checks passed.");
