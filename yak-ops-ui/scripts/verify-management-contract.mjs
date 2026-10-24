import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import ts from "typescript";

const source = readFileSync(
  new URL("../apps/web/app/management/workspaces/members/permissions.ts", import.meta.url),
  "utf8",
);
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
});
const exports = {};
vm.runInNewContext(outputText, { exports });
const { canChangeMember, allowedMemberRoles } = exports;

const owner = { userId: "owner", role: "OWNER" };
const admin = { userId: "admin", role: "ADMIN" };
const member = { userId: "member", role: "MEMBER" };
assert.equal(canChangeMember("OWNER", owner, 1), false, "last owner must be protected");
assert.equal(canChangeMember("OWNER", owner, 2), true, "owner can manage a non-last owner");
assert.equal(canChangeMember("ADMIN", owner, 2), false, "admin cannot change owners");
assert.equal(canChangeMember("ADMIN", admin, 1), true, "admin can manage admins");
assert.equal(canChangeMember("ADMIN", member, 1), true, "admin can manage members");
assert.equal(canChangeMember("MEMBER", member, 2), false, "member cannot manage roles");
assert.equal(
  JSON.stringify(allowedMemberRoles("OWNER")),
  JSON.stringify(["OWNER", "ADMIN", "MEMBER"]),
);
assert.equal(JSON.stringify(allowedMemberRoles("ADMIN")), JSON.stringify(["ADMIN", "MEMBER"]));
console.log("Management membership permission contract passed");
