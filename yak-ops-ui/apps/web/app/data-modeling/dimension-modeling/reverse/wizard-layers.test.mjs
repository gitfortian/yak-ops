import assert from "node:assert/strict";
import { test } from "node:test";
import { defaultReverseLayer, layerBlock } from "./wizard-layers.ts";

const layer = (overrides = {}) => ({
  id: "layer-1",
  enCode: "DWD",
  cnName: "明细层",
  dataSourceId: null,
  dataSourceName: null,
  description: null,
  enabled: true,
  builtin: false,
  namingRuleEnabled: false,
  sortNo: 1,
  revision: 1,
  ...overrides,
});

const WAREHOUSE = [
  layer({ id: "ods", enCode: "ODS", cnName: "贴源层" }),
  layer({ id: "dwd", enCode: "DWD", cnName: "明细层" }),
  layer({ id: "dim", enCode: "DIM", cnName: "公共维度层" }),
  layer({ id: "dws", enCode: "DWS", cnName: "汇总层" }),
  layer({ id: "ads", enCode: "ADS", cnName: "应用层" }),
];

test("defaults the wizard to the kind's own layer, not the first enabled one", () => {
  // The shape the workspace actually returns: ODS is enabled and sorts first, so a default that only
  // asks for an enabled layer files every reverse-modeled detail table in the 贴源 layer.
  assert.equal(WAREHOUSE.find((item) => item.enabled).id, "ods");
  assert.equal(defaultReverseLayer(WAREHOUSE, "DWD")?.id, "dwd");
  assert.equal(defaultReverseLayer(WAREHOUSE, "DIM")?.id, "dim");
});

test("defaults to an enabled layer of the kind's own code even when another is disabled", () => {
  const warehouse = [
    layer({ id: "dwd-a", enCode: "DWD", enabled: false }),
    layer({ id: "dwd-b", enCode: "DWD", enabled: true }),
  ];
  assert.equal(defaultReverseLayer(warehouse, "DWD")?.id, "dwd-b");
});

test("refuses a disabled layer, which the server would reject for having no datasource", () => {
  const warehouse = [layer({ id: "dwd", enCode: "DWD", enabled: false })];
  assert.equal(defaultReverseLayer(warehouse, "DWD"), undefined);
});

test("gives no default when the workspace defines no layer for the kind", () => {
  // Leaving the field empty is what keeps the save from producing an illegal model.
  assert.equal(defaultReverseLayer([layer({ id: "ods", enCode: "ODS" })], "DWD"), undefined);
});

test("blocks a layer that belongs to another kind", () => {
  assert.equal(layerBlock(WAREHOUSE[0], "DWD", true), "mismatch");
  assert.equal(layerBlock(WAREHOUSE[1], "DWD", true), null);
});

test("blocks an unenabled layer before its kind fit", () => {
  assert.equal(
    layerBlock(layer({ id: "dim", enCode: "DIM", enabled: false }), "DIM", true),
    "unenabled",
  );
});

test("keeps every enabled layer pickable when the kind has no layer of its own", () => {
  // Disabling the whole list would strand the page; the gap is stated by the caller instead.
  assert.equal(layerBlock(WAREHOUSE[0], "DWD", false), null);
});
