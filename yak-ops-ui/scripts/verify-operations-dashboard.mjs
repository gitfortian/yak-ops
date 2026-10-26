import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import ts from "typescript";

function load(path, dependencies = {}) {
  const source = readFileSync(new URL(path, import.meta.url), "utf8");
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  const exports = {};
  vm.runInNewContext(outputText, {
    exports,
    require: (name) => {
      if (name in dependencies) return dependencies[name];
      throw new Error("Unexpected dashboard dependency: " + name);
    },
  });
  return exports;
}

const presentation = load("../apps/web/app/operations/dashboard/presentation.ts");
const {
  resolveOperationsRange,
  operationsSummaryCards,
  operationsStatusDistribution,
  operationsTrendOption,
  operationsDistributionOption,
} = presentation;

assert.equal(resolveOperationsRange(null, "REALTIME"), "TODAY");
assert.equal(resolveOperationsRange(null, "OFFLINE"), "LAST_7_DAYS");
assert.equal(resolveOperationsRange("LAST_30_DAYS", "REALTIME"), "LAST_30_DAYS");
assert.equal(resolveOperationsRange("wrong", "OFFLINE"), "LAST_7_DAYS");
const summary = {
  executionCount: 12,
  succeededCount: 5,
  failedCount: 2,
  canceledCount: 3,
  runningCount: 2,
};
assert.equal(operationsSummaryCards(summary, "REALTIME")[0].label, "启动次数");
assert.equal(operationsSummaryCards(summary, "OFFLINE")[0].label, "执行总数");
assert.equal(operationsStatusDistribution(summary, "OFFLINE").at(-1).value, 2);
assert.equal(
  operationsStatusDistribution({ ...summary, executionCount: 3 }, "OFFLINE").at(-1)?.name,
  "已取消",
);
assert.ok(operationsStatusDistribution(summary, "REALTIME").some((row) => row.name === "已停止"));
const trend = [
  {
    bucket: "2026-10-24 08:00",
    executionCount: 4,
    succeededCount: 2,
    failedCount: 1,
    canceledCount: 1,
  },
];
const offlineOption = operationsTrendOption(trend, "OFFLINE", "TODAY");
const realtimeOption = operationsTrendOption(trend, "REALTIME", "LAST_7_DAYS");
assert.equal(offlineOption.series.length, 3);
assert.equal(realtimeOption.series.length, 2);
assert.equal(offlineOption.xAxis.data[0], "08:00");
assert.equal(operationsDistributionOption(summary, "OFFLINE").series[0].data.length, 4);

let userId = "user-1";
let workspaceId = "workspace-1";
const getDataSyncOperationsCalls = [];
const { useOperationsDashboard, operationsQueryKey } = load(
  "../apps/web/app/operations/dashboard/use-dashboard.ts",
  {
    "@tanstack/react-query": { useQuery: (options) => options },
    "@/hooks/use-auth": { useAuth: () => ({ currentUser: { id: userId } }) },
    "@/hooks/use-workspace": {
      useWorkspace: () => ({ currentWorkspace: workspaceId ? { id: workspaceId } : undefined }),
    },
    "@/service/data-sync/operations": {
      getDataSyncOperations: (...args) => {
        getDataSyncOperationsCalls.push(args);
        return Promise.resolve({});
      },
    },
  },
);
const config = useOperationsDashboard("OFFLINE", "LAST_7_DAYS");
assert.equal(config.enabled, true);
assert.equal(config.retry, false);
assert.equal(config.refetchInterval, 30000);
assert.equal(config.refetchIntervalInBackground, false);
assert.equal(config.queryKey[2], "user-1");
assert.equal(config.queryKey[3], "workspace-1");
assert.notEqual(
  JSON.stringify(operationsQueryKey("user-1", "workspace-1", "OFFLINE", "LAST_7_DAYS")),
  JSON.stringify(operationsQueryKey("user-1", "workspace-2", "OFFLINE", "LAST_7_DAYS")),
);
assert.notEqual(
  JSON.stringify(operationsQueryKey("user-1", "workspace-1", "OFFLINE", "LAST_7_DAYS")),
  JSON.stringify(operationsQueryKey("user-2", "workspace-1", "OFFLINE", "LAST_7_DAYS")),
);
assert.notEqual(
  JSON.stringify(operationsQueryKey("user-1", "workspace-1", "OFFLINE", "LAST_7_DAYS")),
  JSON.stringify(operationsQueryKey("user-1", "workspace-1", "REALTIME", "LAST_7_DAYS")),
);
assert.notEqual(
  JSON.stringify(operationsQueryKey("user-1", "workspace-1", "OFFLINE", "LAST_7_DAYS")),
  JSON.stringify(operationsQueryKey("user-1", "workspace-1", "OFFLINE", "TODAY")),
);
workspaceId = "";
assert.equal(useOperationsDashboard("OFFLINE", "LAST_7_DAYS").enabled, false);
workspaceId = "workspace-2";
userId = "user-2";
assert.equal(useOperationsDashboard("REALTIME", "TODAY").queryKey[2], "user-2");
const signal = new AbortController().signal;
await config.queryFn({ signal });
assert.equal(getDataSyncOperationsCalls.at(-1)[2], signal);
console.log("Operations Dashboard presentation, query scoping and refresh contracts passed");
