# Data Sync Task Publication Lifecycle

Status: Active — PR2 implements persistence and backend lifecycle enforcement; PR3 implements OFFLINE / REALTIME frontend lifecycle adoption.

## Goal

Separate **Task publication state** from **Instance execution state**.

A Task answers whether its current definition is allowed to create new execution Instances. From v1.1 PR3, an Instance is the stable Execution root; individual Retry attempts are child Attempts under that Execution.

This contract applies to both `OFFLINE` and `REALTIME` sync types.

REALTIME 从 v1.1 PR5 起同时拥有独立的 Desired State：

```text
Task Publication State
  PUBLISHED / UNPUBLISHED

Realtime Desired State
  RUNNING / STOPPED
```

Publication 只回答“是否允许创建新 Execution”；Desired State 只回答“用户是否希望 REALTIME 持续运行”。完整规则见 [Realtime Desired State + Auto Recovery](./realtime-desired-state.md)。

Retry / Attempt semantics are defined separately in [Execution Retry / Attempt Contract](./execution-retry-attempt.md).

## Terminology

Do not reuse `ONLINE / OFFLINE` as persisted enum values because `DataSyncType.OFFLINE` already means batch synchronization.

Product wording and persisted values are intentionally separated:

| Product wording | Persisted task publication status |
| --- | --- |
| 已下线 / 下线 | `UNPUBLISHED` |
| 已上线 / 上线 | `PUBLISHED` |

Target enum:

```text
DataSyncTaskStatus
├── UNPUBLISHED = 0
└── PUBLISHED   = 1
```

The Task field is `status`.

This status is not an Instance status and must never reuse `PENDING / RUNNING / SUCCEEDED / FAILED / CANCELED / LOST`.

## Core State Machine

```text
CREATE
  ↓
UNPUBLISHED v1
  ├── Update executable definition → UNPUBLISHED v2
  ├── Update metadata only          → UNPUBLISHED v1
  └── Publish
         ↓
     PUBLISHED vN
         ├── Run / Start → Instance(taskVersion = N)
         └── Unpublish
                ↓
          UNPUBLISHED vN
```

Publication does not create an Instance.

Execution does not change publication status.

Instance completion or cancellation does not automatically unpublish a Task.

## Invariants

1. New Tasks are created as `UNPUBLISHED`.
2. Only `UNPUBLISHED` Tasks may update their executable definition.
3. `publish` and `unpublish` do not increment `definitionVersion`.
4. Only `PUBLISHED` Tasks may create a new Instance.
5. `run/start` never publishes a Task implicitly.
6. A Task with a `PENDING` or `RUNNING` Instance cannot be unpublished.
7. A `PUBLISHED` Task cannot be deleted.
8. Task deletion still requires no active Instance.
9. `syncType` is immutable after Task creation. OFFLINE ↔ REALTIME conversion requires a new Task.
10. Instance/Execution `taskVersion` and `definitionSnapshot` remain immutable historical execution input; all child Attempts must reuse them.

These invariants are backend rules. Frontend button state is presentation only and cannot replace backend enforcement.

## Command Semantics

### Create

```text
POST /tasks
  ↓
validate complete Task definition
  ↓
persist status = UNPUBLISHED
persist definitionVersion = 1
```

Create does not publish and does not run.

The current complete-definition validation remains: a Task is not a partial draft model.

### Update

```text
PUT /tasks/{id}
```

Allowed only when `status = UNPUBLISHED`.

Update still performs the current backend Datasource / Catalog / mapping validation.

Version behavior depends on what changed:

- metadata-only change → version unchanged.
- executable-definition change → `definitionVersion + 1`.
- no effective change → version unchanged.

### Publish

Target command:

```text
POST /tasks/{id}/publish
```

Allowed only when `status = UNPUBLISHED`.

Publish must revalidate the current external reality before changing status:

- referenced Datasources still exist.
- Datasource-bound database / schema scope resolves correctly.
- Source / Target tables still exist and remain field-compatible.
- OFFLINE UPSERT primary-key requirements still hold.
- REALTIME Source / Target type rules still hold.
- REALTIME Source primary key and exact Source / Target PK correspondence still hold.

A successful publish:

```text
UNPUBLISHED vN
      ↓
PUBLISHED vN
```

It does not increment `definitionVersion`, create an Instance, or start YakFlow.

### Unpublish

Target command:

```text
POST /tasks/{id}/unpublish
```

Allowed only when:

- `status = PUBLISHED`.
- there is no `PENDING` / `RUNNING` Instance for the Task.

A successful unpublish:

```text
PUBLISHED vN
    ↓
UNPUBLISHED vN
```

Unpublish is not Stop/Cancel.

V1 deliberately rejects unpublish while an Instance is active instead of silently canceling it. A future explicit “Stop and Unpublish” composite action may be added, but it must remain an explicit product command.

### Run / Start

Existing execution command remains conceptually separate:

```text
POST /tasks/{id}/run
```

Run requires:

- `status = PUBLISHED`.
- no active Instance for the Task under the current single-node contract.
- the existing run-time validation to pass.

Run creates a new Instance using the current `definitionVersion`.

Run never changes Task publication status.

### Delete

Delete requires:

- `status = UNPUBLISHED`.
- no active Instance.

Historical Instances remain after Task deletion under the existing contract.

## Definition Version Contract

`definitionVersion` describes **executable Task definition**, not publication actions and not arbitrary row updates.

### Does not increment version

Metadata:

- `name`
- `remark`

Lifecycle commands:

- publish.
- unpublish.
- run/start.
- instance cancel/stop.

### Increments version

Executable definition:

- Source Datasource ID.
- Source database / schema / table scope.
- Target Datasource ID.
- Target database / schema / table scope.
- OFFLINE `writeMode`.
- OFFLINE runtime config.
- REALTIME runtime config.

`syncType` does not participate in version comparison because it is immutable after create.

The backend must compare the canonical persisted executable definition, not blindly increment on every `PUT`.

## Why V1 Does Not Need publishedVersion / draftVersion

V1 keeps one current Task row and one `definitionVersion`.

The invariant:

```text
PUBLISHED Task
  → current definitionVersion is the published definitionVersion
  → executable definition cannot be edited while published
```

means separate fields such as these are intentionally deferred:

- `draftVersion`
- `publishedVersion`
- `currentVersion`
- Task version history table

They become necessary only when the product supports editing a new draft while an older version remains published, rollback, approval workflows, or historical definition browsing.

## OFFLINE Semantics

```text
UNPUBLISHED
  → cannot Run

PUBLISHED
  → may Run
  → Instance reaches SUCCEEDED / FAILED / CANCELED
  → Task remains PUBLISHED
```

A successful bounded sync does not automatically unpublish the Task.

This keeps the model compatible with future manual rerun, Scheduler and Retry triggers.

## REALTIME Semantics

Task publication and continuous execution remain separate:

```text
PUBLISHED + no active Instance
  → may Start

PUBLISHED + RUNNING Instance
  → CDC is active

Stop Instance
  → Instance becomes CANCELED
  → Task remains PUBLISHED
  → may Start again
```

Before unpublishing a running REALTIME Task, the user must stop the active Instance first.

## REALTIME Version and CDC State

Current REALTIME CDC state identity remains:

```text
{workspaceId}/{taskId}/v{definitionVersion}
```

Therefore:

```text
same definitionVersion
  → reuse the same connector state scope
  → later Instance may continue from persisted offset

new definitionVersion
  → new connector state scope
  → fresh initial snapshot
```

The V1 publication contract deliberately keeps this conservative rule.

Consequences:

- metadata-only edits do not increment `definitionVersion`, so they do not force a fresh snapshot.
- any REALTIME executable-definition change increments `definitionVersion`, including runtime tuning, so the next start uses a fresh state scope.
- publish / unpublish alone do not change version and therefore do not discard CDC continuation state.

A future `stateVersion` may decouple executable-definition revisions from CDC state compatibility, but it is not part of this contract.

### Referenced Datasource mutation caution

Task versioning currently versions Task fields, not the mutable connection definition behind a referenced Datasource ID.

Changing a REALTIME Source Datasource connection in place while keeping the same Task ID/version can make old CDC state incompatible with the new physical source.

This contract does **not** claim that such state reuse is safe. Before production-grade Datasource mutation support, Data Sync needs either:

- a stable Datasource revision/fingerprint included in realtime state identity, or
- enforcement that blocks incompatible Datasource connection mutation while referenced by published realtime Tasks.

This is intentionally deferred from PR1/PR2 rather than hidden by the publication model.

## Existing Task Migration

When the status column is introduced, existing Task rows should be backfilled as `PUBLISHED`.

Reason:

- existing Tasks are currently executable without a publication gate.
- backfilling them as `UNPUBLISHED` would silently disable current operational behavior.
- active REALTIME Instances can continue consistently because their parent Task remains published.

After migration:

- newly created Tasks explicitly start as `UNPUBLISHED`.
- existing Tasks may be intentionally unpublished by the user after active Instances are stopped.

## Frontend Contract

Task definition and Task execution are separate product responsibilities.

### Data Integration — Definition Surface

The OFFLINE / REALTIME definition pages own:

```text
UNPUBLISHED
  → Publish
  → Edit
  → Instances (cross-navigation to Operations Center)
  → Delete

PUBLISHED + idle
  → Unpublish
  → Instances (cross-navigation to Operations Center)

PUBLISHED + active Instance
  → Instances (cross-navigation to Operations Center)
  → Unpublish unavailable until the active Instance ends
```

Definition pages do not expose Run / Start / Stop and do not render an Instance-list Tab.

The editor uses two explicit save paths:

```text
Save
  → create/update UNPUBLISHED Task
  → remain in editor

Save & Publish
  → create/update UNPUBLISHED Task
  → call publish command
  → return to Task list
```

### Operations Center — Execution Surface

Operations Center owns execution and runtime visibility:

```text
PUBLISHED + idle
  → Run / Start
  → open the created Instance detail

PUBLISHED + active Instance
  → Stop
  → Instances

Any Task with historical Instances
  → Instance history may be opened with a Task filter
```

Operations Center lists only PUBLISHED Tasks in its executable Task view. Historical Instance lookup is independent of the current Task publication state.

The definition-page `实例` action routes into Operations Center. Legacy Data Integration Instance detail URLs redirect to the equivalent Operations Center detail route.

Create/update APIs never auto-publish, run never auto-publishes, and publication never auto-runs. Save & Publish explicitly composes the normal save and publish commands rather than creating a hidden lifecycle path.

## Persistence

PR2 adds one first-class Task field:

```text
status
  0 = UNPUBLISHED
  1 = PUBLISHED
```

No new Task-version history table is required for V1.

No physical foreign keys are introduced.

## Non-goals

This contract does not add:

- Scheduler execution.
- Retry policy.
- approval workflow.
- draft-vs-published simultaneous versions.
- version rollback.
- Task definition history table.
- automatic Stop on unpublish.
- automatic republish after edit.
- CDC `stateVersion`.
- Datasource revision/fingerprint.
- distributed publication coordination.

## Delivery Sequence

```text
PR1 — Data Sync Task Online / Offline Contract
  → documentation only

PR2 — Data Sync Task Lifecycle Backend
  → implemented: persistence + status enum + validation + publish/unpublish commands + version semantics

PR3 — Offline / Realtime Task Lifecycle UI
  → implemented: status presentation + filtering + active-instance action matrix + editor flow
```
