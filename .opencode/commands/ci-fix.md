---
description: Diagnose and fix actionable CI failures without weakening checks
agent: build
---

Diagnose and fix the failing CI for $ARGUMENTS in Yak Ops.

1. Identify the PR/head SHA, failed job, and earliest actionable log error.
2. Classify the failure as formatting, compilation, production behavior, test expectation, configuration, or infrastructure; inspect affected sources and tests.
3. Fix the demonstrated root cause in the authorized scope. Do not disable checks, weaken assertions, hide exceptions, delete safety tests, or alter branch protection.
4. Run the smallest meaningful verification; expand if contracts or recovery semantics changed.
5. Report changed files, evidence, checks actually run, unresolved failures, and any test not selected.

Do not merge or change repository settings unless explicitly requested. If the required CI access or execution environment is unavailable, report that precisely instead of claiming success.
