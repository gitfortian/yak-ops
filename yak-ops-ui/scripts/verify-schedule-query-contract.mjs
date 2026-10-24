import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import ts from "typescript";

const source = readFileSync(
  new URL("../apps/web/app/schedule/query-keys.ts", import.meta.url),
  "utf8",
);
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
});
const exports = {};
const document = { visibilityState: "visible" };
vm.runInNewContext(outputText, {
  exports,
  document,
  require: (name) => {
    if (name.includes("use-auth")) return { useAuth: () => ({ currentUser: { id: "u1" } }) };
    if (name.includes("use-workspace"))
      return { useWorkspace: () => ({ currentWorkspace: { id: "w1" } }) };
    throw new Error("Unexpected dependency: " + name);
  },
});
const { scheduleQueryKeys: keys, visiblePollInterval, useScheduleScope } = exports;
assert.equal(
  JSON.stringify(useScheduleScope()),
  JSON.stringify({ userId: "u1", workspaceId: "w1" }),
);
assert.notEqual(
  JSON.stringify(keys.targets("u1", "w1", ["task"])),
  JSON.stringify(keys.targets("u2", "w1", ["task"])),
);
assert.notEqual(
  JSON.stringify(keys.targets("u1", "w1", ["task"])),
  JSON.stringify(keys.targets("u1", "w2", ["task"])),
);
assert.notEqual(
  JSON.stringify(keys.targets("u1", "w1", ["task"])),
  JSON.stringify(keys.targets("u1", "w1", ["other"])),
);
assert.notEqual(
  JSON.stringify(keys.detail("u1", "w1", "task")),
  JSON.stringify(keys.detail("u1", "w2", "task")),
);
assert.notEqual(
  JSON.stringify(keys.fires("u1", "w1", "s1", 1, "ALL", "", "")),
  JSON.stringify(keys.fires("u1", "w1", "s1", 2, "ALL", "", "")),
);
assert.notEqual(
  JSON.stringify(keys.preview("u1", "w1", "0 0 * * * ?", "UTC")),
  JSON.stringify(keys.preview("u1", "w1", "0 0 * * * ?", "Asia/Shanghai")),
);
assert.equal(visiblePollInterval(), 5000);
document.visibilityState = "hidden";
assert.equal(visiblePollInterval(), false);
console.log("Schedule query scope and visibility contract passed");
