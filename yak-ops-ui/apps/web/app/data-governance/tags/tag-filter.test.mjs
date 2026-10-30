import assert from "node:assert/strict";
import { test } from "node:test";

import { tagFilterGroups, tagFilterLabel } from "./tag-filter.ts";

const keys = [
  {
    id: "key-dept",
    name: "部门",
    values: [
      { id: "v-dept-a", name: "电商", usageCount: 2 },
      { id: "v-dept-b", name: "零售", usageCount: 1 },
    ],
  },
  {
    id: "key-sensitivity",
    name: "敏感度",
    values: [{ id: "v-pii", name: "PII", usageCount: 3 }],
  },
];

test("no selection leaves no group and the all-tags label", () => {
  assert.deepEqual(tagFilterGroups(keys, []), []);
  assert.equal(tagFilterLabel([]), "全部标签");
});

test("values of one key collapse into one group in dictionary order", () => {
  const groups = tagFilterGroups(keys, ["v-dept-b", "v-dept-a"]);
  assert.equal(groups.length, 1);
  assert.equal(groups[0].keyName, "部门");
  assert.deepEqual(
    groups[0].values.map((value) => value.id),
    ["v-dept-a", "v-dept-b"],
  );
  assert.equal(tagFilterLabel(groups), "部门: 电商、零售");
});

test("values of different keys stay separate groups in dictionary order", () => {
  const groups = tagFilterGroups(keys, ["v-pii", "v-dept-a"]);
  assert.deepEqual(
    groups.map((group) => group.keyName),
    ["部门", "敏感度"],
  );
  assert.equal(tagFilterLabel(groups), "部门: 电商；敏感度: PII");
});

test("an unknown selected id is ignored rather than rendered", () => {
  assert.deepEqual(tagFilterGroups(keys, ["v-unknown"]), []);
});
