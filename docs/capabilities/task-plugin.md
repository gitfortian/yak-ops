# Task Plugin and DATA_SYNC Pipeline Configuration

Status: Active — Task Definition parameter schema v2.

## Stable ownership

- Task Business owns Task Definition identity, publication and immutable version transactions.
- Task plugins parse and check local parameters, then validate authorized Workspace resources once without rewriting the submitted Definition.
- `DATA_SYNC` is a Task plugin, not a YakFlow Connector. Its product parameters refer to **Datasource IDs and table names only**.
- Connector-specific split sizes, fetch settings, CDC credentials and checkpoint tuning remain in YakFlow Core/Runtime/Connector internals.
- `PipelineExecutor` / `JobClient` already exist in YakFlow Core; this scope introduces no new Client module, job submission or runtime integration.

## Definition schema v2

The canonical `TaskDefinitionVersion.parameters` is immutable JSON:

```json
{
  "source": {"type":"JDBC","datasourceId":"mysql-source","table":"orders"},
  "transform": [],
  "sink": {"type":"JDBC","datasourceId":"pg-target","table":"ods_orders","writeMode":"APPEND"}
}
```

Source types: `JDBC` (bounded) and `MYSQL_CDC` (continuous). Sink type: `JDBC`.
A single source and sink are required. No nonempty Transform or multiple stages are supported yet.
CDC uses `UPSERT` and matching source/target primary keys; it is **not** equivalent to APPEND.
OVERWRITE is excluded pending explicit execution/recovery semantics.

The Source and Sink parameter records live in `datasync/source` and `datasync/sink`.
They must not duplicate `JdbcSourceOptions`, `MySqlCdcSourceConfig`, or other physical Connector config classes.
New Connector product types add their own parameter type under the relevant stage package.
Do not introduce speculative SourceFactory/SinkFactory interfaces or an execution Planner in the Task Plugin.

## JSON configuration

Task Definition accepts JSON only. The HTTP `parameters` field contains a serialized JSON
string of the schema v2 pipeline above. The frontend uses `JSON.stringify` and the
`SyncPlugin` parses it directly into `SyncParameters` through `JSONUtils`.
Unknown connector fields (including embedded credentials), invalid JSON, nonempty Transform
and unsupported Source/Sink types are rejected. The trusted Workspace validation
context checks datasource ownership, table compatibility and required primary keys.

The older flat DATA_SYNC parameter JSON is not a second canonical schema. Draft development
definitions using parameter schema v1 must be recreated or explicitly migrated; they are not
silently rewritten in place. There are no Flyway changes because `parameters` is already JSON
and each new DATA_SYNC snapshot records `parameter_schema_version=2`.

## HTTP

`TaskDefinitionController` exposes the single Definition HTTP contract under
`/api/v1/task-definitions`. The former `/api/v1/data-sync/tasks` adapter has been removed.
The frontend submits schema v2 pipeline JSON as a `parameters` string, with
`MYSQL_CDC` and `UPSERT` for realtime definitions; publish/unpublish use the same API.
Runtime/Realtime/Retry tuning from the former editor is **not stored** in schema v2.
Definition filtering reads `source.type`, `source.datasourceId` and `sink.datasourceId`
through explicit safe JSON paths.

## Future execution boundary

A future, separately scoped product execution adapter resolves authorized Datasource resources
into runtime-only Connector configurations and assembles a YakFlow Pipeline. YakFlow Runtime
owns JobGraph, ExecutionGraph, readers, writers and checkpoint execution.
Neither Task Definition parsing nor persistence constructs jobs or submits them.
Workflow nodes reference stable `TaskDefinitionId + Version`, not an embedded Pipeline.

## API and validation simplification

`TaskPlugin` exposes `type`, `parameterSchemaVersion`, `parse`, and `validate`.
`TaskPluginRegistry` discovers `ServiceLoader<TaskPlugin>` entries directly; plugin implementations
declare `@AutoService(TaskPlugin.class)`, which generates the provider metadata during compilation.
It does not create plugins through factories or silently accept aliases such as `data-sync`.

Parsing performs one local check, then `validate` performs one physical check for Definition
writes/publishing. The validator is read-only: requested Database / Schema identifiers are not
silently changed to match Datasource bindings. Conflicting identifiers fail explicitly.

`TaskValidationContext` and top-level `DataSourceInfo` are stable API contracts; the host
looks up each physical table's schema once. The Connector retains JDBC native type and dialect
ownership; the Task Plugin does not maintain a second database-vendor allowlist.
