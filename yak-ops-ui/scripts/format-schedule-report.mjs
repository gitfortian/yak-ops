import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
const root = fileURLToPath(new URL("..", import.meta.url));
const files = ["apps/web/app/schedule/dialog/schedule-dialog.tsx","apps/web/app/schedule/dialog/use-schedule-session.ts","apps/web/app/schedule/history/fire-history.tsx","apps/web/app/schedule/form/schedule-preview.tsx","scripts/verify-schedule-query-contract.mjs","apps/web/app/schedule/query-keys.ts","apps/web/app/schedule/use-task-schedules.ts","apps/web/app/schedule/use-schedule-preview.ts","apps/web/app/schedule/form/schedule-form.tsx","apps/web/app/schedule/dialog/types.ts","apps/web/app/schedule/history/use-fire-history.ts","apps/web/app/data-sync/list/page.tsx","apps/web/app/data-sync/detail/index.tsx","scripts/architecture-imports.mjs","scripts/architecture-imports.test.mjs"];
execFileSync(resolve(root, "node_modules/.bin/oxfmt"), files, { cwd: root, stdio: "inherit" });
for (const path of files) {
  console.log("YAK_SCHEDULE_FORMAT: " + JSON.stringify({ path, content: readFileSync(resolve(root, path), "utf8") }));
}
