import assert from "node:assert/strict";
import { join } from "node:path";
import { test } from "node:test";
import { checkArchitectureImports } from "./architecture-imports.mjs";

const root = join(process.cwd());
const check = (file, source) => checkArchitectureImports(file, source, root);

test("service cannot import App via aliases or relative re-exports", () => {
  assert.match(
    check("apps/web/service/data-sync/index.ts", 'import x from "@/app/data-sync";').join("\n"),
    /imports App code/,
  );
  assert.match(
    check("apps/web/service/data-sync/index.ts", 'export * from "../../app/data-sync";').join("\n"),
    /imports App code/,
  );
});

test("App cannot bypass the domain service through dynamic imports", () => {
  assert.match(
    check(
      "apps/web/app/data-sync/index.tsx",
      'const http = import("@/service/http/request");',
    ).join("\n"),
    /bypasses domain Service/,
  );
  assert.match(
    check("apps/web/app/data-sync/index.tsx", 'import "../../service/http/request";').join("\n"),
    /bypasses domain Service/,
  );
});

test("Yak UI cannot reach product code", () => {
  assert.match(
    check("packages/yak-ui/src/button/Button.tsx", 'import x from "@/service/data-sync";').join(
      "\n",
    ),
    /imports product code into Yak UI/,
  );
});

test("feature-local hooks and legitimate imports are allowed", () => {
  assert.deepEqual(
    check(
      "apps/web/app/datasource/hooks/use-catalog.ts",
      'import { listDataSources } from "@/service/datasource";',
    ),
    [],
  );
  assert.deepEqual(
    check("apps/web/app/data-sync/index.tsx", 'import { Button } from "@yak-ops/yak-ui";'),
    [],
  );
});

test("strings and comments are not treated as module imports", () => {
  assert.deepEqual(
    check(
      "apps/web/service/data-sync/index.ts",
      '// import "@/app/data-sync";\nconst text = \'import "@/app/data-sync"\';',
    ),
    [],
  );
});

test("outside domains cannot deep import Data Sync private features", () => {
  assert.match(
    check(
      "apps/web/app/operations/index.tsx",
      'import { ExecutionSidebar } from "@/app/data-sync/detail/execution-sidebar";',
    ).join("\n"),
    /imports a private Data Sync feature/,
  );
  assert.match(
    check(
      "apps/web/app/operations/index.tsx",
      'const editor = import("../data-sync/editor/index");',
    ).join("\n"),
    /imports a private Data Sync feature/,
  );
});

test("Data Sync surfaces cannot import each other's private implementation", () => {
  assert.match(
    check(
      "apps/web/app/data-sync/list/task-table.tsx",
      'export { TaskSummary } from "../detail/task-summary";',
    ).join("\n"),
    /crosses Data Sync feature boundaries/,
  );
  assert.match(
    check(
      "apps/web/app/data-sync/editor/index.tsx",
      'const log = require("../log/use-instance-logs");',
    ).join("\n"),
    /crosses Data Sync feature boundaries/,
  );
});

test("Data Sync root composition and shared domain dependencies remain legal", () => {
  assert.deepEqual(
    check(
      "apps/web/app/data-sync/index.tsx",
      'import { DataSyncTaskDetailPage } from "./detail";',
    ),
    [],
  );
  assert.deepEqual(
    check(
      "apps/web/app/data-sync/detail/index.tsx",
      'import { ExecutionProgressBar } from "../execution-progress-bar";',
    ),
    [],
  );
  assert.deepEqual(
    check(
      "apps/web/app/data-sync/list/task-table.tsx",
      'import { listDataSyncTasks } from "@/service/data-sync";',
    ),
    [],
  );
});
