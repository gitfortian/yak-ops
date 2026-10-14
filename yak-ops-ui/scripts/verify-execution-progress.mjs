import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

import { getExecutionProgress } from "../apps/web/app/data-sync/execution-progress.ts";

assert.deepEqual(getExecutionProgress("FINISHED"), { mode: "success", value: 100 });
assert.deepEqual(getExecutionProgress("FAILED"), { mode: "failure", value: 0 });
assert.deepEqual(getExecutionProgress("RUNNING"), { mode: "running", value: null });
for (const status of ["SUBMITTED", "LAUNCHING", "CANCELLING", "RECONCILING", "CANCELED"]) {
  assert.deepEqual(getExecutionProgress(status), { mode: "idle", value: null });
}

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const detail = read("../apps/web/app/data-sync/execution-detail.tsx");
const page = read("../apps/web/app/data-sync/task-detail.tsx");
const css = read("../apps/web/app/data-sync/execution-detail.css");
const instancePage = read("../apps/web/app/data-sync/instance-runtime.tsx");

for (const source of [detail, page, instancePage]) {
  assert.doesNotMatch(source, /DataSyncExecutionConfigContent|本次运行配置|配置快照/);
}
assert.doesNotMatch(page, /DataSyncTaskStatusBadge|写入方式|调试模式/);
assert.doesNotMatch(detail, /SectionCard|<Badge|失败代码|查看运行日志/);
assert.doesNotMatch(detail, /结构迁移|全量同步|增量同步/);
assert.match(detail, /role="progressbar"/);
assert.match(detail, /aria-valuenow=\{progress.value \?\? undefined\}/);
assert.match(detail, /未取得进度数据/);
assert.match(detail, /sourceTable: tablePath\(\s*snapshot.source.database/);
assert.match(detail, /targetTable: tablePath\(\s*snapshot.target.database/);
assert.match(detail, /record.readRows/);
assert.match(detail, /record.writeRows/);
assert.doesNotMatch(detail, /readRows\s*\|\|\s*0|writeRows\s*\|\|\s*0/);
assert.match(css, /@media \(prefers-reduced-motion: reduce\)/);
assert.match(css, /animation: none/);
assert.match(page, /!autoRefresh \|\| \(!hasActiveInstance && !hasEnabledSchedule\)/);
assert.match(page, /hasEnabledSchedule,/);
assert.match(page, /window.clearInterval\(timer\)/);
assert.match(page, /request !== detailRequest.current/);
assert.match(page, /<ExecutionStatusIcon status=\{instance.status\}/);
assert.match(page, /key=\{selectedInstance.id\}/);
assert.match(page, /<TaskInstanceLogPanel/);
assert.equal((page.match(/<TabsTab\s/g) || []).length, 2);

console.log("Data Sync execution progress and presentation checks passed");
