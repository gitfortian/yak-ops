import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

import { describe, it } from "node:test";

const SHARED_DIR = dirname(fileURLToPath(import.meta.url));
const MODELING_DIR = dirname(SHARED_DIR);

function tsxFiles(dir) {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry);
    if (entry === "node_modules" || entry.startsWith(".")) return [];
    if (statSync(path).isDirectory()) return tsxFiles(path);
    return entry.endsWith(".tsx") ? [path] : [];
  });
}

/** A bare `required` JSX prop on one of these elements installs a native constraint. */
const NATIVE_CONSTRAINT_ELEMENTS = new Set(["Input", "Textarea", "Select"]);

function nativeRequiredSites() {
  const found = [];
  for (const file of tsxFiles(MODELING_DIR)) {
    let owner = null;
    readFileSync(file, "utf8")
      .split("\n")
      .forEach((line, index) => {
        const opened = /<([A-Za-z][\w.]*)/.exec(line);
        if (opened) owner = opened[1];
        if (/^\s*required\s*$/.test(line) && NATIVE_CONSTRAINT_ELEMENTS.has(owner)) {
          found.push(`${relative(MODELING_DIR, file)}:${index + 1}`);
        }
      });
  }
  return found;
}

describe("data-modeling field errors", () => {
  const source = readFileSync(join(SHARED_DIR, "form-fields.tsx"), "utf8");

  it("keeps app-validated dialogs in charge of their own submit", () => {
    const shared = tsxFiles(MODELING_DIR).filter((file) => {
      const text = readFileSync(file, "utf8");
      // Base UI's Form skips the submit event when a registered field reports invalid state, so a
      // dialog that validates in its own state can never clear the message it is showing.
      return /<Form[\s>]/.test(text) && /(setErrors|invalid=\{)/.test(text);
    });
    assert.deepEqual(
      shared.map((file) => relative(MODELING_DIR, file)),
      [],
      "app-validated dialogs must not be wrapped in Base UI Form",
    );
  });

  it("carries requiredness as ARIA state instead of a native constraint", () => {
    assert.equal(
      nativeRequiredSites().length,
      0,
      `native required blocks Base UI's Form before the app validator runs: ${nativeRequiredSites()}`,
    );
    assert.match(source, /aria-required=/, "required fields must still expose aria-required");
  });

  it("mounts an error node only while it has text to show", () => {
    const errors = source.match(/<FieldError[^>]*>/g) ?? [];
    assert.ok(errors.length >= 3, "the three shared field controls each render an error node");
    for (const tag of errors) {
      assert.match(tag, /<FieldError match>/, `error node must be gated by its message: ${tag}`);
    }
    assert.doesNotMatch(
      source,
      /<FieldError match=\{Boolean\(/,
      "a match-only boolean leaves an empty aria-describedby target when the message is absent",
    );
  });

  it("shows the message through the field error, not a duplicate paragraph", () => {
    assert.doesNotMatch(source, /className=\{?"[^"]*field-error[^"]*"\}/);
    assert.equal(
      (source.match(/<p className="mt-1 text-xs leading-4/g) ?? []).length,
      2,
      "only the two hints may render their own paragraph",
    );
  });
});
