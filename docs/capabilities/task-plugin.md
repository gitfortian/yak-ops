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

## User-facing HOCON

```hocon
source {
  Jdbc {
    datasource_id = "mysql-source"
    table = "orders"
  }
}
transform {}
sink {
  Jdbc {
    datasource_id = "pg-target"
    table = "ods_orders"
    write_mode = "APPEND"
  }
}
```

`SyncHoconParser` reads HOCON directly into `SyncParameters`; `SyncHoconFormatter` exports it. No intermediate JSON conversion is needed.
HOCON and JSON both normalize to the same typed product parameters and canonical JSON snapshot.
Only explicitly supplied `parametersFormat: "HOCON"` selects HOCON; default is JSON.
`GET /api/v1/task-definitions/{id}/parameters/hocon` exports the current canonical snapshot.
HOCON comments, whitespace, and key order do not change the executable Definition Version.

HOCON is parsed **in memory only**. Includes from local files, classpath or URLs, substitutions,
unsupported top-level keys (including `env`), unknown connector parameters, nonempty Transform,
excessive input length/depth and inline credentials are rejected. Never call `ConfigFactory.load()`
or resolve host environment variables for user-supplied configurations.

The older flat DATA_SYNC parameter JSON is not a second canonical schema. Draft development
definitions using parameter schema v1 must be recreated or explicitly migrated; they are not
silently rewritten in place. There are no Flyway changes because `parameters` is already JSON
and each new DATA_SYNC snapshot records `parameter_schema_version=2`.

## HTTP and former editor

The old `/api/v1/data-sync/tasks` Definition-only endpoints remain as an HTTP adapter to the
same generic DefinitionService, without a second Business Service or persistence model.
Runtime/Realtime/Retry tuning from the old editor is **not stored** in schema v2.
Legacy realtime APPEND requests return an explicit HTTP 400 instead of silently switching
to UPSERT; the frontend now uses canonical Task Definition CRUD and sends UPSERT for CDC.
Definition filtering reads `source.type`, `source.datasourceId` and `sink.datasourceId`
through explicit safe JSON paths.

## Future execution boundary

A future, separately scoped product execution adapter resolves authorized Datasource resources
into runtime-only Connector configurations and assembles a YakFlow Pipeline. YakFlow Runtime
owns JobGraph, ExecutionGraph, readers, writers and checkpoint execution.
Neither HOCON parsing nor Task Definition persistence constructs jobs or submits them.
Workflow nodes reference stable `TaskDefinitionId + Version`, not an embedded Pipeline.

## API and validation simplification

`TaskPlugin` exposes `type`, `parameterSchemaVersion`, `parse`, `validate`, and `format`.
`TaskPluginRegistry` discovers `ServiceLoader<TaskPlugin>` entries directly; it does not create
plugins through `TaskPluginFactory` or silently accept aliases such as `data-sync`.

Parsing performs one local check, then `validate` performs one physical check for Definition
writes/publishing. The validator is read-only: requested Database / Schema identifiers are not
silently changed to match Datasource bindings. Conflicting identifiers fail explicitly.

`TaskValidationContext` and top-level `DataSourceInfo` are stable API contracts; the host
looks up each physical table's schema once. The Connector retains JDBC native type and dialect
ownership; the Task Plugin does not maintain a second database-vendor allowlist.
