import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
const root = fileURLToPath(new URL("..", import.meta.url));
const files = [
  "scripts/check-architecture.mjs",
  "scripts/architecture-imports.mjs",
  "scripts/architecture-imports.test.mjs",
  "scripts/verify-management-contract.mjs",
  "apps/web/app/management/users/page.tsx",
  "apps/web/app/management/users/user-table.tsx",
  "apps/web/app/management/users/user-filters.tsx",
  "apps/web/app/management/users/user-form.tsx",
  "apps/web/app/management/users/use-users.ts",
  "apps/web/app/management/workspaces/page.tsx",
  "apps/web/app/management/workspaces/workspace-table.tsx",
  "apps/web/app/management/workspaces/workspace-form.tsx",
  "apps/web/app/management/workspaces/use-workspaces.ts",
  "apps/web/app/management/workspaces/members/add-member-dialog.tsx",
  "apps/web/app/management/workspaces/members/member-dialog.tsx",
  "apps/web/app/management/workspaces/members/member-table.tsx",
  "apps/web/app/management/workspaces/members/permissions.ts",
  "apps/web/app/management/workspaces/members/use-members.ts"
];
execFileSync(resolve(root, "node_modules/.bin/oxfmt"), files, { cwd: root, stdio: "inherit" });
for (const path of files) {
  console.log("YAK_MANAGEMENT_FORMAT: " + JSON.stringify({ path, content: readFileSync(resolve(root, path), "utf8") }));
}
