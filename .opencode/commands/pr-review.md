---
description: Review a PR for correctness and actionable regression risks
agent: plan
---

Review $ARGUMENTS against its actual base and head SHA. Read-only: do not edit, merge, or open another PR.

Read the full diff and relevant callers, state ownership, tests, and CI evidence. Prioritize correctness, lifecycle, recovery, persistence, security, and concrete regressions. For each actionable issue, provide severity, a path/line reference, why it is a defect, and a minimal fix direction.

Do not repeat resolved findings or turn stylistic preferences into blockers. Distinguish unverified risks from demonstrated bugs. Record reviewed SHA/tested tree; on a follow-up review inspect the delta and expand scope only when shared contracts or base change.

Conclude with remaining blockers and accurately scoped verification results.
