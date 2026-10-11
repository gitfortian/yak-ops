# Task Plugin Rules

Status: Active — Task Definition parameter parsing and validation only.

## Ownership

- `yak-ops-plugin-task-api` exposes the provider-neutral `TaskPlugin`, `TaskParameters`, `TaskPluginRegistry`, and resource validation contract.
- Direct `ServiceLoader<TaskPlugin>` discovery registers one stateless plugin for one exact canonical type; no redundant Factory or inferred type aliases. Reject duplicates on startup.
- The Registry only discovers providers and delegates parsing and validation. It never implements an extra parameter-validation flow.
- The selected plugin checks local parameter structure once in `parse`, and authorized Workspace resource constraints once in `validate`. `validate` does not mutate or replace the submitted configuration.
- Task Business alone owns Workspace, Definition identity, immutable versions and publishing. JSON is the sole accepted parameter format and versioned storage format.
- Keep cross-layer `DataSourceInfo` as a top-level record in the API validation package.

## DATA_SYNC

- Product parameter definitions live under `source/`, `sink/` and one `SyncParameters` pipeline. Current implementations are JDBC Source, MySQL CDC Source and JDBC Sink.
- No one-class `source/jdbc`, `source/mysqlcdc` or `sink/jdbc` packages until the type owns enough implementation detail to warrant them.
- `SyncPlugin` parses JSON directly into typed `SyncParameters`; do not add a second format parser or conversion layer.
- Source JDBC is bounded; MySQL CDC is continuous and requires UPSERT with matching primary keys. Transform is empty and runtime/retry policy is not part of Definition parameters.
- Resolve Datasource metadata only through trusted read-only `TaskValidationContext`; use Core `TableId` and `TableSchema` without JDBC URLs, passwords, access tokens or connections.
- Reject user-provided Database/Schema conflicts with Datasource scope. Use the Datasource default solely to locate metadata when the user did not specify a value.
- Query each physical table's Schema once for validation. Unknown database dialects should fail through actual Connector/Datasource capability rather than a duplicate type allowlist.
- Reject unsupported stages/fields and credential-bearing options during JSON parameter parsing.

## Code style

- No generic `normalize/normalized` rewrite hooks; name each operation according to what it actually does.
- No `?:` for Task Plugin business, parameter or resource selection; prefer direct `if` and `switch`.
- No empty default methods, forwarding-only layers, redundant factories, speculative abstract base classes or duplicated validation calls.
- Follow repository `JAVA_RULES.md` and `JAVA_COMMENT_RULES.md`: describe API contracts, not obvious getters or method names.
- YakFlow Core/Runtime/Connectors own execution-time Options, Readers, Writers, JobGraph and Checkpoint. The Task Plugin does not build or execute jobs.
- Do not introduce Client/Planner/Execution/Workflow modules in this refactor. Existing `PipelineExecutor` and `JobClient` remain the future submission boundary.
