import assert from "node:assert/strict";
import { test } from "node:test";
import { toTargetPayload, validatePublicationTarget } from "./publication-target.ts";

test("requires an environment before anything else", () => {
  assert.deepEqual(validatePublicationTarget("", "MYSQL"), {
    field: "environment",
    message: "请选择发布环境（开发或生产）",
  });
  assert.deepEqual(validatePublicationTarget("TEST", "MYSQL")?.field, "environment");
});

test("refuses an engine it cannot connect to", () => {
  assert.equal(validatePublicationTarget("DEVELOP", "HIVE")?.field, "engine");
  assert.equal(validatePublicationTarget("PROD", null)?.field, "engine");
});

test("accepts both publication environments on the supported engines", () => {
  assert.equal(validatePublicationTarget("DEVELOP", "MYSQL"), null);
  assert.equal(validatePublicationTarget("PROD", "DORIS"), null);
  assert.equal(validatePublicationTarget("PROD", "POSTGRE_SQL"), null);
});

test("submits the environment and the loss acknowledgement only", () => {
  assert.deepEqual(toTargetPayload("DEVELOP"), {
    environment: "DEVELOP",
    acknowledgeDataLoss: false,
  });
  assert.deepEqual(toTargetPayload("PROD", true), {
    environment: "PROD",
    acknowledgeDataLoss: true,
  });
});
