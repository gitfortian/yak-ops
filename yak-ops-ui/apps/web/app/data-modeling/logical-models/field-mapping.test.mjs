import assert from "node:assert/strict";
import { test } from "node:test";
import {
  columnToField,
  findDuplicateFieldCodes,
  mapPhysicalColumn,
  normalizeFieldCode,
  normalizePhysicalType,
} from "./field-mapping.ts";

const column = (typeName, extra = {}) => ({ name: "physical_column", typeName, ...extra });

test("normalizes physical type modifiers", () => {
  assert.equal(normalizePhysicalType(" varchar(64) "), "VARCHAR");
  assert.equal(normalizePhysicalType("INT UNSIGNED"), "INT");
  assert.equal(normalizePhysicalType("timestamp   with time zone"), "TIMESTAMP WITH TIME ZONE");
  assert.equal(normalizePhysicalType(null), "");
});

test("maps unambiguous string, integer, date and timestamp types", () => {
  const cases = [
    [column("VARCHAR(64)", { size: 64 }), "STRING", "64"],
    [column("TEXT"), "STRING", null],
    [column("CLOB", { size: 65535 }), "STRING", "65535"],
    [column("INT"), "BIGINT", null],
    [column("BIGINT UNSIGNED"), "BIGINT", null],
    [column("DATE"), "DATE", null],
    [column("DATETIME"), "TIMESTAMP", null],
    [column("TIMESTAMPTZ"), "TIMESTAMP", null],
  ];
  for (const [input, dataType, lengthPrecision] of cases) {
    assert.deepEqual(mapPhysicalColumn(input), {
      kind: "MAPPED",
      dataType,
      lengthPrecision,
      note: null,
    });
  }
});

test("uses JDBC integer metadata for PostgreSQL int2, int4 and int8 names", () => {
  const cases = [
    ["int2", 5],
    ["int4", 4],
    ["int8", -5],
  ];
  for (const [typeName, jdbcType] of cases) {
    const mapping = mapPhysicalColumn(column(typeName, { jdbcType }));
    assert.equal(mapping.kind, "MAPPED");
    assert.equal(mapping.dataType, "BIGINT");
  }
});

test("preserves only complete DECIMAL precision inside the logical contract", () => {
  assert.deepEqual(mapPhysicalColumn(column("DECIMAL(20,2)", { size: 20, scale: 2 })), {
    kind: "MAPPED",
    dataType: "DECIMAL",
    lengthPrecision: "20,2",
    note: null,
  });
  assert.deepEqual(mapPhysicalColumn(column("NUMBER", { size: 38, scale: 0 })), {
    kind: "MAPPED",
    dataType: "DECIMAL",
    lengthPrecision: "38,0",
    note: null,
  });
});

test("requires review for incomplete or unrepresentable DECIMAL metadata", () => {
  const cases = [
    column("DECIMAL", { size: 20 }),
    column("DECIMAL", { scale: 2 }),
    column("NUMBER", { size: 0, scale: 0 }),
    column("NUMBER", { size: 39, scale: 2 }),
    column("NUMBER", { size: 38, scale: -127 }),
    column("DECIMAL", { size: 8, scale: 9 }),
  ];
  for (const input of cases) {
    const mapping = mapPhysicalColumn(input);
    assert.equal(mapping.kind, "REVIEW", JSON.stringify(input));
    assert.ok(mapping.note.length > 0);
  }
});

test("keeps lossy or unsupported types editable but unmapped", () => {
  assert.equal(mapPhysicalColumn(column("JSON")).kind, "MAPPED");
  assert.equal(mapPhysicalColumn(column("JSON")).dataType, "STRING");
  assert.ok(mapPhysicalColumn(column("JSON")).note.includes("文本"));

  for (const typeName of ["BOOL", "DOUBLE", "FLOAT", "REAL"]) {
    assert.equal(mapPhysicalColumn(column(typeName)).kind, "REVIEW");
  }
  for (const typeName of ["BLOB", "BYTEA", "GEOMETRY", "STRUCT<a:int>"]) {
    assert.equal(mapPhysicalColumn(column(typeName)).kind, "UNSUPPORTED");
  }
});

test("normalizes physical names to stable logical codes", () => {
  const cases = new Map([
    ["ORDER_ID", "order_id"],
    ["orderId", "order_id"],
    ["2fa", "f_2fa"],
    ["camelCaseName", "camel_case_name"],
    ["用户 编号", "field"],
  ]);
  for (const [input, expected] of cases) assert.equal(normalizeFieldCode(input), expected);
  assert.equal(normalizeFieldCode("a".repeat(80)).length, 64);
});

test("detects duplicate codes within the same import batch", () => {
  const duplicates = findDuplicateFieldCodes([
    { name: "userName" },
    { name: "user_name" },
    { name: "用户甲" },
    { name: "用户乙" },
    { name: `${"a".repeat(64)}x` },
    { name: `${"a".repeat(64)}y` },
    { name: "order_id" },
  ]);
  assert.deepEqual(duplicates, new Set(["user_name", "field", "a".repeat(64)]));
  assert.equal(findDuplicateFieldCodes([{ name: "order_id" }, { name: "customer_id" }]).size, 0);
});

test("keeps unresolved mapping reasons and physical remarks on the editable row", () => {
  const remarks = "字段备注".repeat(40);
  const row = columnToField(
    column("BLOB", { name: "payload", remarks, primaryKey: true, nullable: false }),
    mapPhysicalColumn(column("BLOB")),
    "field-test",
  );
  assert.equal(row.cnName, remarks);
  assert.equal(row.dataType, null);
  assert.equal(row.importNote, mapPhysicalColumn(column("BLOB")).note);
  assert.equal(row.isPrimaryKey, true);
  assert.equal(row.isNotNull, true);
});
