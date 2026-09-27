# Data Sync Capability

Status: Phase 2 — Datasource Catalog + Offline Task Editor

## Goal

Data Sync is the Yak Ops product layer above YakFlow.

The first product milestone is offline synchronization:

```text
Task Definition
      ↓
Task Instance
      ↓
YakFlow Local Runtime
      ↓
JdbcSource -> JdbcSink
```

Phase 1 established task-definition and instance persistence. Phase 2 adds Datasource Catalog reads, backend same-name field compatibility validation and the offline task editor. It still does not start YakFlow.

## Task Definition

A task answers:

> What should be synchronized from which datasource/table to which target datasource/table?

Current task type:

```text
OFFLINE
```

A task persists:

- Workspace ownership.
- source / target datasource IDs.
- source / target database, schema and table locations.
- YakFlow runtime tuning JSON.
- monotonically increasing definition version.
- user-facing name and remark.

Datasource credentials never belong to the task table.

## Task Instance

An instance represents one concrete execution attempt of a task.

One task may have many instances:

```text
Task
├── Instance #1 SUCCEEDED
├── Instance #2 FAILED
└── Instance #3 RUNNING
```

Instance persistence owns:

- task ID / task name / task definition version.
- trigger type.
- execution status.
- sanitized definition snapshot.
- read / write row counters.
- start / finish time.
- structured failure code and sanitized failure message.

The definition snapshot must not contain datasource connection JSON, passwords, SSH private keys, tokens or other credentials.

## Phase 2 — Offline Task Editor

The editable product path is:

```text
choose source datasource/table
        ↓
choose target datasource/table
        ↓
backend same-name field mapping preview
        ↓
compatible?
        ↓ yes
save OFFLINE task definition
```

The editor contains only:
- basic information.
- source datasource/table.
- target datasource/table.
- read-only automatic field mapping result.
- YakFlow runtime tuning.

No filter SQL, split key, pre/post SQL, resource group or Transform is introduced in this phase.

## Phase 1 Boundary

Phase 1 provides:

- Flyway tables.
- DAO Entity / Mapper / Repository.
- shared DTO / VO and persistence enums.
- `DataSyncService` task CRUD and task/instance query contract.
- task persistence implementation.

Phase 2 now provides task HTTP CRUD, frontend task list/editor, Datasource Catalog browsing and field mapping validation.

It still does not provide:

- YakFlow execution.
- run / cancel / retry.
- scheduler.
- execution recovery.

Those belong to later PRs.
