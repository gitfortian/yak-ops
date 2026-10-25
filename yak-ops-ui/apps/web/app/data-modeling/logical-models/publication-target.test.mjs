import assert from "node:assert/strict";
import { test } from "node:test";
import {
  describeAddress,
  toTargetPayload,
  validatePublicationTarget,
} from "./publication-target.ts";

test("requires a database before anything else", () => {
  assert.deepEqual(validatePublicationTarget({ dbType: "MYSQL", database: "  ", schema: null }), {
    field: "database",
    message: "请选择目标数据库",
  });
});

test("refuses an engine it cannot connect to", () => {
  assert.equal(
    validatePublicationTarget({ dbType: "HIVE", database: "dw", schema: null })?.field,
    "engine",
  );
  assert.equal(
    validatePublicationTarget({ dbType: null, database: "dw", schema: null })?.field,
    "engine",
  );
});

test("demands a schema on the engines that namespace by it", () => {
  assert.equal(
    validatePublicationTarget({ dbType: "POSTGRE_SQL", database: "dw", schema: null })?.field,
    "schema",
  );
  assert.equal(
    validatePublicationTarget({ dbType: "ORACLE", database: "dw", schema: "" })?.field,
    "schema",
  );
  assert.equal(
    validatePublicationTarget({ dbType: "ORACLE", database: "dw", schema: "EDW" }),
    null,
  );
});

test("refuses a surplus schema on MySQL, where the database is the schema", () => {
  assert.equal(
    validatePublicationTarget({ dbType: "MYSQL", database: "dw", schema: "public" })?.field,
    "schema",
  );
  assert.equal(validatePublicationTarget({ dbType: "MYSQL", database: "dw", schema: null }), null);
});

test("renders the address the confirmation sentence shows", () => {
  assert.equal(describeAddress("dw", "public", "POSTGRE_SQL"), "dw.public");
  assert.equal(describeAddress("dw", "  ", "ORACLE"), "dw");
  assert.equal(describeAddress(" dw ", "ignored", "MYSQL"), "dw");
});

test("never sends a schema the engine does not have", () => {
  assert.deepEqual(toTargetPayload({ dbType: "MYSQL", database: " dw ", schema: "public" }), {
    database: "dw",
    schema: null,
  });
  assert.deepEqual(toTargetPayload({ dbType: "postgresql", database: "dw", schema: " public " }), {
    database: "dw",
    schema: "public",
  });
});
