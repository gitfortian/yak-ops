import assert from "node:assert/strict";
import { test } from "node:test";
import { isJdbcReverseModelingEngine } from "./reverse-modeling-engine.ts";

test("accepts the three JDBC engines the Catalog can read", () => {
  assert.equal(isJdbcReverseModelingEngine("MYSQL"), true);
  assert.equal(isJdbcReverseModelingEngine("ORACLE"), true);
  assert.equal(isJdbcReverseModelingEngine("POSTGRE_SQL"), true);
});

test("accepts a stored alias and ignores case and padding", () => {
  assert.equal(isJdbcReverseModelingEngine("postgresql"), true);
  assert.equal(isJdbcReverseModelingEngine("Postgres"), true);
  assert.equal(isJdbcReverseModelingEngine("  mysql  "), true);
});

test("rejects engines whose metadata is not reachable over JDBC", () => {
  assert.equal(isJdbcReverseModelingEngine("PAIMON"), false);
  assert.equal(isJdbcReverseModelingEngine("HIVE"), false);
  assert.equal(isJdbcReverseModelingEngine(""), false);
  assert.equal(isJdbcReverseModelingEngine("   "), false);
  assert.equal(isJdbcReverseModelingEngine(null), false);
  assert.equal(isJdbcReverseModelingEngine(undefined), false);
});
