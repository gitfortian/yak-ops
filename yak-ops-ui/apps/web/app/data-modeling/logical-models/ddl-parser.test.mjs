import assert from "node:assert/strict";
import { test } from "node:test";
import { parseCreateTable, typeSize } from "./ddl-parser.ts";

const byName = (result, name) => result.fields.find((field) => field.name === name);

test("reads a Doris DDL with decimal precision, nested types and a comma inside COMMENT", () => {
  const ddl = [
    "CREATE TABLE `dwd_trade_order` (",
    "  `order_id` BIGINT NOT NULL COMMENT '订单号',",
    "  `order_amount` DECIMAL(20,2) COMMENT '金额, 含税',",
    "  `ext` STRUCT<a:int, b:string> COMMENT '扩展',",
    "  PRIMARY KEY (`order_id`)",
    ") ENGINE=OLAP",
    "DISTRIBUTED BY HASH(`order_id`) BUCKETS 10;",
  ].join("\n");
  const result = parseCreateTable(ddl);
  assert.equal(result.found, true);
  assert.deepEqual(result.errors, []);
  assert.equal(result.fields.length, 3);

  const id = byName(result, "order_id");
  assert.equal(id.line, 2);
  assert.equal(id.baseType, "BIGINT");
  assert.equal(id.notNull, true);
  assert.equal(id.primaryKey, true, "a table-level PRIMARY KEY marks its columns");

  const amount = byName(result, "order_amount");
  assert.equal(amount.baseType, "DECIMAL");
  assert.equal(amount.typeArgs, "20,2", "the comma inside DECIMAL must not split the field");
  assert.equal(amount.comment, "金额, 含税");

  const ext = byName(result, "ext");
  assert.equal(ext.baseType, "STRUCT");
  assert.equal(ext.typeArgs, "a:int, b:string", "the comma inside STRUCT must not split the field");
  assert.equal(ext.comment, "扩展");
});

test("ignores comment markers before scanning structure", () => {
  const ddl = [
    "CREATE TABLE t (",
    "  a INT, -- don't drop (this)",
    "  `b` STRING COMMENT '金额(含税), 必填',",
    "  /* c, d */ c INT",
    ")",
  ].join("\n");
  const result = parseCreateTable(ddl);
  assert.equal(result.found, true);
  assert.deepEqual(result.errors, []);
  assert.deepEqual(
    result.fields.map((field) => field.name),
    ["a", "b", "c"],
  );
  assert.equal(byName(result, "b").comment, "金额(含税), 必填");
});

test("does not read constraint words out of COMMENT or DEFAULT text", () => {
  const ddl = [
    "CREATE TABLE t (",
    "  a STRING COMMENT 'not null 必填',",
    "  b BIGINT COMMENT 'primary key',",
    "  c STRING DEFAULT 'NOT NULL',",
    "  d INT NOT NULL",
    ")",
  ].join("\n");
  const result = parseCreateTable(ddl);
  assert.deepEqual(result.errors, []);
  assert.equal(byName(result, "a").notNull, false);
  assert.equal(byName(result, "a").primaryKey, false);
  assert.equal(byName(result, "b").primaryKey, false);
  assert.equal(byName(result, "c").notNull, false);
  assert.equal(byName(result, "d").notNull, true);
});

test("fails only the malformed fragment and reports its real line", () => {
  const ddl = [
    "CREATE TABLE t (",
    "  good_a INT COMMENT 'A',",
    "  good_b INT,",
    "  broken,",
    "  good_c STRING COMMENT 'C'",
    ")",
  ].join("\n");
  const result = parseCreateTable(ddl);
  assert.equal(result.fields.length, 4, "a field without a type still keeps its row");
  assert.equal(byName(result, "broken").baseType, "");
  assert.equal(result.errors.length, 1);
  assert.match(result.errors[0], /第 4 行/);
  assert.match(result.errors[0], /broken/);
});

test("reports one whole-statement message when no field body exists", () => {
  const result = parseCreateTable("CREATE TABLE t LIKE other;");
  assert.equal(result.found, false);
  assert.equal(result.fields.length, 0);
  assert.equal(result.errors.length, 1);
  assert.match(result.errors[0], /字段定义区/);
});

test("reports an empty field body instead of silently importing nothing", () => {
  const result = parseCreateTable("CREATE TABLE t (PRIMARY KEY (id))");
  assert.equal(result.found, true);
  assert.equal(result.fields.length, 0);
  assert.equal(result.errors.length, 1);
  assert.match(result.errors[0], /字段定义区为空/);
});

test("ignores table-level indexes and keys without reporting them as fields", () => {
  const ddl = [
    "CREATE TABLE t (",
    "  id BIGINT,",
    "  name STRING,",
    "  INDEX idx_name (name),",
    "  UNIQUE KEY uk_id (id),",
    "  KEY k_id (id),",
    "  COMMENT '表注释'",
    ")",
  ].join("\n");
  const result = parseCreateTable(ddl);
  assert.deepEqual(
    result.fields.map((field) => field.name),
    ["id", "name"],
  );
  assert.deepEqual(result.errors, []);
});

test("accepts backticks, double quotes and brackets, and keeps comment markers inside quotes", () => {
  const ddl = [
    'CREATE TABLE "t" (',
    "  \"orderId\" VARCHAR(64) COMMENT '含,逗号',",
    "  [user_name] STRING COMMENT '方括号',",
    "  `remark` TEXT COMMENT 'A--B',",
    "  `plain` INT",
    ")",
  ].join("\n");
  const result = parseCreateTable(ddl);
  assert.deepEqual(result.errors, []);
  assert.equal(byName(result, "orderId").comment, "含,逗号");
  assert.equal(byName(result, "user_name").baseType, "STRING");
  assert.equal(byName(result, "remark").comment, "A--B", "a comment marker inside quotes is data");
  assert.equal(byName(result, "plain").comment, null);
});

test("unescapes a doubled or backslash-escaped quote in COMMENT", () => {
  const ddl = [
    "CREATE TABLE t (",
    "  a STRING COMMENT 'It\\'s fine',",
    "  b STRING COMMENT 'tag ''x'''",
    ")",
  ].join("\n");
  const result = parseCreateTable(ddl);
  assert.equal(byName(result, "a").comment, "It's fine");
  assert.equal(byName(result, "b").comment, "tag 'x'");
});

test("reads inline constraints and a named table-level primary key", () => {
  const ddl = [
    "CREATE TABLE t (",
    "  /* 主键 */ id BIGINT,",
    "  status STRING NOT NULL DEFAULT 'NEW',",
    "  amount DECIMAL(38,0) COMMENT '金额',",
    "  CONSTRAINT pk_t PRIMARY KEY (id)",
    ")",
  ].join("\n");
  const result = parseCreateTable(ddl);
  assert.deepEqual(result.errors, []);
  assert.equal(byName(result, "id").primaryKey, true, "CONSTRAINT name must not hide the key");
  assert.equal(byName(result, "id").line, 2);
  assert.equal(byName(result, "status").notNull, true);
  assert.equal(byName(result, "amount").line, 4);
  assert.equal(byName(result, "amount").typeArgs, "38,0");
});

test("recognises CREATE OR REPLACE, EXTERNAL and TEMPORARY TABLE", () => {
  for (const head of [
    "CREATE OR REPLACE TABLE t (id BIGINT)",
    "CREATE EXTERNAL TABLE t (id BIGINT)",
    "CREATE TEMPORARY TABLE t (id BIGINT)",
  ]) {
    const result = parseCreateTable(head);
    assert.equal(result.found, true, head);
    assert.equal(result.fields.length, 1, head);
    assert.equal(result.fields[0].name, "id", head);
  }
});

test("says so when several statements were pasted but only the first is read", () => {
  const result = parseCreateTable(
    ["CREATE TABLE a (x INT);", "CREATE TABLE b (y INT);"].join("\n"),
  );
  assert.equal(result.fields.length, 1);
  assert.equal(result.fields[0].name, "x");
  assert.equal(result.errors.length, 1);
  assert.match(result.errors[0], /多条 CREATE TABLE/);
});

test("splits type arguments into a size and scale the logical type accepts", () => {
  assert.deepEqual(typeSize("20,2"), { size: 20, scale: 2 });
  assert.deepEqual(typeSize("64"), { size: 64 });
  assert.deepEqual(typeSize(null), {});
  assert.deepEqual(typeSize("0,0"), {});
});
