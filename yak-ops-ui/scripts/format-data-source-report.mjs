import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("..", import.meta.url));
const files = [
  "apps/web/app/datasource/form/advanced-properties.tsx",
  "apps/web/app/datasource/form/data-source-dialog.tsx",
  "apps/web/app/datasource/form/form-fields.tsx",
  "apps/web/app/datasource/form/model.ts",
  "apps/web/app/datasource/form/type-selector.tsx",
  "apps/web/app/datasource/form/use-data-source-form.ts",
  "apps/web/app/datasource/list/data-source-filters.tsx",
  "apps/web/app/datasource/list/data-source-table.tsx",
  "apps/web/app/datasource/list/page.tsx",
  "apps/web/app/datasource/list/use-data-sources.ts",
  "scripts/verify-data-source-model.mjs"
];
execFileSync(resolve(root, "node_modules/.bin/oxfmt"), files, { cwd: root, stdio: "inherit" });
for (const path of files) {
  console.log("YAK_FORMAT: " + JSON.stringify({ path, content: readFileSync(resolve(root, path), "utf8") }));
}
