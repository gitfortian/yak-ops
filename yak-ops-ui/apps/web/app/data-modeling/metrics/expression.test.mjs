import assert from "node:assert/strict";
import { test } from "node:test";
import { metricExpressionReferences, parseMetricExpression } from "./expression.ts";

test("collects references in appearance order", () => {
  assert.deepEqual(parseMetricExpression("(pay_amt - pay_amt_last) / pay_amt_last * 100"), [
    "pay_amt",
    "pay_amt_last",
    "pay_amt_last",
  ]);
  assert.deepEqual(metricExpressionReferences("(pay_amt - pay_amt_last) / pay_amt_last * 100"), [
    "pay_amt",
    "pay_amt_last",
  ]);
});

test("accepts numbers, parentheses and signs", () => {
  assert.deepEqual(parseMetricExpression("-a * (1.5 + 2) / -0.5"), ["a"]);
  assert.deepEqual(parseMetricExpression("1 + 2 * 3"), []);
});

test("refuses blank, malformed and illegal input", () => {
  assert.equal(parseMetricExpression("   "), null);
  assert.equal(parseMetricExpression("a +"), null);
  assert.equal(parseMetricExpression("(a - b"), null);
  assert.equal(parseMetricExpression("a b"), null);
  assert.equal(parseMetricExpression("SUM(a)"), null);
  assert.equal(parseMetricExpression("金额 + 1"), null);
  assert.equal(parseMetricExpression("a + 'x'"), null);
  assert.equal(parseMetricExpression("a".repeat(65)), null);
});
