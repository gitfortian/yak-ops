import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";
const root = fileURLToPath(new URL("..", import.meta.url));
const paths = ["apps/web/app/layout/ProductLauncher.tsx","apps/web/app/layout/TopBar.tsx","apps/web/app/layout/account-menu.tsx","apps/web/app/layout/product-favorites.ts","apps/web/app/layout/use-product-favorites.ts","apps/web/app/layout/workspace-create-dialog.tsx","apps/web/app/layout/workspace-switcher.tsx","scripts/architecture-imports.mjs","scripts/architecture-imports.test.mjs","scripts/verify-app-shell.mjs"];
execFileSync(resolve(root, "node_modules/.bin/oxfmt"), paths, { cwd: root, stdio: "inherit" });
for (const path of paths) {
  console.log("YAK_SHELL_FORMAT: " + JSON.stringify({ path, content: readFileSync(resolve(root, path), "utf8") }));
}
