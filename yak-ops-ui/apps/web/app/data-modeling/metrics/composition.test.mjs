import assert from "node:assert/strict";
import { test } from "node:test";
import { composeComposition, compositionFits, previewComposition } from "./composition.ts";

test("joins the code with underscores and the name without separators", () => {
  const composed = composeComposition({
    periodCode: "d7",
    modifierCodes: ["muying", "vip"],
    atomicCode: "pay_amt",
    periodName: "最近7天",
    modifierNames: ["母婴", "会员"],
    atomicName: "支付金额",
  });
  assert.equal(composed.enCode, "d7_muying_vip_pay_amt");
  assert.equal(composed.cnName, "最近7天母婴会员支付金额");
});

test("keeps the submitted modifier order", () => {
  const composed = composeComposition({
    periodCode: "d1",
    modifierCodes: ["vip", "muying"],
    atomicCode: "pay_amt",
    periodName: "昨日",
    modifierNames: ["会员", "母婴"],
    atomicName: "支付金额",
  });
  assert.equal(composed.enCode, "d1_vip_muying_pay_amt");
});

test("accepts values at the column limits and refuses one past them", () => {
  const at = composeComposition({
    periodCode: "p".repeat(20),
    modifierCodes: ["m".repeat(20)],
    atomicCode: "a".repeat(20),
    periodName: "近".repeat(42),
    modifierNames: ["饰".repeat(42)],
    atomicName: "支".repeat(42),
  });
  assert.equal(at.enCode.length, 62);
  assert.equal(at.cnName.length, 126);
  assert.ok(compositionFits(at));
  assert.ok(!compositionFits({ enCode: "a".repeat(65), cnName: "名" }));
  assert.ok(!compositionFits({ enCode: "code", cnName: "名".repeat(129) }));
});

test("previews only complete parts and reports whether they fit", () => {
  assert.equal(
    previewComposition({ periodCode: "d7", modifierCodes: [], atomicName: "支付金额" }),
    null,
  );
  const preview = previewComposition({
    periodCode: "d7",
    modifierCodes: [],
    atomicCode: "pay_amt",
    periodName: "最近7天",
    atomicName: "支付金额",
  });
  assert.deepEqual(preview, {
    composed: { enCode: "d7_pay_amt", cnName: "最近7天支付金额" },
    fits: true,
  });
  const overlong = previewComposition({
    periodCode: "p".repeat(60),
    modifierCodes: [],
    atomicCode: "pay_amt",
    periodName: "近",
    atomicName: "支付金额",
  });
  assert.ok(overlong && !overlong.fits);
});
