import assert from "node:assert/strict";
import { projectSyncTaskPage } from "../apps/web/service/data-sync/definition.ts";
import { readSyncParameters } from "../apps/web/service/data-sync/parameters.ts";
import {
  parseTaskListFilters,
  updateTaskListFilters,
} from "../apps/web/app/data-sync/list/filters.ts";
import { listQueryKeys } from "../apps/web/app/data-sync/list/query-keys.ts";

assert.deepEqual(parseTaskListFilters(new URLSearchParams()), {
  pageNo: 1,
  keyword: "",
  solutionType: "ALL",
  status: "ALL",
});
assert.deepEqual(
  parseTaskListFilters(new URLSearchParams("page=-3&solutionType=UNKNOWN&status=RUNNING")),
  {
    pageNo: 1,
    keyword: "",
    solutionType: "ALL",
    status: "ALL",
  },
);
assert.deepEqual(
  parseTaskListFilters(
    new URLSearchParams(
      "page=4&keyword=%20orders%20&solutionType=DATABASE_OFFLINE&status=PUBLISHED",
    ),
  ),
  {
    pageNo: 4,
    keyword: "orders",
    solutionType: "DATABASE_OFFLINE",
    status: "PUBLISHED",
  },
);
const original = new URLSearchParams(
  "create=1&page=3&keyword=orders&solutionType=DATABASE_OFFLINE",
);
const updated = updateTaskListFilters(original, {
  keyword: " updated ",
  status: "UNPUBLISHED",
  pageNo: 1,
});
assert.equal(updated.get("create"), "1");
assert.equal(updated.get("page"), null);
assert.equal(updated.get("keyword"), "updated");
assert.equal(updated.get("status"), "UNPUBLISHED");
assert.equal(updated.get("solutionType"), "DATABASE_OFFLINE");
assert.equal(original.get("keyword"), "orders");
assert.notDeepEqual(
  listQueryKeys.tasks("user-a:workspace-a"),
  listQueryKeys.tasks("user-a:workspace-b"),
);
assert.notDeepEqual(
  listQueryKeys.schedules("user-a:workspace-a"),
  listQueryKeys.schedules("user-b:workspace-a"),
);
console.log("Data Sync list filter and scope checks passed.");

const baseParameters = {
  solutionType: "SINGLE_TABLE_REALTIME",
  steps: ["FULL", "INCREMENTAL"],
  schemaChangesEnabled: false,
  source: { datasourceId: "source" },
  sink: { datasourceId: "target" },
  tableSelection: { mode: "EXPLICIT" },
  tableMappings: [
    {
      source: { table: "orders" },
      target: { table: "orders_copy" },
      writeMode: "UPSERT",
    },
  ],
};
const definition = (id, parameters, parameterSchemaVersion = 3) => ({
  id,
  name: "task-" + id,
  status: "PUBLISHED",
  taskType: "DATA_SYNC",
  definitionVersion: 4,
  parameterSchemaVersion,
  parameters,
  updateBy: "operator",
  updateTime: "2026-10-25 12:00:00",
});
const normal = definition("valid", JSON.stringify(baseParameters));
const legacyKeyless = definition(
  "old-keyless",
  JSON.stringify({
    ...baseParameters,
    tableMappings: [
      {
        source: { table: "orders", keyless: true, chunkKeyColumn: "bucket" },
        target: { table: "orders_copy" },
        writeMode: "RETRACT",
      },
    ],
  }),
);
assert.throws(() => readSyncParameters(3, legacyKeyless.parameters));
const validXa = definition(
  "xa",
  JSON.stringify({
    ...baseParameters,
    tableMappings: [
      {
        source: { table: "orders", keyless: true, chunkKeyColumn: "bucket", xa: true },
        target: { table: "orders_copy" },
        writeMode: "RETRACT",
      },
    ],
  }),
);
const page = {
  bizData: [
    normal,
    legacyKeyless,
    validXa,
    definition("broken-json", "{invalid"),
    definition("unknown-version", "{}", 99),
  ],
  pagination: { pageNo: 2, pageSize: 20, total: 45 },
};
const projected = projectSyncTaskPage(page);
assert.deepEqual(projected.pagination, page.pagination);
assert.deepEqual(
  projected.bizData.map((task) => task.id),
  ["valid", "old-keyless", "xa", "broken-json", "unknown-version"],
);
assert.equal(projected.bizData[0].syncType, "REALTIME");
assert.equal(projected.bizData[1].syncType, "REALTIME");
assert.equal(projected.bizData[1].sourceDataSourceId, "source");
assert.equal(projected.bizData[1].targetTable, "orders_copy");
assert.equal(projected.bizData[1].tableMappings[0].source.chunkKeyColumn, "bucket");
assert.equal(projected.bizData[2].keylessEnabled, true);
assert.equal(projected.bizData[2].tableMappings[0].source.xa, true);
for (const record of [projected.bizData[1], projected.bizData[3], projected.bizData[4]]) {
  assert.equal(record.name, "task-" + record.id);
  assert.equal(record.definitionVersion, 4);
  assert.equal(record.status, "PUBLISHED");
  assert.equal(record.updateBy, "operator");
  assert.ok(record.configurationError);
  if (record.id === "old-keyless") {
    assert.equal(record.sourceDataSourceId, "source");
  } else {
    assert.equal("sourceDataSourceId" in record, false);
  }
}
assert.match(projected.bizData[1].configurationError, /不支持的同步目标写入配置/);
assert.match(projected.bizData[3].configurationError, /JSON/);
assert.match(projected.bizData[4].configurationError, /不支持的同步任务参数版本/);
assert.equal("configurationError" in projected.bizData[0], false);
assert.equal("configurationError" in projected.bizData[2], false);
assert.throws(() => projectSyncTaskPage({ bizData: null, pagination: page.pagination }));
console.log("Data Sync unreadable definition isolation checks passed.");
