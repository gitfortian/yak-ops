import assert from "node:assert/strict";
import { test } from "node:test";
import {
  DEFAULT_AUTHENTICATED_PATH,
  isAuthenticatedPath,
  resolveReturnTo,
} from "./authenticated-path.ts";

const ORIGIN = "https://yak.example";

test("keeps a data map asset deep link instead of resetting it to the datasource list", () => {
  const deepLink = "/data-governance/data-map/tables/42";
  assert.equal(isAuthenticatedPath(deepLink), true);
  assert.equal(resolveReturnTo(deepLink, ORIGIN), deepLink);
});

test("keeps the query and hash of a data map deep link", () => {
  assert.equal(
    resolveReturnTo("/data-governance/data-map/tables/42?tab=preview#columns", ORIGIN),
    "/data-governance/data-map/tables/42?tab=preview#columns",
  );
});

test("covers every data map route the router registers", () => {
  for (const pathname of [
    "/data-governance/data-map",
    "/data-governance/data-map/overview",
    "/data-governance/data-map/catalog",
    "/data-governance/data-map/crawls",
    "/data-governance/data-map/personal",
  ]) {
    assert.equal(isAuthenticatedPath(pathname), true, pathname);
  }
});

test("does not regress the prefixes that existed before data map", () => {
  for (const pathname of [
    "/data-source",
    "/data-sync",
    "/data-sync/abc/detail",
    "/operations/offline-tasks",
    "/data-modeling/logical-models/abc",
    "/management/users",
  ]) {
    assert.equal(isAuthenticatedPath(pathname), true, pathname);
  }
});

test("rejects unknown paths and cross-origin return targets", () => {
  assert.equal(isAuthenticatedPath("/data-governance-elsewhere"), false);
  assert.equal(resolveReturnTo("/unknown", ORIGIN), DEFAULT_AUTHENTICATED_PATH);
  assert.equal(
    resolveReturnTo("https://evil.example/data-source", ORIGIN),
    DEFAULT_AUTHENTICATED_PATH,
  );
  assert.equal(resolveReturnTo(null, ORIGIN), DEFAULT_AUTHENTICATED_PATH);
  assert.equal(resolveReturnTo("", ORIGIN), DEFAULT_AUTHENTICATED_PATH);
});
