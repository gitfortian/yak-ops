import assert from "node:assert/strict";
import { test } from "node:test";
import { FIELD_DESCRIPTION_MAX } from "./field-mapping.ts";
import {
  CN_NAME_MAX,
  describeFieldDrafts,
  draftTableCnName,
  draftTableName,
  toModelField,
} from "./reverse-modeling-draft.ts";

const column = (name, extra = {}) => ({ name, typeName: "VARCHAR", size: 64, ...extra });

test("derives the Chinese name and description from the column comment", () => {
  const [draft] = describeFieldDrafts([
    column("order_no", {
      remarks: "订单号",
      primaryKey: true,
      nullable: false,
      size: 32,
    }),
  ]);
  assert.equal(draft.enCode, "order_no");
  assert.equal(draft.cnName, "订单号");
  assert.equal(draft.description, "订单号");
  assert.equal(draft.dataType, "STRING");
  assert.equal(draft.lengthPrecision, "32");
  assert.equal(draft.isPrimaryKey, true);
  assert.equal(draft.isNotNull, true);
  assert.equal(draft.isPartition, false);
  assert.equal(draft.aggregateType, null);
  assert.equal(draft.importNote, null);
});

test("falls back to the physical name when the column has no comment", () => {
  const [draft] = describeFieldDrafts([column("order_no", { remarks: null })]);
  assert.equal(draft.cnName, "order_no");
  assert.equal(draft.description, null);
});

test("truncates long comments to the server limits", () => {
  const long = "订".repeat(600);
  const [draft] = describeFieldDrafts([column("order_no", { remarks: long })]);
  assert.equal(draft.cnName.length, CN_NAME_MAX);
  assert.equal(draft.description.length, FIELD_DESCRIPTION_MAX);
});

test("keeps unmapped columns with a reason instead of dropping them", () => {
  const drafts = describeFieldDrafts([
    column("payload", { typeName: "BLOB" }),
    column("amount", { typeName: "DECIMAL", size: null, scale: null }),
  ]);
  assert.equal(drafts.length, 2);
  assert.equal(drafts[0].dataType, null);
  assert.match(drafts[0].importNote, /二进制/);
  assert.equal(drafts[1].dataType, null);
  assert.match(drafts[1].importNote, /DECIMAL/);
});

test("reports duplicate derived codes without renaming them", () => {
  const drafts = describeFieldDrafts([column("order-no"), column("order_no")]);
  assert.equal(drafts[0].enCode, drafts[1].enCode);
  assert.equal(drafts[1].importNote, "编码与第 1 行重复");
});

test("keeps the physical origin on each draft when the source table is known", () => {
  const [draft] = describeFieldDrafts([column("Amount")], "ods.trade_order_detail");
  assert.equal(draft.enCode, "amount");
  assert.deepEqual(draft.source, { table: "ods.trade_order_detail", column: "Amount" });
});

test("leaves the origin empty when no source table is known", () => {
  assert.equal(describeFieldDrafts([column("amount")])[0].source, null);
});

test("narrows only complete drafts to the declared seam field shape", () => {
  const [mapped, unmapped] = describeFieldDrafts([
    column("order_no", { remarks: "订单号" }),
    column("payload", { typeName: "BLOB" }),
  ]);
  assert.equal(toModelField(unmapped), null);
  const field = toModelField(mapped);
  assert.equal(field.enCode, "order_no");
  assert.equal(field.cnName, "订单号");
  assert.equal(field.dataType, "STRING");
  assert.equal(field.lengthPrecision, "64");
  assert.equal(field.description, "订单号");
});

test("strips the selected layer prefix from the physical table name", () => {
  assert.equal(draftTableName("dwd_order_detail", "dwd"), "order_detail");
  assert.equal(draftTableName("DWD_ORDER_DETAIL", "DWD"), "order_detail");
  assert.equal(draftTableName("order_detail", "dwd"), "order_detail");
  assert.equal(draftTableName("dwd_order_detail", null), "dwd_order_detail");
  assert.equal(draftTableName("dwd_order-detail", "dwd"), "order-detail");
});

test("does not coerce a stripped name that still violates the naming rule", () => {
  assert.equal(draftTableName("dwd_1order", "dwd"), "1order");
  assert.equal(draftTableName("dwd_", "dwd"), "");
});

test("passes an over-length suffix through instead of truncating it", () => {
  const long = `${"dwd_"}${"a".repeat(60)}`;
  // Truncation would silently rename the table; the wizard rejects it through the shared rule.
  assert.equal(draftTableName(long, "dwd"), "a".repeat(60));
});

test("uses the table comment for the Chinese name and falls back to the name", () => {
  assert.equal(draftTableCnName({ name: "dwd_order", remarks: "订单明细" }), "订单明细");
  assert.equal(draftTableCnName({ name: "dwd_order", remarks: "  " }), "dwd_order");
  assert.equal(
    draftTableCnName({ name: "dwd_order", remarks: "表".repeat(200) }).length,
    CN_NAME_MAX,
  );
});
