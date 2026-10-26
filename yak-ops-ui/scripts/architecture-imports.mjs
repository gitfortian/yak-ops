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
  const dataSyncSurfaces = ["list", "editor", "detail"];
  const dataSyncRouteEntries = new Set([
    resolve(dataSync, "editor/page"),
    resolve(dataSync, "detail"),
    resolve(dataSync, "list/page"),
  ]);
  const surfaceOf = (path) =>
    dataSyncSurfaces.find((name) => inside(path, resolve(dataSync, name)));

  const management = resolve(app, "management");
  const managementDomains = ["users", "workspaces"];
  const managementPages = new Set(
    managementDomains.map((name) => resolve(management, name, "page")),
  );
  const managementDomain = (path) =>
    managementDomains.find((name) => inside(path, resolve(management, name)));
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
    if (
      targetSurface &&
      !inside(source, dataSync) &&
      !(inside(source, resolve(app, "router")) && dataSyncRouteEntries.has(target))
    ) {
      violations.push(relativePath + " imports a private Data Sync feature: " + specifier);
    }
    if (sourceSurface && targetSurface && sourceSurface !== targetSurface) {
      violations.push(relativePath + " crosses Data Sync feature boundaries: " + specifier);
    }
    const targetManagementDomain = managementDomain(target);
    const sourceManagementDomain = managementDomain(source);
    if (
      targetManagementDomain &&
      !inside(source, management) &&
      !(inside(source, resolve(app, "router")) && managementPages.has(target))
    ) {
      violations.push(relativePath + " imports a private Management feature: " + specifier);
    }
    if (
      sourceManagementDomain &&
      targetManagementDomain &&
      sourceManagementDomain !== targetManagementDomain
    ) {
      violations.push(relativePath + " crosses Management feature boundaries: " + specifier);
    }
    const schedule = resolve(app, "schedule");
    const schedulePublicEntries = new Set([
      resolve(schedule, "dialog/schedule-dialog"),
      resolve(schedule, "task-schedule-cell"),
      resolve(schedule, "form/schedule-form"),
      resolve(schedule, "use-task-schedules"),
      resolve(schedule, "schedule-presentation"),
    ]);
    if (
      inside(target, schedule) &&
      !inside(source, schedule) &&
      !schedulePublicEntries.has(target)
    ) {
      violations.push(relativePath + " imports a private Schedule feature: " + specifier);
    }
    const operations = resolve(app, "operations");
    const operationsPage = resolve(operations, "dashboard/page");
    if (
      inside(target, operations) &&
      !inside(source, operations) &&
      !(inside(source, resolve(app, "router")) && target === operationsPage)
    ) {
      violations.push(relativePath + " imports a private Operations feature: " + specifier);
    }
    const layout = resolve(app, "layout");
    const shellEntries = new Set([resolve(layout, "AppLayout"), resolve(layout, "navigation")]);
    if (
      inside(target, layout) &&
      !inside(source, layout) &&
      !(inside(source, resolve(app, "router")) && shellEntries.has(target))
    ) {
      violations.push(relativePath + " imports a private App Shell module: " + specifier);
    }
    if (inside(source, layout) && inside(target, app) && !inside(target, layout)) {
      violations.push(relativePath + " imports a feature into App Shell: " + specifier);
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
