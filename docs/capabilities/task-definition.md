# Task Definition Foundation

Status: Active — definition-only backend reset (v1.3 draft).

## Single ownership

- `yak-ops-business-task` owns all Task Definition CRUD, publication, logical deletion and version transactions.
- `yak_ops_task_definition` owns stable task ID, Workspace, name, plugin type, current version, status and audit fields.
- `yak_ops_task_definition_version` owns immutable executable parameters, their plugin schema version, and audit fields.
- The unique active name is per Workspace. Deleted definitions retain versions; the same name may be reused.
- Changes to executable parameters append a version. Rename, remark and status changes do not.
- Task types are open canonical strings, such as `DATA_SYNC`, not a hardcoded enum.

## Plugin validation

`DefinitionService` calls `TaskPluginRegistry.validate` with a typed `ParameterFormat` and a read-only `TaskValidationContext`.
The DATA_SYNC plugin uses the Source / Transform / Sink pipeline parameter schema v2, with HOCON input and
JDBC / MySQL CDC source/target table compatibility checks. The host supplies Workspace-checked datasource metadata,
never database credentials. Unknown parameters and credential-bearing configuration are rejected.

Each version is immutable and is uniquely identified by `(workspace_id, definition_id, version)`.
A future Workflow node references a stable `definition_id + version`, not another copy of its parameters.

## HTTP

Canonical endpoints: `/api/v1/task-definitions` (create, edit, query, page, publish, unpublish, delete)
and `/api/v1/task-definitions/{id}/versions`.

The existing `/api/v1/data-sync/tasks` Definition endpoints remain as HTTP-only DTO projections
onto the one `DefinitionService`. They do not preserve Data Sync Instance, Schedule or Execution.

## Migration boundary

Published V1–V3 remain frozen. Draft V4–V8 were withdrawn and replaced with
`V4__task_definition_foundation.sql`. It explicitly retires V1–V3's old data-sync tables
and creates only the two Task Definition tables. This is a destructive product reset:
existing V4–V8 development databases must be backed up and rebuilt from V1–V3,
not silently repaired or migrated. Published v1.2 execution history is not carried forward.

## Deliberate non-goals

No TaskInstance, Attempt, Scheduler, Quartz, Metrics, Log, Workflow entities, runtime submission or
Master/Worker abstractions are introduced. YakFlow Core/Runtime/Connectors are unchanged.
The DATA_SYNC frontend uses canonical Task Definition CRUD and projects pipeline JSON onto
its existing single-table editor. Instance, Schedule, and execution pages remain out of scope.

## Configuration formats

Canonical Task Definition payloads default to JSON. A create or update request may supply
`parametersFormat: "HOCON"` for a DATA_SYNC pipeline; parsing happens inside SyncPlugin and the
result is stored as canonical JSON before version comparison and persistence. A separate
`GET /api/v1/task-definitions/{id}/parameters/hocon` returns human-readable HOCON without
storing duplicate source text. DATA_SYNC parameter schema version 2 is frozen per Definition
Version; old schema 1 snapshots are not silently rewritten or reinterpreted.
