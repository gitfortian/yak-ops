import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import ts from "typescript";
import {
  EMPTY_SCHEDULE,
  FIRE_OUTCOMES,
  formatScheduleTime,
  sameScheduleRule,
  registrationText,
} from "../apps/web/app/schedule/schedule-presentation.ts";

// Execute the real service against a recording transport, including rejected reads and unknown writes.
const calls = [];
let responses = [];
class BizError extends Error {
  constructor(message, code) {
    super(message);
    this.code = code;
  }
}
let authenticationFailures = 0;
const http = Object.fromEntries(
  ["getData", "postData", "putData", "deleteData"].map((method) => [
    method,
    async (...args) => {
      calls.push({ method, args });
      const response = responses.shift();
      if (response instanceof Error) throw response;
      return response;
    },
  ]),
);
const source = readFileSync(
  new URL("../apps/web/service/schedule/index.ts", import.meta.url),
  "utf8",
);
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
});
const exported = {};
vm.runInNewContext(outputText, {
  exports: exported,
  require: (name) => {
    if (name.endsWith("HttpUtils")) return { default: http };
    if (name.endsWith("request"))
      return {
        BizError,
        handleAuthenticationFailure: () => {
          authenticationFailures += 1;
        },
      };
    throw new Error("Unexpected service dependency: " + name);
  },
  Error,
  encodeURIComponent,
});
const {
  listTaskSchedules,
  getTaskSchedule,
  createSchedule,
  updateSchedule,
  setScheduleEnabled,
  deleteSchedule,
  previewSchedule,
  listScheduleFires,
} = exported;
const page = (rows, total = rows.length) => ({
  bizData: rows,
  pagination: { pageNo: 1, pageSize: 200, total, pages: 1 },
});
await listTaskSchedules([]);
assert.equal(calls.length, 0);
await assert.rejects(listTaskSchedules(Array.from({ length: 201 }, () => "task")));
assert.equal(calls.length, 0);
responses = [page([{ id: "schedule", targetId: "task" }])];
await listTaskSchedules(["task"]);
assert.equal(calls.length, 1);
assert.equal(calls[0].args[0], "/api/v1/schedules/page");
assert.equal(
  JSON.stringify(calls[0].args[1]),
  JSON.stringify({ targetType: "TASK", targetIds: ["task"], pageNo: 1, pageSize: 200 }),
);
responses = [page([])];
assert.equal(await getTaskSchedule("task"), null);
responses = [new Error("connection lost")];
await assert.rejects(getTaskSchedule("task"));
responses = [page([], 1)];
await assert.rejects(listTaskSchedules(["task"]));
responses = [page([{ id: "other", targetId: "other" }])];
await assert.rejects(getTaskSchedule("task"));
const rule = {
  cronExpression: "0 0 2 ? * MON#5 2030",
  timeZone: "Asia/Singapore",
  enabled: true,
  revision: 999,
  parameters: "not schedule data",
};
responses = [{ id: "schedule", enabled: false }];
const beforeCreate = calls.length;
assert.equal((await createSchedule("task", rule)).enabled, false);
assert.equal(calls.length, beforeCreate + 1); // Saving must never call /enable.
assert.equal(
  JSON.stringify(calls.at(-1).args[1]),
  JSON.stringify({
    targetType: "TASK",
    targetId: "task",
    cronExpression: rule.cronExpression,
    timeZone: rule.timeZone,
  }),
);
responses = [{}];
await updateSchedule("schedule", 2, rule);
assert.equal(calls.at(-1).args[1].revision, 2);
assert.equal(calls.at(-1).args[1].enabled, undefined);
assert.equal(calls.at(-1).args[1].parameters, undefined);
responses = [{ after: "2026-10-14T00:00:00Z", nextFireTimes: [] }];
assert.equal((await previewSchedule(rule)).nextFireTimes.length, 0);
assert.equal(calls.at(-1).args[1].enabled, undefined);
responses = [new BizError("conflict", 18002)];
const beforeConflict = calls.length;
await assert.rejects(setScheduleEnabled("schedule", 2, true));
assert.equal(calls.length, beforeConflict + 1); // A stale mutation is not retried using a newer revision.
responses = [new Error("unknown commit")];
await assert.rejects(createSchedule("task", rule));
responses = [true];
await deleteSchedule("schedule", 3);
assert.equal(calls.at(-1).args[0], "/api/v1/schedules/schedule?revision=3");
responses = [page([])];
const signal = new AbortController().signal;
await listScheduleFires("deleted-schedule", { pageNo: 1, pageSize: 20 }, signal);
assert.equal(calls.at(-1).args[0], "/api/v1/schedules/deleted-schedule/fires/page");
assert.equal(calls.at(-1).args[2].signal, signal);
responses = [new BizError("expired", 2001)];
await assert.rejects(listTaskSchedules(["task"]));
assert.equal(authenticationFailures, 1);
assert.equal(
  exported.scheduleErrorMessage(new Error("jdbc:private-password")),
  "调度请求未完成，请重新加载确认结果",
);
assert.equal(EMPTY_SCHEDULE.cronExpression, "");
assert.equal(sameScheduleRule(rule, { ...rule }), true);
assert.equal(sameScheduleRule(rule, { ...rule, timeZone: "UTC" }), false);
assert.equal(FIRE_OUTCOMES.ADMITTED, "已提交");
assert.equal(FIRE_OUTCOMES.SKIPPED, "已跳过");
assert.equal(registrationText({ registrationStatus: "PENDING" }), "应用中");
assert.equal(
  registrationText({ registrationStatus: "ERROR", registrationReason: "jdbc:private" }),
  "调度状态异常",
);
assert.equal(
  registrationText({ registrationStatus: "APPLIED", enabled: true, exhausted: true }),
  "无后续触发",
);
assert.equal(formatScheduleTime(null), "—");
assert.equal(formatScheduleTime("2026-10-14T00:00:00"), "—");
assert.match(formatScheduleTime("2026-10-14T00:00:00Z", "Asia/Shanghai"), /08:00:00.*GMT\+8/);
const earlier = formatScheduleTime("2026-11-01T05:30:00Z", "America/New_York");
const later = formatScheduleTime("2026-11-01T06:30:00Z", "America/New_York");
assert.match(earlier, /01:30:00.*GMT[-−]4/);
assert.match(later, /01:30:00.*GMT[-−]5/);
assert.notEqual(earlier, later);
console.log("Schedule service, batching, provenance, UTC/DST and presentation contracts passed");
