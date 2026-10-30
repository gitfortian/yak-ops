import assert from "node:assert/strict";
import { test } from "node:test";
import {
  applyStandard,
  bindingWarningNote,
  fromStandard,
  inheritStandard,
  standardBindingWarning,
} from "./standard-binding.ts";

const standard = (overrides = {}) => ({
  id: "standard-1",
  cnName: "商品ID",
  enCode: "item_id",
  dataType: "STRING",
  lengthPrecision: "32",
  isNotNull: true,
  ...overrides,
});

const row = (overrides = {}) => ({
  key: "field-90",
  cnName: "旧名称",
  enCode: "old_code",
  dataType: "BIGINT",
  lengthPrecision: "",
  isPrimaryKey: true,
  isNotNull: false,
  isPartition: true,
  aggregateType: "SUM",
  standardId: null,
  standardEnCode: null,
  lookupId: "lookup-1",
  lookupEnCode: "lookup_code",
  measureUnitId: "unit-1",
  measureUnitEnCode: "unit_code",
  metricId: "metric-1",
  description: "保留的描述",
  importNote: "待确认",
  source: null,
  ...overrides,
});

test("the standard owns the four attributes the server rewrites", () => {
  assert.deepEqual(inheritStandard(standard()), {
    cnName: "商品ID",
    enCode: "item_id",
    dataType: "STRING",
    lengthPrecision: "32",
    isNotNull: true,
    standardId: "standard-1",
    standardEnCode: "item_id",
  });
});

test("binding shows the standard's name, code, type and length immediately", () => {
  const bound = applyStandard(row(), standard());
  assert.equal(bound.cnName, "商品ID");
  assert.equal(bound.enCode, "item_id");
  assert.equal(bound.dataType, "STRING");
  assert.equal(bound.lengthPrecision, "32");
  assert.equal(bound.isNotNull, true);
  assert.equal(bound.standardId, "standard-1");
  assert.equal(bound.standardEnCode, "item_id");
  assert.equal(bound.importNote, null);
  // The row keeps its own identity and the attributes no standard owns.
  assert.equal(bound.key, "field-90");
  assert.equal(bound.isPrimaryKey, true);
  assert.equal(bound.isPartition, true);
  assert.equal(bound.aggregateType, "SUM");
  assert.equal(bound.lookupId, "lookup-1");
  assert.equal(bound.measureUnitId, "unit-1");
  assert.equal(bound.metricId, "metric-1");
  assert.equal(bound.description, "保留的描述");
});

test("an empty standard length becomes an empty draft length", () => {
  assert.equal(applyStandard(row(), standard({ lengthPrecision: null })).lengthPrecision, "");
});

test("importing a standard creates a bare row without links or description", () => {
  const imported = fromStandard(standard());
  assert.equal(imported.cnName, "商品ID");
  assert.equal(imported.enCode, "item_id");
  assert.equal(imported.dataType, "STRING");
  assert.equal(imported.lengthPrecision, "32");
  assert.equal(imported.isNotNull, true);
  assert.equal(imported.standardId, "standard-1");
  assert.equal(imported.description, null);
  assert.equal(imported.lookupId, null);
  assert.equal(imported.lookupEnCode, null);
  assert.equal(imported.measureUnitId, null);
  assert.equal(imported.measureUnitEnCode, null);
  assert.equal(imported.metricId, null);
  assert.equal(imported.aggregateType, null);
  assert.equal(imported.isPrimaryKey, false);
  assert.equal(imported.isPartition, false);
  assert.equal(imported.importNote, null);
  assert.equal(imported.source, null);
  assert.match(imported.key, /^field-\d+$/);
  assert.notEqual(fromStandard(standard()).key, imported.key);
});

const sourcedRow = (overrides = {}) =>
  row({
    cnName: "销售金额",
    enCode: "amount",
    dataType: "DECIMAL",
    source: { table: "ods.trade_order_detail", column: "amount" },
    ...overrides,
  });

const salesAmountStandard = (overrides = {}) =>
  standard({
    cnName: "商品销售金额",
    enCode: "item_sales_amount",
    dataType: "DECIMAL",
    lengthPrecision: "20,2",
    ...overrides,
  });

test("warns before a standard moves an imported column off its source code", () => {
  assert.deepEqual(standardBindingWarning(sourcedRow(), salesAmountStandard()), {
    standardCnName: "商品销售金额",
    sourceTable: "ods.trade_order_detail",
    sourceColumn: "amount",
    currentCode: "amount",
    nextCode: "item_sales_amount",
  });
});

test("stays quiet for hand-added rows and for a binding that keeps the code", () => {
  assert.equal(standardBindingWarning(row(), salesAmountStandard()), null);
  assert.equal(
    standardBindingWarning(sourcedRow({ enCode: "item_sales_amount" }), salesAmountStandard()),
    null,
  );
});

test("stays quiet when the binding lands back on the physical column", () => {
  const restored = sourcedRow({ enCode: "item_sales_amount" });
  assert.equal(standardBindingWarning(restored, salesAmountStandard({ enCode: "amount" })), null);
});

test("a confirmed break leaves a row note naming the source and both codes", () => {
  const warning = standardBindingWarning(sourcedRow(), salesAmountStandard());
  const note = bindingWarningNote(warning);
  assert.match(note, /商品销售金额/);
  assert.match(note, /amount/);
  assert.match(note, /item_sales_amount/);
  assert.match(note, /ods\.trade_order_detail/);
  assert.equal(applyStandard(sourcedRow(), salesAmountStandard(), note).importNote, note);
});

test("a binding without a break still clears the previous row note", () => {
  assert.equal(
    applyStandard(sourcedRow(), salesAmountStandard({ enCode: "amount" })).importNote,
    null,
  );
});
