import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import ts from "typescript";
import { detailQueryKeys } from "../apps/web/app/data-sync/detail/query-keys.ts";

assert.notDeepEqual(
  detailQueryKeys.task("user-a:ws-a", "t1"),
  detailQueryKeys.task("user-b:ws-a", "t1"),
);
assert.notDeepEqual(
  detailQueryKeys.instance("user-a:ws-a", "t1", "i1"),
  detailQueryKeys.instance("user-a:ws-b", "t1", "i1"),
);
assert.notDeepEqual(
  detailQueryKeys.instancePage("user-a:ws-a", "t1", 1, "ALL"),
  detailQueryKeys.instancePage("user-a:ws-a", "t1", 2, "ALL"),
);
assert.notDeepEqual(
  detailQueryKeys.instancePage("user-a:ws-a", "t1", 1, "RUNNING"),
  detailQueryKeys.instancePage("user-a:ws-a", "t1", 1, "ALL"),
);
assert.notDeepEqual(
  detailQueryKeys.instance("user-a:ws-a", "t1", "i1"),
  detailQueryKeys.instance("user-a:ws-a", "t2", "i1"),
);

const actionsSource = readFileSync(
  new URL("../apps/web/app/data-sync/detail/use-execution-actions.ts", import.meta.url),
  "utf8",
);
const { outputText } = ts.transpileModule(actionsSource, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
});
function createActions({ active = [], instanceStatus = "RUNNING", published = true } = {}) {
  const called = [];
  const services = {
    "@yak-ops/yak-ui": {
      toast: {
        success: (msg) => called.push("toast:" + msg),
        error: (msg) => called.push("error:" + msg),
      },
    },
    "@/service/data-sync": {
      runDataSyncTask: async () => {
        called.push("submit");
        return { instanceId: "i-new" };
      },
      cancelDataSyncInstance: async () => {
        called.push("cancel");
      },
    },
    "@/service/data-sync/execution": { canCancelTaskInstance: (s) => s === "RUNNING" },
    "@/service/task-execution": {
      listLatestTaskInstances: async () => {
        called.push("check-latest");
        return active;
      },
    },
    react: { useState: (initial) => [initial, (next) => called.push("state:" + String(next))] },
  };
  const exports = {};
  vm.runInNewContext(outputText, {
    exports,
    require(name) {
      if (Object.hasOwn(services, name)) return services[name];
      throw Error("Unexpected dependency: " + name);
    },
    Error,
  });
  const actions = exports.useExecutionActions({
    taskId: "t1",
    task: { id: "t1", status: published ? "PUBLISHED" : "UNPUBLISHED" },
    canStart: true,
    selectedInstance: { id: "i1", status: instanceStatus },
    refreshActiveInstances: async () => called.push("refresh-active"),
    refreshAfterCancel: async () => called.push("refresh-detail"),
    selectSubmitted: (id) => called.push("select:" + id),
  });
  return { actions, called };
}

const normal = createActions();
await normal.actions.run();
assert.deepEqual(
  normal.called.filter((s) =>
    ["check-latest", "submit", "refresh-active", "select:i-new"].includes(s),
  ),
  ["check-latest", "submit", "refresh-active", "select:i-new"],
);

const conflict = createActions({ active: [{ id: "i-old" }] });
await conflict.actions.run();
assert.ok(conflict.called.includes("error:任务正在执行，请先停止"));
assert.ok(conflict.called.includes("refresh-active"));
assert.equal(
  conflict.called.includes("submit"),
  false,
  "never submit if an active instance exists",
);

const offline = createActions({ published: false });
await offline.actions.run();
assert.equal(offline.called.length, 0, "unpublished tasks cannot execute");

const stopped = createActions();
await stopped.actions.cancel();
assert.ok(stopped.called.includes("cancel"));
assert.ok(stopped.called.includes("refresh-active"));
assert.ok(stopped.called.includes("refresh-detail"));
assert.ok(stopped.called.indexOf("cancel") < stopped.called.indexOf("refresh-detail"));

const finalized = createActions({ instanceStatus: "FINISHED" });
await finalized.actions.cancel();
assert.equal(finalized.called.length, 0, "finished executions cannot be canceled");

const logSource = readFileSync(
  new URL("../apps/web/app/data-sync/log/use-instance-logs.ts", import.meta.url),
  "utf8",
);
assert.match(logSource, /activeRef\.current = active/);
assert.match(logSource, /\[instanceId, revision\]/);
assert.doesNotMatch(logSource, /\[instanceId, active, revision\]/);
assert.match(logSource, /trimLogContent\(previous \+ page\.content\)/);
assert.match(logSource, /pages < 5/);
console.log("Detail query scope, task commands, cancellation and log cursor checks passed");
