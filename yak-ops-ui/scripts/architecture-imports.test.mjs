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
  const operations = "apps/web/app/operations/index.tsx";
  const direct = 'import { ExecutionSidebar } from "@/app/data-sync/detail/execution-sidebar";';
  const dynamic = 'const editor = import("../data-sync/editor/index");';

  assert.match(check(operations, direct).join("\n"), /imports a private Data Sync feature/);
  assert.match(check(operations, dynamic).join("\n"), /imports a private Data Sync feature/);
});

test("Data Sync surfaces cannot import each other's private implementation", () => {
  const list = "apps/web/app/data-sync/list/task-table.tsx";
  const editor = "apps/web/app/data-sync/editor/index.tsx";
  const reexport = 'export { TaskSummary } from "../detail/task-summary";';
  const required = 'const log = require("../detail/log/use-instance-logs");';

  assert.match(check(list, reexport).join("\n"), /crosses Data Sync feature boundaries/);
  assert.match(check(editor, required).join("\n"), /crosses Data Sync feature boundaries/);
});

test("router may import public Data Sync pages but not private components", () => {
  const router = "apps/web/app/router/AppRouter.tsx";
  assert.deepEqual(check(router, 'import { DataSyncPage } from "@/app/data-sync/list/page";'), []);
  assert.deepEqual(
    check(router, 'import { DataSyncTaskDetailPage } from "@/app/data-sync/detail";'),
    [],
  );
  assert.deepEqual(
    check(router, 'import { DataSyncTaskEditorPage } from "@/app/data-sync/editor/page";'),
    [],
  );
  assert.match(
    check(
      router,
      'import { ExecutionSidebar } from "@/app/data-sync/detail/execution-sidebar";',
    ).join("\n"),
    /imports a private Data Sync feature/,
  );
});

test("Data Sync root composition and shared domain dependencies remain legal", () => {
  const root = "apps/web/app/data-sync/index.tsx";
  const detail = "apps/web/app/data-sync/detail/index.tsx";
  const list = "apps/web/app/data-sync/list/task-table.tsx";
  const composition = 'import { DataSyncTaskDetailPage } from "./detail";';
  const shared = 'import { ExecutionProgressBar } from "../execution-progress-bar";';
  const service = 'import { listDataSyncTasks } from "@/service/data-sync";';

  assert.deepEqual(check(root, composition), []);
  assert.deepEqual(check(detail, shared), []);
  assert.deepEqual(check(list, service), []);
});

test("router imports Management entrypoints but private implementations stay owned", () => {
  const router = "apps/web/app/router/AppRouter.tsx";
  assert.deepEqual(
    check(router, 'import { UserManagementPage } from "@/app/management/users/page";'),
    [],
  );
  assert.deepEqual(
    check(router, 'import { WorkspaceManagementPage } from "@/app/management/workspaces/page";'),
    [],
  );
  assert.match(
    check(router, 'import { UserForm } from "@/app/management/users/user-form";').join("\\n"),
    /imports a private Management feature/,
  );
  assert.match(
    check(
      "apps/web/app/data-sync/list/page.tsx",
      'const member = import("@/app/management/workspaces/members/member-dialog");',
    ).join("\\n"),
    /imports a private Management feature/,
  );
  assert.match(
    check(
      "apps/web/app/management/users/page.tsx",
      'export * from "../workspaces/members/permissions";',
    ).join("\\n"),
    /crosses Management feature boundaries/,
  );
  assert.deepEqual(
    check("apps/web/app/management/users/page.tsx", 'import { userQueryKeys } from "./use-users";'),
    [],
  );
});

test("Schedule public contracts remain usable; private lifecycle imports are rejected", () => {
  const consumer = "apps/web/app/data-sync/detail/index.tsx";
  assert.deepEqual(
    check(consumer, 'import { ScheduleDialog } from "@/app/schedule/dialog/schedule-dialog";'),
    [],
  );
  assert.deepEqual(
    check(
      "apps/web/app/data-sync/editor/schedule-section.tsx",
      'import { ScheduleForm } from "@/app/schedule/form/schedule-form";',
    ),
    [],
  );
  assert.match(
    check(
      "apps/web/app/data-sync/editor/schedule-section.tsx",
      'import { SchedulePreview } from "@/app/schedule/form/schedule-preview";',
    ).join("\n"),
    /imports a private Schedule feature/,
  );
  assert.deepEqual(
    check(consumer, 'import { useTaskSchedules } from "@/app/schedule/use-task-schedules";'),
    [],
  );
  assert.deepEqual(
    check(
      "apps/web/app/data-sync/list/task-table.tsx",
      'import { TaskScheduleCell } from "@/app/schedule/task-schedule-cell";',
    ),
    [],
  );
  assert.match(
    check(
      consumer,
      'import { useFireHistory } from "@/app/schedule/history/use-fire-history";',
    ).join("\n"),
    /imports a private Schedule feature/,
  );
  assert.match(
    check(consumer, 'const editor = import("@/app/schedule/dialog/use-schedule-session");').join(
      "\n",
    ),
    /imports a private Schedule feature/,
  );
  assert.match(
    check(consumer, 'export * from "../../schedule/query-keys";').join("\n"),
    /imports a private Schedule feature/,
  );
});

test("Operations exposes only its Dashboard page to the router", () => {
  const router = "apps/web/app/router/AppRouter.tsx";
  assert.deepEqual(
    check(router, 'import { OfflineTaskOperationsPage } from "@/app/operations/dashboard/page";'),
    [],
  );
  assert.match(
    check(router, 'import { EChart } from "@/app/operations/dashboard/echart";').join("\n"),
    /imports a private Operations feature/,
  );
  assert.match(
    check(
      "apps/web/app/data-sync/list/page.tsx",
      'import { useOperationsDashboard } from "@/app/operations/dashboard/use-dashboard";',
    ).join("\n"),
    /imports a private Operations feature/,
  );
  assert.deepEqual(
    check(
      "apps/web/app/operations/dashboard/page.tsx",
      'import { useOperationsDashboard } from "./use-dashboard";',
    ),
    [],
  );
});
