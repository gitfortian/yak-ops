import assert from "node:assert/strict";
import { test } from "node:test";
import { EMPTY_MODEL_SEARCH, isModelSearching, matchesModelFilters } from "./filters.ts";

const model = (overrides = {}) => ({
  id: "model-1",
  type: "DIM",
  name: "dim_customer",
  cnName: "客户维度",
  layerId: "layer-public",
  ownerId: "user-1",
  domainId: "domain-order",
  datamartId: null,
  ...overrides,
});

test("an empty filter set matches every model", () => {
  assert.equal(matchesModelFilters(model(), EMPTY_MODEL_SEARCH), true);
  assert.equal(
    matchesModelFilters(model({ domainId: null, datamartId: "mart-a" }), EMPTY_MODEL_SEARCH),
    true,
  );
  assert.equal(isModelSearching(EMPTY_MODEL_SEARCH), false);
});

test("the keyword matches the table name and the Chinese name, ignoring case and blanks", () => {
  assert.equal(matchesModelFilters(model(), { ...EMPTY_MODEL_SEARCH, keyword: "CUSTOMER" }), true);
  assert.equal(matchesModelFilters(model(), { ...EMPTY_MODEL_SEARCH, keyword: " 客户 " }), true);
  assert.equal(matchesModelFilters(model(), { ...EMPTY_MODEL_SEARCH, keyword: "order" }), false);
  assert.equal(isModelSearching({ ...EMPTY_MODEL_SEARCH, keyword: "  " }), false);
});

test("each attribute filter narrows the list on its own field", () => {
  const filters = { ...EMPTY_MODEL_SEARCH, type: "DIM" };
  assert.equal(matchesModelFilters(model(), filters), true);
  assert.equal(matchesModelFilters(model({ type: "DWD" }), filters), false);

  assert.equal(
    matchesModelFilters(model(), { ...EMPTY_MODEL_SEARCH, layerId: "layer-public" }),
    true,
  );
  assert.equal(
    matchesModelFilters(model(), { ...EMPTY_MODEL_SEARCH, layerId: "layer-app" }),
    false,
  );
  assert.equal(matchesModelFilters(model(), { ...EMPTY_MODEL_SEARCH, ownerId: "user-2" }), false);
  assert.equal(
    matchesModelFilters(model(), { ...EMPTY_MODEL_SEARCH, domainId: "domain-order" }),
    true,
  );
  // A model owns either a domain or a data mart, so the other filter cannot match it.
  assert.equal(
    matchesModelFilters(model(), { ...EMPTY_MODEL_SEARCH, datamartId: "mart-a" }),
    false,
  );
  assert.equal(
    matchesModelFilters(model({ domainId: null, datamartId: "mart-a" }), {
      ...EMPTY_MODEL_SEARCH,
      datamartId: "mart-a",
    }),
    true,
  );
});

test("a model with an unknown type survives a type filter", () => {
  assert.equal(
    matchesModelFilters(model({ type: null }), { ...EMPTY_MODEL_SEARCH, type: "DIM" }),
    false,
  );
  assert.equal(matchesModelFilters(model({ type: null }), EMPTY_MODEL_SEARCH), true);
});
