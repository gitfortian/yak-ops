import { dirname, resolve, sep } from "node:path";
import ts from "typescript";

function moduleSpecifiers(filePath, content) {
  const source = ts.createSourceFile(filePath, content, ts.ScriptTarget.Latest, true);
  const specifiers = [];
  function visit(node) {
    if (
      (ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) &&
      node.moduleSpecifier &&
      ts.isStringLiteral(node.moduleSpecifier)
    ) {
      specifiers.push(node.moduleSpecifier.text);
    }
    if (
      ts.isCallExpression(node) &&
      (node.expression.kind === ts.SyntaxKind.ImportKeyword ||
        (ts.isIdentifier(node.expression) && node.expression.text === "require")) &&
      node.arguments.length === 1 &&
      ts.isStringLiteral(node.arguments[0])
    ) {
      specifiers.push(node.arguments[0].text);
    }
    ts.forEachChild(node, visit);
  }
  visit(source);
  return specifiers;
}

const inside = (candidate, owner) => candidate === owner || candidate.startsWith(owner + sep);

export function checkArchitectureImports(relativePath, content, root) {
  const source = resolve(root, relativePath);
  const app = resolve(root, "apps/web/app");
  const service = resolve(root, "apps/web/service");
  const http = resolve(root, "apps/web/service/http");
  const ui = resolve(root, "packages/yak-ui");
  const dataSync = resolve(app, "data-sync");
  const dataSyncSurfaces = ["list", "editor", "detail", "log"];
  const dataSyncRouteEntries = new Set([resolve(dataSync, "editor/page"), resolve(dataSync, "detail")]);
  const surfaceOf = (path) =>
    dataSyncSurfaces.find((name) => inside(path, resolve(dataSync, name)));

  const violations = [];

  for (const specifier of moduleSpecifiers(relativePath, content)) {
    let target;
    if (specifier.startsWith("@/")) {
      target = resolve(root, "apps/web", specifier.slice(2));
    } else if (specifier.startsWith(".")) {
      target = resolve(dirname(source), specifier);
    } else {
      continue;
    }

    const sourceSurface = surfaceOf(source);
    const targetSurface = surfaceOf(target);
    if (targetSurface && !inside(source, dataSync) && !(inside(source, resolve(app, "router")) && dataSyncRouteEntries.has(target))) {
      violations.push(relativePath + " imports a private Data Sync feature: " + specifier);
    }
    if (sourceSurface && targetSurface && sourceSurface !== targetSurface) {
      violations.push(relativePath + " crosses Data Sync feature boundaries: " + specifier);
    }
    if (inside(source, service) && inside(target, app)) {
      violations.push(relativePath + " imports App code: " + specifier);
    }
    if (inside(source, app) && inside(target, http)) {
      violations.push(relativePath + " bypasses domain Service: " + specifier);
    }
    if (inside(source, ui) && (inside(target, app) || inside(target, service))) {
      violations.push(relativePath + " imports product code into Yak UI: " + specifier);
    }
  }

  return violations;
}
