import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";
const root = fileURLToPath(new URL("..", import.meta.url));
const paths = ["apps/web/app/operations/dashboard/dashboard-charts.tsx","apps/web/app/operations/dashboard/page.tsx","apps/web/app/operations/dashboard/presentation.ts","apps/web/app/operations/dashboard/recent-failures.tsx","scripts/architecture-imports.test.mjs","scripts/verify-operations-dashboard.mjs"];
execFileSync(resolve(root, "node_modules/.bin/oxfmt"), paths, { cwd: root, stdio: "inherit" });
for (const path of paths) {
  console.log("YAK_OPERATIONS_FORMAT: " + JSON.stringify({ path, content: readFileSync(resolve(root, path), "utf8") }));
}
