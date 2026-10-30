import assert from "node:assert/strict";
import { test } from "node:test";
import {
  countByModelKey,
  layerCounts,
  modelsOfNode,
  nodeKeyForView,
  nodeKeyOf,
  parseWorkbenchView,
  pinnedScopeOf,
  publicationCounts,
  resolveNode,
  sameNode,
} from "./workbench-views.ts";

const tree = {
  categories: [
    {
      id: "cat-1",
      name: "电商业务",
      children: [
        {
          id: "domain-1",
          name: "交易域",
          children: [{ id: "process-1", name: "商品支付", children: [] }],
        },
      ],
    },
  ],
  datamarts: [{ id: "mart-1", name: "电商交易集市", children: [] }],
};
const dimensions = [
  {
    id: "dim-1",
    scope: "PUBLIC",
    domainId: "domain-1",
    domainName: "交易域",
    datamartId: null,
    datamartName: null,
  },
  {
    id: "dim-2",
    scope: "APPLICATION",
    domainId: null,
    domainName: null,
    datamartId: "mart-1",
    datamartName: "电商交易集市",
  },
];
const layers = [
  { id: "layer-dwd", enCode: "dwd", cnName: "明细层" },
  { id: "layer-ads", enCode: "ads", cnName: "应用层" },
];
const model = (overrides = {}) => ({
  id: "model-1",
  layerId: "layer-dwd",
  categoryId: "cat-1",
  domainId: "domain-1",
  processId: "process-1",
  dimensionId: null,
  datamartId: null,
  latestPublicationStatus: null,
  ...overrides,
});
const models = [
  model({ id: "m-1" }),
  model({ id: "m-2", layerId: "layer-ads", datamartId: "mart-1", domainId: null, processId: null }),
  model({ id: "m-3", latestPublicationStatus: "SUCCEEDED" }),
  model({ id: "m-4", latestPublicationStatus: "FAILED" }),
];

test("the view parameter falls back to the layer view on anything unknown", () => {
  assert.equal(parseWorkbenchView("layer"), "layer");
  assert.equal(parseWorkbenchView("publish"), "publish");
  assert.equal(parseWorkbenchView(null), "layer");
  assert.equal(parseWorkbenchView("tree"), "layer");
});

test("a node round-trips through its key and resolves against the catalog", () => {
  const selections = [
    { kind: "ROOT" },
    { kind: "LAYER", layer: layers[0] },
    { kind: "CATEGORY", category: tree.categories[0] },
    { kind: "DOMAIN", category: tree.categories[0], domain: tree.categories[0].children[0] },
    {
      kind: "PROCESS",
      category: tree.categories[0],
      domain: tree.categories[0].children[0],
      process: tree.categories[0].children[0].children[0],
    },
    { kind: "DIMENSION", dimension: dimensions[0] },
    { kind: "DATAMART", datamart: tree.datamarts[0] },
    { kind: "PUBLICATION", status: null },
    { kind: "PUBLICATION", status: "FAILED" },
  ];
  for (const selection of selections) {
    const restored = resolveNode(nodeKeyOf(selection), tree, dimensions, layers);
    assert.ok(restored, `key ${nodeKeyOf(selection)} should resolve`);
    assert.equal(sameNode(selection, restored), true);
  }
});

test("unresolvable or malformed keys return null instead of guessing", () => {
  assert.equal(resolveNode("layer:missing", tree, dimensions, layers), null);
  assert.equal(resolveNode("process:gone", tree, dimensions, layers), null);
  assert.equal(resolveNode("pub:WEIRD", tree, dimensions, layers), null);
  assert.equal(resolveNode("nope", tree, dimensions, layers), null);
  assert.equal(resolveNode("layer:", tree, dimensions, layers), null);
  assert.deepEqual(resolveNode(null, tree, dimensions, layers), { kind: "ROOT" });
});

test("a node key survives a view switch only when that view renders its kind", () => {
  assert.equal(nodeKeyForView("layer:layer-dwd", "layer"), "layer:layer-dwd");
  assert.equal(nodeKeyForView("layer:layer-dwd", "biz"), "root");
  assert.equal(nodeKeyForView("process:process-1", "biz"), "process:process-1");
  assert.equal(nodeKeyForView("process:process-1", "publish"), "root");
  assert.equal(nodeKeyForView("pub:FAILED", "publish"), "pub:FAILED");
  assert.equal(nodeKeyForView("root", "biz"), "root");
});

test("each node kind selects exactly the models it owns", () => {
  assert.equal(modelsOfNode(models, { kind: "ROOT" }).length, 4);
  assert.deepEqual(
    modelsOfNode(models, { kind: "LAYER", layer: layers[1] }).map((item) => item.id),
    ["m-2"],
  );
  assert.deepEqual(
    modelsOfNode(models, {
      kind: "PROCESS",
      category: tree.categories[0],
      domain: tree.categories[0].children[0],
      process: tree.categories[0].children[0].children[0],
    }).map((item) => item.id),
    ["m-1", "m-3", "m-4"],
  );
  assert.deepEqual(
    modelsOfNode(models, { kind: "DATAMART", datamart: tree.datamarts[0] }).map((item) => item.id),
    ["m-2"],
  );
  assert.deepEqual(
    modelsOfNode(models, { kind: "PUBLICATION", status: "FAILED" }).map((item) => item.id),
    ["m-4"],
  );
  // A model never published and a status the build does not know both land in the none group.
  assert.deepEqual(
    modelsOfNode(models, { kind: "PUBLICATION", status: null }).map((item) => item.id),
    ["m-1", "m-2"],
  );
});

test("pinned scope follows the node; layer and publication nodes pin nothing", () => {
  assert.deepEqual(pinnedScopeOf({ kind: "ROOT" }), { domain: null, datamart: null });
  assert.deepEqual(pinnedScopeOf({ kind: "LAYER", layer: layers[0] }), {
    domain: null,
    datamart: null,
  });
  assert.deepEqual(
    pinnedScopeOf({
      kind: "DOMAIN",
      category: tree.categories[0],
      domain: tree.categories[0].children[0],
    }).domain,
    { id: "domain-1", name: "交易域" },
  );
  assert.deepEqual(pinnedScopeOf({ kind: "DIMENSION", dimension: dimensions[1] }).datamart, {
    id: "mart-1",
    name: "电商交易集市",
  });
});

test("layer counts stay zero for empty layers", () => {
  const counts = layerCounts(layers, models);
  assert.equal(counts.get("layer-dwd"), 3);
  assert.equal(counts.get("layer-ads"), 1);
});

test("countByModelKey skips models without the key", () => {
  const counts = countByModelKey(models, (item) => item.processId);
  assert.equal(counts.get("process-1"), 3);
  assert.equal(counts.size, 1);
});

test("publication counts group by outcome and count unknown values as unpublished", () => {
  const counts = publicationCounts([
    ...models,
    model({ id: "m-5", latestPublicationStatus: "PENDING" }),
  ]);
  assert.equal(counts.none, 2);
  assert.equal(counts.byStatus.get("SUCCEEDED"), 1);
  assert.equal(counts.byStatus.get("FAILED"), 1);
  assert.equal(counts.byStatus.get("PENDING"), 1);
  assert.equal(counts.byStatus.has("UNKNOWN"), false);
});
