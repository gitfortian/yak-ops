# Data Development SQL Task Runtime - Stage 4

## 1. Goal

Stage 4 turns the SQL Task Plugin from a validation-only skeleton into a real executable plugin and connects the Data Development `Run` action to the existing datasource plugin system.

The stage deliberately keeps authoring and execution separate:

```text
current editor definition
        |
        | POST /nodes/{id}/run
        v
DevelopmentTaskRunService
        |
        v
TaskPluginRegistry
        |
        v
SqlTaskPlugin
        |
        | TaskExecutionContext capability
        v
DataSourceExecutionProvider
        |
        v
DataSourcePluginRegistry
        |
        v
Datasource Plugin
        |
        v
DataSourceSqlExecutor
        |
        v
Database
```

A manual run executes the current editor content. It does **not** implicitly save a Draft and does **not** publish a Revision.

## 2. Datasource execution contract

The datasource plugin API now exposes a small SQL execution boundary:

- `DataSourceExecutionProvider`: resolves a durable datasource reference without exposing credentials to Task Plugins.
- `DataSourceSqlExecutor`: one physical SQL execution attempt with optional cancellation.
- `DataSourceSqlRequest`: SQL text, row limit and statement timeout.
- `DataSourceSqlResult`: result-set/update-count response.
- `DataSourceSqlColumn`: JDBC-neutral column metadata.

`DataSourcePlugin#createSqlExecutor` defaults to unsupported. JDBC datasource plugins opt in through `AbstractJdbcDataSourcePlugin`, so MySQL/PostgreSQL/Oracle/SQL Server/Dameng/Kingbase and Doris can reuse the same physical execution mechanism while keeping datasource-specific connection parsing in their own plugins.

The SQL Task Plugin never receives connection passwords, JDBC URLs or repository objects. It persists only a datasource ID in `TaskDefinition.configJson` and asks the runtime capability for an executor when a task attempt starts.

## 3. SQL Task Plugin schemaVersion=1

The SQL plugin currently understands:

```json
{
  "dataSourceId": "123",
  "maxRows": 200,
  "timeoutSeconds": 30
}
```

Only `dataSourceId` is currently written by the UI. Runtime defaults are:

- `maxRows = 200`
- `timeoutSeconds = 30`

Hard guards in this stage:

- max rows: `1..500`
- timeout: `1..300` seconds

The descriptor is now `executable=true` and `cancellable=true`. The plugin validates SQL content, schema version, datasource reference and runtime config before creating an executor.

## 4. JDBC execution behavior

`JdbcDataSourceSqlExecutor` uses `Statement.execute(...)`, so one SQL task can return either:

### Result set

```text
columns
rows
returnedRows
truncated
```

The executor requests `maxRows + 1` rows internally to detect truncation without returning the extra row.

### Update / DDL count

```text
affectedRows
```

Statement timeout is applied with JDBC `setQueryTimeout` when supported. Cancellation is best-effort through `Statement.cancel()` and connection close.

Large values are bounded before leaving the datasource layer: long text is truncated, large binary values are represented by a placeholder, and common temporal/vendor values are converted into JSON-safe strings.

## 5. Data Development manual run API

New endpoint:

```http
POST /api/v1/data-development/nodes/{nodeId}/run
```

Request uses the plugin-neutral definition envelope:

```json
{
  "taskType": "SQL",
  "schemaVersion": 1,
  "content": "select 1",
  "configJson": "{\"dataSourceId\":\"123\"}"
}
```

Response:

```json
{
  "status": "SUCCESS",
  "message": "",
  "durationMs": 12,
  "output": {
    "kind": "RESULT_SET",
    "columns": [],
    "rows": [],
    "returnedRows": 0,
    "affectedRows": -1,
    "truncated": false,
    "dataSourceId": "123"
  }
}
```

The current endpoint is synchronous because Stage 4 only proves the real SQL execution path. `TaskExecution` persistence, asynchronous attempt handles, cross-request cancellation and unified Manual/Workflow/Schedule runtime belong to the later Task Runtime stage.

## 6. Frontend behavior

The Data Development workbench now:

1. Executes the current in-memory SQL and datasource selection, including unsaved editor changes.
2. Opens the bottom result panel immediately and shows `RUNNING` state.
3. Renders real result-set rows/columns in a compact grid.
4. Shows update/DDL affected rows.
5. Shows backend execution duration.
6. Surfaces datasource/JDBC/plugin errors in the result panel and message notification.
7. Indicates when the result is truncated by the row guard.

Only SQL advertises `run=true` in the frontend registry in Stage 4. Shell/HTTP/Python keep their authoring placeholders until their Task Plugins are executable.

## 7. Boundary with Draft / Revision

Manual Run and Publish intentionally differ:

```text
Run
  current editor -> execute now
  no Draft mutation
  no Revision creation

Save
  current editor -> TaskDraft

Publish
  TaskDraft -> immutable TaskRevision
```

This preserves the Stage 3 concurrency/version guarantees while still allowing IDE-style exploratory execution before saving.

## 8. Tests

Added/updated unit coverage for:

- SQL plugin datasource validation and execution through a typed runtime capability.
- SQL plugin ServiceLoader assembly now advertising executable/cancellable.
- Data Development manual run routing the current definition through Task Plugin.

Recommended local verification before Ready for Review:

```bash
mvn -pl yak-ops-plugins/yak-ops-plugin-task/yak-ops-plugin-task-sql -am test
mvn -pl yak-ops-business/yak-ops-business-data-development -am test
mvn -pl yak-ops-boot -am package -DskipTests

cd yak-ops-ui
npm run tsc
npm run build
```

Use at least one real MySQL/PostgreSQL/Doris datasource to verify:

- SELECT result grid
- empty result set
- DML affected rows
- SQL syntax error
- missing datasource
- row truncation
- statement timeout

## 9. Deliberately out of scope

Stage 4 does not add:

- durable `TaskExecution` / attempt tables
- asynchronous run polling
- HTTP stop/cancel endpoint
- Workflow or Schedule execution
- Task Asset / Task Catalog
- Workflow `taskAssetId + taskRevisionId` binding
- Shell/Python/HTTP execution
- worker/container/Kubernetes backends

Those boundaries keep this stage focused on proving that the same platform Task Plugin can execute real SQL by consuming the existing datasource plugin capability.
