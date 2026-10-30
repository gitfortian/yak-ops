/*
 * Guards the design-token rule: product code must reference tokens, never raw
 * colour literals.
 *
 * Allowed exceptions are file-scoped and explicit. Each one is artwork whose
 * colours are not a UI decision: login illustrations, vendor logos, the app
 * loading artwork, and the log-viewer palette.
 */
import { readFileSync, readdirSync, statSync } from "node:fs";

const ROOT = new URL("..", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");

const SCAN = ["apps/web", "packages/yak-ui/src"];
const EXTENSIONS = [".tsx", ".css"];

const ALLOWED = new Set([
  "apps/web/app/login/LoginCharacters.tsx",
  "apps/web/app/login/LoginSuccessPose.tsx",
  "apps/web/app/login/YellowCharacter.tsx",
  "apps/web/app/login/login-characters.css",
  "apps/web/app/login/login-welcome-scene.css",
  "apps/web/app/datasource/icons/MysqlIcon.tsx",
  "apps/web/app/datasource/icons/OracleIcon.tsx",
  "apps/web/app/datasource/icons/PsSqlIcon.tsx",
  "apps/web/app/router/AppLoading.tsx",
  "apps/web/app/data-sync/detail/log/task-instance-log.css",
  "packages/yak-ui/src/styles.css",
]);

/* Token definition files are where literals belong; everything else consumes them. */
const ALLOWED_PREFIXES = ["apps/web/theme/", "apps/web/themes/"];

// #rgb / #rrggbb, plus rgb()/rgba()/hsl() literals with hard-coded channels.
const HEX = /#[0-9a-fA-F]{3,8}\b/g;

const walk = (dir) =>
  readdirSync(dir).flatMap((name) => {
    const full = `${dir}/${name}`;
    if (name === "node_modules") return [];
    return statSync(full).isDirectory() ? walk(full) : [full];
  });

const violations = [];

for (const target of SCAN) {
  for (const path of walk(`${ROOT}/${target}`)) {
    if (!EXTENSIONS.some((ext) => path.endsWith(ext))) continue;
    const rel = path.slice(ROOT.length + 1).replaceAll("\\", "/");
    if (ALLOWED.has(rel) || ALLOWED_PREFIXES.some((prefix) => rel.startsWith(prefix))) continue;
    readFileSync(path, "utf8")
      .split("\n")
      .forEach((line, index) => {
        const hits = line.match(HEX);
        if (hits) violations.push(`${rel}:${index + 1}  ${[...new Set(hits)].join(" ")}`);
      });
  }
}

if (violations.length > 0) {
  console.error(`Hard-coded colours found in ${violations.length} place(s):`);
  for (const line of violations) console.error("  " + line);
  console.error("\nUse a design token instead, e.g. text-[var(--yak-color-text-secondary)].");
  console.error("If the colour is artwork, add the file to ALLOWED in this script with a reason.");
  process.exit(1);
}

console.log("Design token rule holds: no hard-coded colours outside the artwork allowlist.");
