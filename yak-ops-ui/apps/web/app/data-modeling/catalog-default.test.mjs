import assert from "node:assert/strict";
import { test } from "node:test";
import { resolveDefaultDatabase } from "./catalog-default.ts";

const INSTANCE = ["ads", "dwd", "dim", "dws", "ods", "item_db", "trade_db"];

test("opens on the database the datasource registers, not the first one", () => {
  // The instance list starts with ads, which is what both audit rounds silently landed on.
  assert.deepEqual(resolveDefaultDatabase("ods", INSTANCE), { database: "ods", hint: "" });
  assert.deepEqual(resolveDefaultDatabase("item_db", INSTANCE), { database: "item_db", hint: "" });
});

test("tolerates the spaces a registration form can carry", () => {
  assert.equal(resolveDefaultDatabase(" dim ", INSTANCE).database, "dim");
});

test("leaves nothing chosen when the registered database is absent", () => {
  const result = resolveDefaultDatabase("staging", INSTANCE);
  assert.equal(result.database, "");
  assert.match(result.hint, /staging/);
  assert.notEqual(result.database, INSTANCE[0]);
});

test("does not invent a database for a source that registered none", () => {
  for (const registered of [null, undefined, "", "   "]) {
    const result = resolveDefaultDatabase(registered, INSTANCE);
    assert.equal(result.database, "", String(registered));
    assert.match(result.hint, /未登记/);
  }
});

test("reports an empty instance without falling back", () => {
  assert.deepEqual(resolveDefaultDatabase("ods", []), {
    database: "",
    hint: "数据源登记库「ods」不在该实例的数据库列表中，请手动选择目标数据库",
  });
});
