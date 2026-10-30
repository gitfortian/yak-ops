import assert from "node:assert/strict";
import { test } from "node:test";
import {
  defaultLayerForType,
  layersForType,
  layerMatchesType,
  modelTypeFromCode,
} from "./layer-binding.ts";

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

test("binds every model kind to the layer code it is named after", () => {
  assert.equal(layerMatchesType(layer({ id: "dwd", enCode: "DWD" }), "DWD"), true);
  assert.equal(layerMatchesType(layer({ id: "dim", enCode: "DIM" }), "DWD"), false);
  assert.equal(layerMatchesType(layer({ id: "ads", enCode: "ADS" }), "DWS"), false);
  assert.equal(layerMatchesType(undefined, "DWD"), false);
});

test("compares layer codes without case or surrounding space", () => {
  assert.equal(layerMatchesType(layer({ enCode: " dwd " }), "DWD"), true);
});

test("never offers a layer that belongs to another kind", () => {
  assert.deepEqual(
    layersForType(WAREHOUSE, "DWD").map((item) => item.id),
    ["dwd"],
  );
  assert.deepEqual(
    layersForType(WAREHOUSE, "ADS").map((item) => item.id),
    ["ads"],
  );
  // The raw layer (ODS) is named by no kind, so it is never offered either.
  assert.deepEqual(
    layersForType(WAREHOUSE, "DWS").map((item) => item.id),
    ["dws"],
  );
});

test("preselects the first enabled match and falls back to a disabled one", () => {
  const warehouse = [
    layer({ id: "dwd-a", enCode: "DWD", enabled: false }),
    layer({ id: "dwd-b", enCode: "DWD", enabled: true }),
  ];
  assert.equal(defaultLayerForType(warehouse, "DWD")?.id, "dwd-b");
  assert.equal(
    defaultLayerForType([layer({ id: "dwd-c", enCode: "DWD", enabled: false })], "DWD")?.id,
    "dwd-c",
  );
  assert.equal(defaultLayerForType(warehouse, "DIM"), undefined);
});

test("decodes every kind a tree node can name", () => {
  for (const kind of ["DWD", "DIM", "DWS", "ADS"]) {
    assert.equal(modelTypeFromCode(kind), kind);
  }
});

test("names no kind for a value that is not one", () => {
  // ODS and STG are layers no model kind is bound to, so such a link must not pick a type.
  assert.equal(modelTypeFromCode("ODS"), null);
  assert.equal(modelTypeFromCode("STG"), null);
  assert.equal(modelTypeFromCode("dwd"), null);
  assert.equal(modelTypeFromCode(""), null);
  assert.equal(modelTypeFromCode(null), null);
});

test("a decoded entry kind lands on its own layer", () => {
  // The reported failure was a 汇总表 link that rendered as a 明细表 over the 明细层.
  assert.equal(defaultLayerForType(WAREHOUSE, modelTypeFromCode("DWS")).id, "dws");
  assert.equal(defaultLayerForType(WAREHOUSE, modelTypeFromCode("ADS")).id, "ads");
});

test("the reverse wizard default is the kind's layer, not the first enabled one", () => {
  // Sorted with ODS first, the shape the workspace returns: a default that only looks for an enabled
  // layer picks the 贴源 layer and every save yields a model the editor refuses to publish.
  assert.equal(defaultLayerForType(WAREHOUSE, "DWD")?.id, "dwd");
  assert.equal(defaultLayerForType(WAREHOUSE, "DIM")?.id, "dim");
  assert.equal(WAREHOUSE.find((item) => item.enabled).id, "ods");
});

test("a kind with no layer of its own has no default at all", () => {
  // Refusing to invent one is what keeps the wizard from storing an illegal model.
  assert.equal(defaultLayerForType([layer({ id: "ods", enCode: "ODS" })], "DWD"), undefined);
  assert.deepEqual(layersForType([layer({ id: "ods", enCode: "ODS" })], "DWD"), []);
});
