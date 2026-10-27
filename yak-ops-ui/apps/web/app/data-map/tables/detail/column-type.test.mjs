import assert from "node:assert/strict";
import { test } from "node:test";

import { formatColumnType } from "./column-type.ts";

/** The API omits null properties, so a character column arrives without a `scale` key at all. */
test("a character column shows its length and never a missing scale", () => {
  assert.equal(formatColumnType({ typeName: "VARCHAR", size: 32 }), "VARCHAR(32)");
  assert.equal(formatColumnType({ typeName: "VARCHAR", size: 32, scale: null }), "VARCHAR(32)");
  assert.equal(formatColumnType({ typeName: "CHAR", size: 4 }), "CHAR(4)");
  assert.equal(formatColumnType({ typeName: "VARBINARY", size: 255 }), "VARBINARY(255)");
});

test("an exact numeric keeps precision and scale", () => {
  assert.equal(formatColumnType({ typeName: "DECIMAL", size: 18, scale: 2 }), "DECIMAL(18,2)");
  assert.equal(formatColumnType({ typeName: "NUMERIC", size: 10, scale: 0 }), "NUMERIC(10,0)");
  assert.equal(formatColumnType({ typeName: "NUMBER", size: 12, scale: 4 }), "NUMBER(12,4)");
});

test("an exact numeric without a scale reports precision only", () => {
  assert.equal(formatColumnType({ typeName: "DECIMAL", size: 18 }), "DECIMAL(18)");
  assert.equal(formatColumnType({ typeName: "DECIMAL", size: 18, scale: null }), "DECIMAL(18)");
});

test("integer and temporal columns stay bare", () => {
  assert.equal(formatColumnType({ typeName: "INT", size: 10 }), "INT");
  assert.equal(formatColumnType({ typeName: "BIGINT", size: 19 }), "BIGINT");
  assert.equal(formatColumnType({ typeName: "DATETIME", size: 19 }), "DATETIME");
  assert.equal(formatColumnType({ typeName: "TEXT", size: 65535 }), "TEXT");
  assert.equal(formatColumnType({ typeName: "JSON", size: 4_000_000_000 }), "JSON");
});

test("a column without size or type degrades without placeholders", () => {
  assert.equal(formatColumnType({ typeName: "VARCHAR" }), "VARCHAR");
  assert.equal(formatColumnType({ typeName: "VARCHAR", size: null }), "VARCHAR");
  assert.equal(formatColumnType({ typeName: "VARCHAR", size: 0 }), "VARCHAR");
  assert.equal(formatColumnType({ typeName: "  " }), "-");
  assert.equal(formatColumnType({}), "-");
});

test("no rendered type can contain undefined or null", () => {
  const samples = [
    { typeName: "VARCHAR", size: 32 },
    { typeName: "DECIMAL", size: 18, scale: 2 },
    { typeName: "DECIMAL", size: 18 },
    { typeName: "INT", size: 10 },
    { typeName: "VARCHAR", size: null, scale: null },
    { typeName: null, size: 10, scale: 2 },
    { typeName: "VARCHAR", size: Number.NaN, scale: Number.NaN },
  ];
  for (const sample of samples) {
    const rendered = formatColumnType(sample);
    assert.doesNotMatch(rendered, /undefined|null|NaN/, JSON.stringify(sample));
    assert.doesNotMatch(rendered, /,\s*\)/, JSON.stringify(sample));
  }
});
