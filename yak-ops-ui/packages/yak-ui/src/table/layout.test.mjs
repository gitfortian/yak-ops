import assert from "node:assert/strict";
import { test } from "node:test";

import { applyColumnWidthOverrides } from "./layout.ts";

test("overrides replace resolved widths by index", () => {
  const merged = applyColumnWidthOverrides([240, 200, 110], [null, 320, null]);
  assert.deepEqual(merged, [240, 320, 110]);
});

test("overrides without any value keep the resolved widths", () => {
  const resolved = [240, 200];
  assert.equal(applyColumnWidthOverrides(resolved, [null, null]), resolved);
  assert.equal(applyColumnWidthOverrides(resolved, undefined), resolved);
  assert.equal(applyColumnWidthOverrides(resolved, null), resolved);
});

test("overrides beyond the column count are ignored", () => {
  assert.deepEqual(applyColumnWidthOverrides([100], [null, 999]), [100]);
});

test("null widths resolve to no overrides at all", () => {
  assert.equal(applyColumnWidthOverrides(null, [100, 200]), null);
});
