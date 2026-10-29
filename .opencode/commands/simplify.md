---
description: Review responsibilities, duplication, and unjustified abstractions
agent: plan
---

Review the architecture and responsibilities of $ARGUMENTS. Read-only: no code changes or PR.

Map real callers, lifecycle owners, state, configuration, validation, and module boundaries. Look for:
- Interfaces with no independent contract or caller.
- Delegating wrappers, synonym DTOs, duplicate models or validations.
- Speculative compatibility paths, outdated implementations, and ambiguous Factory/Manager/Handler classes.
- Helpers outside the module that owns their invariant.

Do not equate fewer classes or shorter files with better design. For each finding, provide evidence, behavioral risk, and a **keep / consolidate / remove** recommendation. Preserve persisted/checkpoint safety and supported contracts. Avoid unrelated cleanup.
