import ts from "typescript";

// Completed feature surfaces keep layout colors in the shared semantic tokens.
// Log syntax highlighting and the execution animation are owned by dedicated CSS.
const migratedSurface = /^apps\/web\/app\/data-sync\/(?:list|editor|detail)\/.*\.[jt]sx?$/;
const fixedColor = /#[0-9a-f]{3,8}\b/i;

export function checkDataSyncStyleTokens(relativePath, content) {
  if (!migratedSurface.test(relativePath)) return [];

  const source = ts.createSourceFile(relativePath, content, ts.ScriptTarget.Latest, true);
  const violations = [];

  function visit(node) {
    if (
      (ts.isStringLiteral(node) ||
        ts.isNoSubstitutionTemplateLiteral(node) ||
        ts.isTemplateHead(node) ||
        ts.isTemplateMiddle(node) ||
        ts.isTemplateTail(node)) &&
      fixedColor.test(node.text)
    ) {
      const line = source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1;
      violations.push(
        `${relativePath}:${line} embeds a fixed color; use a semantic Yak UI theme token`,
      );
    }
    ts.forEachChild(node, visit);
  }

  visit(source);
  return violations;
}
