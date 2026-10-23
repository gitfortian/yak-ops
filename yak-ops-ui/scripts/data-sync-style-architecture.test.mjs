import assert from "node:assert/strict";
import { test } from "node:test";
import { checkDataSyncStyleTokens } from "./data-sync-style-architecture.mjs";

const check = (path, source) => checkDataSyncStyleTokens(path, source);
const detail = "apps/web/app/data-sync/detail/task-summary.tsx";
const editor = "apps/web/app/data-sync/editor/basic-section.tsx";
const list = "apps/web/app/data-sync/list/task-table.tsx";

test("migrated Data Sync components reject fixed Tailwind colors", () => {
  assert.match(
    check(detail, 'const Panel = () => <div className="text-[#344054] bg-[#fff]" />;').join("\n"),
    /embeds a fixed color/,
  );
  assert.match(
    check(editor, 'const Panel = () => <div style={{ backgroundColor: "#fff" }} />;').join("\n"),
    /embeds a fixed color/,
  );
  assert.match(
    check(list, 'const item = `text-[#d92d20] ${selected ? "font-bold" : ""}`;').join("\n"),
    /embeds a fixed color/,
  );
});

test("semantic Tailwind colors, arbitrary geometry and comments are valid", () => {
  assert.deepEqual(
    check(
      detail,
      '// old text-[#ff00ff] should not count\n' +
        'const Panel = () => <div className="text-[var(--yak-components-muted-text)] w-[300px]" />;',
    ),
    [],
  );
});

test("domain-specific log syntax colors remain in their CSS owner", () => {
  assert.deepEqual(
    check("apps/web/app/data-sync/task-instance-log.css", ".yak-log-level { color: #79adff }"),
    [],
  );
  assert.deepEqual(
    check("apps/web/app/layout/index.tsx", 'const Panel = () => <div className="text-[#fff]" />;'),
    [],
  );
});
