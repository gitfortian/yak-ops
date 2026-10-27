import assert from "node:assert/strict";
import { test } from "node:test";
import {
  composeSuffix,
  decomposeSuffix,
  qualifierMaxLength,
  rootSearchSegment,
  sanitizeQualifier,
  stripLayerPrefix,
} from "./naming-composer.ts";

test("keeps digit-led qualifier tails the backend grammar accepts", () => {
  assert.equal(sanitizeQualifier("1d"), "1d");
  assert.equal(sanitizeQualifier("24H "), "24h");
  assert.equal(sanitizeQualifier("Order-2026!"), "order2026");
  assert.equal(sanitizeQualifier("_a_b"), "_a_b");
  assert.equal(sanitizeQualifier(""), "");
});

test("composes root_process plus an optional qualifier", () => {
  assert.equal(composeSuffix("trade", "order", ""), "trade_order");
  assert.equal(composeSuffix("trade", "order", "1d"), "trade_order_1d");
  assert.equal(composeSuffix("", "order", "1d"), "");
  assert.equal(composeSuffix("trade", "", "1d"), "");
});

test("decomposes a saved suffix back into its qualifier tail", () => {
  assert.equal(decomposeSuffix("trade_order_1d", "trade", "order"), "1d");
  assert.equal(decomposeSuffix("trade_order", "trade", "order"), "");
  assert.equal(decomposeSuffix("trade_order_1d", "trade", "refund"), "");
  assert.equal(decomposeSuffix("trade_order_1d", null, "order"), "");
  assert.equal(decomposeSuffix("trade_order_1d", "trade", ""), "");
});

test("resolves the keyword segment used to find a saved root beyond the first page", () => {
  assert.equal(rootSearchSegment("trade_order_1d"), "trade");
  assert.equal(rootSearchSegment("t_"), "t");
  assert.equal(rootSearchSegment(""), "");
});

test("drops a layer prefix already typed into the suffix", () => {
  assert.equal(stripLayerPrefix("dwd_trade_order", "dwd"), "trade_order");
  assert.equal(stripLayerPrefix("dwd_dwd_trade_order", "dwd"), "trade_order");
  assert.equal(stripLayerPrefix("trade_order", "dwd"), "trade_order");
  assert.equal(stripLayerPrefix("dw_trade_order", "dwd"), "dw_trade_order");
  assert.equal(stripLayerPrefix("trade_order", ""), "trade_order");
});

test("strips an upper-cased prefix without rewriting the rest of the name", () => {
  assert.equal(stripLayerPrefix("DWD_trade_order", "dwd"), "trade_order");
  assert.equal(stripLayerPrefix("Dwd_DWD_trade_order", "dwd"), "trade_order");
  // The tail keeps its case so the suffix rule can report it instead of silently rewriting it.
  assert.equal(stripLayerPrefix("DWD_Trade_Order", "dwd"), "Trade_Order");
});

test("caps the qualifier so the suffix stays within 41 characters", () => {
  // The budget covers the suffix only; the layer prefix is stored beside it, not inside it.
  assert.equal(qualifierMaxLength("dwd", "trade", "order"), 41 - "trade_order".length - 1);
  assert.equal(qualifierMaxLength("", "", ""), 41);
  assert.equal(qualifierMaxLength("dwd", "a_very_long_root_word_code", "a_very_long_process"), 0);
});
