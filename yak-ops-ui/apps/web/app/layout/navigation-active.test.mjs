import assert from "node:assert/strict";
import { test } from "node:test";

import { DATA_GOVERNANCE_NAVIGATION } from "./navigation.ts";
import { isNavigationChildActive } from "./navigation-active.ts";

const [dataMap] = DATA_GOVERNANCE_NAVIGATION;
const children = dataMap.children ?? [];
const overview = children.find((child) => child.path.endsWith("/overview"));
const catalog = children.find((child) => child.path.endsWith("/catalog"));
const tasks = children.find((child) => child.path.endsWith("/tasks"));

test("every data map page is reachable from the sidebar", () => {
  assert.deepEqual(
    children.map((child) => child.path),
    [
      "/data-governance/data-map/overview",
      "/data-governance/data-map/catalog",
      "/data-governance/data-map/tasks",
      "/data-governance/data-map/personal",
    ],
  );
});

test("a child is active on its own route only", () => {
  assert.equal(isNavigationChildActive(overview, "/data-governance/data-map/overview"), true);
  assert.equal(isNavigationChildActive(overview, "/data-governance/data-map/catalog"), false);
  assert.equal(isNavigationChildActive(tasks, "/data-governance/data-map/catalog"), false);
});

test("the task editor keeps its list entry active", () => {
  assert.equal(isNavigationChildActive(tasks, "/data-governance/data-map/tasks/42/edit"), true);
});

test("a detail route keeps its list entry active", () => {
  assert.equal(isNavigationChildActive(catalog, "/data-governance/data-map/tables/42"), true);
  assert.equal(isNavigationChildActive(overview, "/data-governance/data-map/tables/42"), false);
});

test("a sibling name sharing a prefix is not treated as a descendant", () => {
  assert.equal(
    isNavigationChildActive(catalog, "/data-governance/data-map/catalog-archive"),
    false,
  );
  assert.equal(isNavigationChildActive(overview, "/data-governance/data-map/overviews"), false);
});
