import assert from "node:assert/strict";

import { resolveTableLayout } from "../packages/yak-ui/src/table/layout.ts";

const columns = [
  { key: "name", minWidth: 220 },
  { key: "status", width: 100 },
  { key: "actions", width: 240, fixed: "right" },
];
const wide = resolveTableLayout(columns, 900, 700);
assert.deepEqual(wide.widths, [560, 100, 240]);
assert.equal(wide.tableWidth, 900);
const narrow = resolveTableLayout(columns, 400, 700);
assert.deepEqual(narrow.widths, [360, 100, 240]);
assert.equal(narrow.tableWidth, 700);
const allFixed = resolveTableLayout([{ width: 100 }, { width: 140 }], 1000, 1100);
assert.deepEqual(allFixed.widths, [100, 140]);
assert.equal(allFixed.tableWidth, 240);
const customCss = resolveTableLayout([{ width: "40%" }, { minWidth: 150 }], 900);
assert.equal(customCss.widths, null);
console.log("Table layout checks passed");
