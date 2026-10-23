import assert from "node:assert/strict";

import {
  parseLogContent,
  parseLogLine,
  tokenizeLogMessage,
  trimLogContent,
} from "../apps/web/app/data-sync/log/task-instance-log-parser.ts";

const entry =
  "2026-10-22 15:48:07.418 INFO [yak-local-checkpoint-0] i.y.o.f.r.c.AlignedCheckpointCoordinator - YakFlow checkpoint saved, jobId=01785c, checkpointId=689";
const parsed = parseLogLine(entry);
assert.equal(parsed.kind, "structured");
assert.equal(parsed.timestamp, "2026-10-22 15:48:07.418");
assert.equal(parsed.level, "INFO");
assert.equal(parsed.thread, "yak-local-checkpoint-0");
assert.equal(parsed.logger, "i.y.o.f.r.c.AlignedCheckpointCoordinator");
assert.deepEqual(
  parsed.message?.filter((token) => token.kind !== "text"),
  [
    { text: "checkpointId=", kind: "key" },
    { text: "689", kind: "value" },
  ],
);
assert.equal(parsed.message?.map((token) => token.text).join(""), entry.split(" - ")[1]);

for (const level of ["TRACE", "DEBUG", "WARN", "ERROR", "FATAL"]) {
  assert.equal(parseLogLine(entry.replace(" INFO ", " " + level + " ")).level, level);
}
assert.equal(parseLogLine("not a structured log").kind, "plain");
assert.equal(parseLogLine("\tat io.yak.flow.Reader.read(Reader.java:42)").kind, "stack");
assert.equal(
  parseLogLine("Caused by: java.sql.SQLException: timeout").raw,
  "Caused by: java.sql.SQLException: timeout",
);
assert.deepEqual(
  tokenizeLogMessage("recordsOut=3 readRate=0 duration=12ms java.sql.SQLException"),
  [
    { text: "recordsOut=", kind: "key" },
    { text: "3", kind: "value" },
    { text: " ", kind: "text" },
    { text: "readRate=", kind: "key" },
    { text: "0", kind: "value" },
    { text: " ", kind: "text" },
    { text: "duration=", kind: "key" },
    { text: "12ms", kind: "value" },
    { text: " ", kind: "text" },
    { text: "java.sql.SQLException", kind: "exception" },
  ],
);

const unsafe = "<script>alert(1)</script> jobId=123456 checkpointId=7";
assert.equal(
  tokenizeLogMessage(unsafe)
    .map((token) => token.text)
    .join(""),
  unsafe,
);
assert.equal(tokenizeLogMessage(unsafe).filter((token) => token.kind === "value").length, 1);
assert.deepEqual(parseLogContent(entry + "\r\n  at example.Reader.read(Reader.java:12)\n"), [
  parsed,
  parseLogLine("  at example.Reader.read(Reader.java:12)"),
  parseLogLine(""),
]);
assert.equal(trimLogContent("old-line\nrecent-line\nlast-line", 21), "recent-line\nlast-line");
assert.equal(trimLogContent("single-long-line", 8), "ong-line");
assert.equal(trimLogContent("short\n", 20), "short\n");

console.log("Data Sync log parsing and highlighting checks passed");
