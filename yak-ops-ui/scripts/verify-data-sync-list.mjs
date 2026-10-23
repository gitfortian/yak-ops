import assert from "node:assert/strict";
import { parseTaskListFilters, updateTaskListFilters } from "../apps/web/app/data-sync/list/filters.ts";
import { listQueryKeys } from "../apps/web/app/data-sync/list/query-keys.ts";

assert.deepEqual(parseTaskListFilters(new URLSearchParams()), {
  pageNo: 1, keyword: "", solutionType: "ALL", status: "ALL",
});
assert.deepEqual(parseTaskListFilters(new URLSearchParams("page=-3&solutionType=UNKNOWN&status=RUNNING")), {
  pageNo: 1, keyword: "", solutionType: "ALL", status: "ALL",
});
assert.deepEqual(parseTaskListFilters(new URLSearchParams("page=4&keyword=%20orders%20&solutionType=DATABASE_OFFLINE&status=PUBLISHED")), {
  pageNo: 4, keyword: "orders", solutionType: "DATABASE_OFFLINE", status: "PUBLISHED",
});
const original = new URLSearchParams("create=1&page=3&keyword=orders&solutionType=DATABASE_OFFLINE");
const updated = updateTaskListFilters(original, { keyword: " updated ", status: "UNPUBLISHED", pageNo: 1 });
assert.equal(updated.get("create"), "1");
assert.equal(updated.get("page"), null);
assert.equal(updated.get("keyword"), "updated");
assert.equal(updated.get("status"), "UNPUBLISHED");
assert.equal(updated.get("solutionType"), "DATABASE_OFFLINE");
assert.equal(original.get("keyword"), "orders");
assert.notDeepEqual(listQueryKeys.tasks("user-a:workspace-a"), listQueryKeys.tasks("user-a:workspace-b"));
assert.notDeepEqual(listQueryKeys.schedules("user-a:workspace-a"), listQueryKeys.schedules("user-b:workspace-a"));
console.log("Data Sync list filter and scope checks passed.");
