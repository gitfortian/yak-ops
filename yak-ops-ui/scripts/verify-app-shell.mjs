import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import ts from "typescript";

const read = (path) => readFileSync(new URL("../" + path, import.meta.url), "utf8");
function load(path, deps) {
  const source = read(path);
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  const exports = {};
  vm.runInNewContext(outputText, {
    exports,
    require: (name) => {
      if (name in deps) return deps[name];
      throw new Error("Unexpected App Shell dependency: " + name);
    },
  });
  return exports;
}

const fakeProducts = [
  { id: "data-integration", label: "数据集成" },
  { id: "operations", label: "运维中心" },
  { id: "management", label: "管理中心" },
];
const model = load("apps/web/app/layout/product-favorites.ts", {
  "./navigation": { GLOBAL_PRODUCT_MENU: fakeProducts },
});
const base = [
  { scene: "PRODUCT_MENU", itemKey: "operations", favorite: true, sortOrder: 3, useCount: 7 },
  { scene: "PRODUCT_MENU", itemKey: "management", favorite: true, sortOrder: 1, useCount: 0 },
  { scene: "PRODUCT_MENU", itemKey: "unknown", favorite: true, sortOrder: 2, useCount: 0 },
];
const added = model.updateFavoritePreference(base, "data-integration");
assert.equal(added.favorite, true);
assert.equal(added.preferences.find((x) => x.itemKey === "data-integration").sortOrder, 4);
const disabled = model.updateFavoritePreference(base, "operations");
assert.equal(disabled.favorite, false);
assert.equal(disabled.preferences.find((x) => x.itemKey === "operations").useCount, 7);
assert.deepEqual(
  Array.from(model.favoriteProducts(base)).map((x) => x.id),
  ["management", "operations"],
);
assert.equal(model.upsertFavoritePreference(base, base[0]).length, base.length);

let userId = "user-A";
let prefs = base;
const queries = new Map();
const keyString = (key) => JSON.stringify(key);
const client = {
  cancelQueries: async () => undefined,
  getQueryData: (key) => queries.get(keyString(key)),
  setQueryData: (key, next) => {
    const name = keyString(key);
    const value = typeof next === "function" ? next(queries.get(name)) : next;
    queries.set(name, value);
  },
  invalidateQueries: () => Promise.resolve(),
};
let mutationConfig;
let queryConfig;
const hook = load("apps/web/app/layout/use-product-favorites.ts", {
  "@tanstack/react-query": {
    useQuery: (config) => {
      queryConfig = config;
      return { data: prefs, isPending: false, isError: false, refetch: async () => undefined };
    },
    useMutation: (config) => {
      mutationConfig = config;
      return { isPending: false, mutate: () => undefined };
    },
    useQueryClient: () => client,
  },
  "@yak-ops/yak-ui": { toast: { error: () => undefined } },
  "@/hooks/use-auth": { useAuth: () => ({ currentUser: { id: userId } }) },
  "@/service/preference": {
    listUserPreferences: (_scene, signal) => Promise.resolve({ signal }),
    updateUserPreferenceFavorite: () => Promise.resolve(base[0]),
  },
  "./navigation": { GLOBAL_PRODUCT_MENU: fakeProducts },
  "./product-favorites": model,
});
const hookState = hook.useProductFavorites(true);
assert.deepEqual(
  Array.from(hookState.favorites).map((x) => x.id),
  ["management", "operations"],
);
assert.equal(queryConfig.enabled, true);
assert.equal(queryConfig.retry, false);
assert.equal(queryConfig.queryKey[1], "user-A");
assert.equal(queryConfig.queryKey.includes("PRODUCT_MENU"), true);
const signal = new AbortController().signal;
assert.equal((await queryConfig.queryFn({ signal })).signal, signal);
assert.equal(hook.favoriteQueryKey("user-B")[1], "user-B");
assert.notEqual(
  keyString(hook.favoriteQueryKey("user-A")),
  keyString(hook.favoriteQueryKey("user-B")),
);
client.setQueryData(queryConfig.queryKey, base);
const result = await mutationConfig.onMutate({ productId: "operations", favorite: false });
assert.equal(
  client.getQueryData(queryConfig.queryKey).find((x) => x.itemKey === "operations").favorite,
  false,
);
mutationConfig.onError(new Error("network"), { productId: "operations", favorite: false }, result);
assert.equal(
  client.getQueryData(queryConfig.queryKey).find((x) => x.itemKey === "operations").favorite,
  true,
);
userId = "user-B";
hook.useProductFavorites(true);
assert.equal(queryConfig.queryKey[1], "user-B");
hook.useProductFavorites(false);
assert.equal(queryConfig.enabled, false);
assert.match(read("apps/web/app/layout/TopBar.tsx"), /<WorkspaceSwitcher \/>/);
assert.match(read("apps/web/app/layout/TopBar.tsx"), /<AccountMenu \/>/);
assert.doesNotMatch(
  read("apps/web/app/layout/ProductLauncher.tsx"),
  /listUserPreferences|updateUserPreferenceFavorite/,
);
assert.match(read("apps/web/app/router/data-sync-instance-redirect.tsx"), /DataSyncInstanceRedirect/);
console.log("App Shell composition, user-owned favorites and mutation rollback contracts passed");
