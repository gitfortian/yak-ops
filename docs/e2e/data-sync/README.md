# Data Sync Manual E2E Playbook

Status: Active

Scope:

- Yak Ops Data Sync product flow.
- OFFLINE and REALTIME manual end-to-end acceptance.
- Human-executed verification against real databases.

## Purpose

Manual E2E is the product-level acceptance layer above automated Unit / Integration / Acceptance tests.

It answers one question:

> Can a user prepare real database data, configure Data Sync through Yak Ops, run the task and verify the final database result from beginning to end?

The verification path is:

```text
Source Database
      ↓
Datasource Resource
      ↓
Task Definition
      ↓
Yak Ops UI Operation
      ↓
Data Sync Business Layer
      ↓
YakFlow Local Execution Engine
      ↓
Connector
      ↓
Target Database
      ↓
Result Verification
```

## What Manual E2E Is Not

Manual E2E does not replace automated tests.

```text
Unit Test
  proves local behavior

Integration / Acceptance Test
  proves Runtime / Connector behavior against real databases

Manual E2E
  proves the complete user-visible product workflow
```

Manual E2E documents must not be converted into large automated test matrices merely to mirror every parameter combination.

## Case Design Rules

Each case represents one meaningful user scenario.

Must:

- Start from database preparation and finish with result verification.
- Be executable independently from the first step to the last step.
- Use explicit Source / Target DDL and seed data.
- Use deterministic `e2e_*` table names.
- Describe the Yak Ops UI operation instead of calling internal APIs directly.
- Record the important task parameters.
- State the expected Instance lifecycle.
- Verify the target database with SQL.
- State exact expected business rows whenever possible.
- Include an acceptance checklist.
- Include cleanup SQL.
- Keep database passwords, tokens, SSH keys and other credentials out of the document.
- Match the capability contract currently implemented on `main`.

Must Not:

- Treat a successful Task save as proof that synchronization works.
- Treat `RUNNING` as proof that realtime CDC works.
- Verify only row counts when a scenario depends on UPDATE / DELETE semantics.
- Claim exactly-once semantics.
- Expand one scenario into a Cartesian-product matrix of every database, write mode and runtime parameter.
- Duplicate automated connector Acceptance tests line by line.

## Execution Conventions

Before running a case:

1. Use a non-production database environment.
2. Confirm the required Datasource resources can connect successfully.
3. Run the case with a clean `e2e_*` table state.
4. Do not reuse data from another E2E case unless that dependency is explicitly documented.
5. Record any deviation from the documented expected result as a failure until explained.

For realtime cases, wait for the current change to appear on the target before performing the next source mutation. This keeps each INSERT / UPDATE / DELETE observation attributable to one operation.

## Current Golden Paths

| ID | Scenario | Purpose |
| --- | --- | --- |
| OFFLINE-001 | [MySQL → MySQL / APPEND](offline/01-mysql-to-mysql-append.md) | Prove the complete bounded offline product path and APPEND semantics. |
| REALTIME-001 | [MySQL CDC → MySQL](realtime/01-mysql-cdc-to-mysql.md) | Prove initial snapshot plus INSERT / UPDATE / DELETE CDC through the product UI. |

## Expansion Rule

Add a new Manual E2E case only when it proves a materially different product behavior.

Good future cases include:

- MySQL → PostgreSQL offline type compatibility.
- MySQL → Oracle offline type compatibility.
- OFFLINE OVERWRITE semantics.
- OFFLINE UPSERT semantics.
- OFFLINE split + parallel Reader execution.
- MySQL CDC → PostgreSQL.
- MySQL CDC → Oracle.
- Realtime Stop → rerun continuation from persisted offset.
- Process restart → old Instance LOST → new Instance continuation.

A Pull Request that changes one of these behaviors should name the Manual E2E case that proves the behavior after implementation.
