---
description: Trace an execution path and ownership without editing
agent: plan
---

Analyze $ARGUMENTS in the current Yak Ops source. Do not modify files or create a PR.

1. Identify actual entry points, direct callers, configuration flow, and execution path.
2. Trace lifecycle, state ownership, resource cleanup, and persistence/recovery where applicable.
3. Explain each key class's responsibility and the contracts connecting them.
4. Separate observed problems from preferences or assumptions; cite file paths and line numbers.
5. Report concrete risks and narrowly scoped recommendations. Do not propose a PR unless asked.

Follow AGENTS.md; verify behavior from implementations, not names or comments alone.
