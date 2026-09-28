# Data Sync Rules

Scope:
- `yak-ops-business/yak-ops-business-data-sync/**`
- `yak-ops-common/src/main/java/io/yak/ops/common/**/datasync/**`
- `yak-ops-dao/src/main/java/io/yak/ops/dao/**/datasync/**`
- `yak_ops_data_sync_*` schema

Status:
- Active / Staged

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`
- `/yak-ops-business/BUSINESS_RULES.md`
- `/yak-ops-common/DTO_VO_RULES.md`
- `/yak-ops-dao/DAO_RULES.md`
- `/yak-ops-dao/ENTITY_RULES.md`
- `/yak-ops-dao/FLYWAY_RULES.md`
- `/docs/capabilities/data-sync/README.md`

## Business Boundary

Data Sync owns product task definitions and task instances. YakFlow owns execution mechanics.

```text
DataSyncService
      ↓
Task / Instance persistence

OfflineSyncExecutor                 RealtimeSyncExecutor
      ↓                                   ↓
OfflineSyncExecutionPlanner         RealtimeSyncExecutionPlanner
      ↓                                   ↓
OfflineSyncExecutionPlan            RealtimeSyncExecutionPlan
      └──────────────→ YakFlow Local Execution Engine ←──────────────┘
```

This capability explicitly uses `DataSyncService / DataSyncServiceImpl` naming.

## Task Definition

Must:
- Be Workspace-scoped.
- Keep task name unique inside one Workspace.
- Persist datasource references by datasource ID, not by copying credentials.
- Treat the current Datasource-bound database as authoritative. Task / mapping requests cannot override a bound database.
- Treat the current Datasource-bound Schema as authoritative when present. A task-level Schema is allowed only when the Datasource leaves Schema unbound.
- Keep `definitionVersion` starting at 1 and increment it on every successful task-definition update.
- Persist one type-specific YakFlow config JSON in `runtime_config`; `OFFLINE` uses batch/fetch/split tuning while `REALTIME` uses CDC/checkpoint/write tuning.
- Support `OFFLINE` and `REALTIME` task definitions.
- Keep `REALTIME` Source limited to MySQL CDC in the current milestone.
- Keep `REALTIME` Target limited to MySQL / PostgreSQL / Oracle JDBC sinks in the current milestone.
- Require a primary key on the `REALTIME` Source table. Same-name mapping compatibility guarantees those key fields also exist on the Target.

Must Not:
- Persist datasource password, `connection_params`, `original_json`, SSH private key, token or other secret in a task.
- Create a second datasource connection model inside Data Sync.
- Start YakFlow as a side effect of create/update/query methods in Phase 1.

## Task Instance

Task instance is historical execution state, not the current task definition.

Persisted instance states:

```text
PENDING
RUNNING
SUCCEEDED
FAILED
CANCELED
LOST
```

Trigger types:

```text
MANUAL
SCHEDULE
RETRY
```

Phase 1 only defines persistence. Runtime state transitions are introduced with execution work.

The instance `definitionSnapshot` is immutable execution input captured when an instance starts. It may include task name, datasource IDs/names/types, table locations, runtime config and future field mappings.

It must never contain:
- normalized datasource connection JSON.
- original datasource JSON.
- database passwords.
- SSH password/private key/passphrase.
- API tokens, access keys or equivalent secrets.

## Persistence

Tables:

```text
yak_ops_data_sync_task
yak_ops_data_sync_instance
```

No database physical foreign keys.

Repository queries must always scope Task / Instance product access by `workspace_id`.

Task deletion does not imply deleting historical instances.

## Realtime Task Definition Contract

The first realtime product contract reuses the existing Task model rather than creating a second realtime task table or service.

Persisted type-specific config:

```text
OFFLINE
  -> DataSyncRuntimeConfig

REALTIME
  -> DataSyncRealtimeConfig
```

Realtime config owns only product/runtime tuning:

- `checkpointIntervalSeconds`
- `queueCapacity`
- `pollBatchSize`
- `writeBatchSize`
- `timeoutSeconds`

It must not expose Debezium offsets, schema-history files, state directories or MySQL `serverId`; those belong to realtime execution/runtime ownership.

Realtime contract boundary:
- create/update/detail/page persist and return `REALTIME` tasks.
- realtime save-time validation resolves Datasource/Catalog again on the backend.
- `runTask` may create and submit a REALTIME Instance through `RealtimeSyncExecutor`.
- durable checkpoint/state-directory ownership, stable serverId allocation and frontend routes remain later stages.

## Offline Task Editor Contract

Phase 2 publishes task CRUD and mapping-preview HTTP contracts for the offline task editor.

Field mapping rules:
- Mapping is automatic by case-insensitive same-name field matching.
- The editor is read-only for mappings; no rename, expression or Transform exists.
- Backend mapping preview is the source of truth.
- Mapping preview and run-time validation must reuse JDBC logical compatibility after Catalog fields are projected to `YakColumn`; Data Sync must not maintain its own `java.sql.Types` family rules.
- Backend must canonicalize database / Schema scope from the referenced Datasource before Catalog lookup and before task persistence; frontend values are hints only for unbound scope levels.
- Task create/update must repeat the same backend compatibility validation; frontend state cannot bypass it.
- String / binary target capacity must not be smaller when both sides expose size metadata.
- DECIMAL target precision / scale must not be smaller when metadata is available.
- Numeric widening is limited to integer → integer/decimal and decimal → decimal.
- String ↔ numeric and other implicit Transform are rejected.

## Offline Execution Lifecycle

Phase 3 enables manual execution of saved OFFLINE tasks.

Lifecycle:

```text
run task
   ↓
PENDING
   ↓
RUNNING
   ├── SUCCEEDED
   ├── FAILED
   └── CANCELED
```

Must:
- Persist a sanitized definition snapshot before execution starts.
- Resolve runtime datasource credentials by datasource ID only after the instance exists.
- Keep at most one PENDING / RUNNING instance per task in the current single-node product.
- Register each active LocalExecution in the in-process execution registry before transitioning the instance to RUNNING.
- Allow PENDING and RUNNING instances to be canceled.
- Mark all leftover PENDING / RUNNING instances LOST at application startup because Local Execution Engine is not process-recoverable.
- Revalidate current Catalog field compatibility when a task is started.
- Build runtime Catalog schema, datasource connections and JDBC Source / Sink through `OfflineSyncExecutionPlanner`.
- Keep `OfflineSyncExecutionPlan` in memory only; it may hold runtime connection objects indirectly and must never be persisted, serialized into an Instance or logged.
- Keep `OfflineSyncExecutor` focused on execution lifecycle, metrics, cancellation and terminal-state persistence.
- Keep historical instances after task deletion; active instances block task deletion.

Must Not:
- Persist runtime DataSourceConnection or credentials into definitionSnapshot.
- Expose DataSourceService.resolveRuntimeConnection through Boot.
- Claim distributed execution or restart recovery.
- Add scheduler / retry policy in Phase 3.

## Realtime Execution Lifecycle

PR2 enables manual execution of saved REALTIME tasks.

Lifecycle:

```text
run REALTIME task
      ↓
PENDING
      ↓
RealtimeSyncExecutionPlanner
      ↓
MySqlCdcSource(snapshot.mode=initial + binlog)
      ↓
LocalExecutionEngine
      ↓
JdbcSink(CHANGELOG)
      ↓
RUNNING
   ├── CANCELED
   └── FAILED
```

Must:
- Revalidate current realtime Source/Target topology, Source primary key and field compatibility before creating execution input.
- Persist `syncType` and the type-specific config in the sanitized definition snapshot.
- Resolve source/target runtime credentials only inside `RealtimeSyncExecutionPlanner` after the Instance exists.
- Reuse `OfflineSyncSchemaResolver` so source event value order and target physical column names stay aligned across MySQL/PostgreSQL/Oracle.
- Use `JdbcWriteMode.CHANGELOG` for INSERT/UPDATE/DELETE application.
- Start the Local Execution Engine with the task's `checkpointIntervalSeconds`.
- Reuse the process-local execution registry so the existing cancel API works for both OFFLINE and REALTIME.
- Persist Runtime counters while RUNNING.

Current PR2 bootstrap only:
- CDC state is placed under `java.io.tmpdir/yak-ops/realtime-sync/{instanceId}`.
- MySQL replication `serverId` is temporarily derived from the Instance ID.
- A process restart still marks active instances LOST and does not restore REALTIME execution.

Must Not:
- Claim durable checkpoint recovery or exactly-once.
- Treat the temporary state directory as product-owned persistent state.
- Treat the temporary instance-derived serverId as the final allocation contract.
- Allow a continuous REALTIME execution to finish as SUCCEEDED; unexpected Source completion is FAILED.

## Metrics + Acceptance

Phase 4 closes the first offline-sync milestone with observable row metrics and real cross-database acceptance.

Metrics:
- `readRows` and `writeRows` are copied from YakFlow `ExecutionMetrics` into the Instance while RUNNING.
- Active metrics are flushed approximately every 500ms and once again after Runtime termination.
- Metrics must never decrease within one Instance.
- Final successful Instance metrics must match the Runtime final snapshot.

Acceptance:
- CI must execute backend tests; `verify -DskipTests` is forbidden.
- Real Testcontainers coverage must prove MySQL -> MySQL, MySQL -> PostgreSQL and MySQL -> Oracle.
- H2 compatibility tests remain useful unit/integration coverage but are not the final cross-database acceptance proof.

## Current Phase

The offline milestone remains Phase 4 and fully executable. Realtime Phase 2 adds single-node manual execution wiring.

Phase 4 implements:
- task create/update/delete/detail/page.
- instance detail/page query.
- persistence contracts.
- Datasource Catalog reads through DataSourceService.
- task HTTP CRUD / page endpoints.
- backend automatic field mapping preview and save-time validation.
- offline task editor frontend.
- manual instance creation and run.
- PENDING / RUNNING / SUCCEEDED / FAILED / CANCELED lifecycle.
- in-process LocalExecution registry and cancel.
- startup LOST recovery.
- instance list / detail product contract.
- Runtime read/write row metrics persisted into Instance.
- real MySQL -> MySQL/PostgreSQL/Oracle JDBC acceptance in CI.
- save-and-run product flow.

Realtime Phase 2 additionally implements:
- `REALTIME` task type persistence and query.
- dedicated realtime runtime config DTO / VO.
- MySQL Source + MySQL/PostgreSQL/Oracle Target contract validation.
- Source primary-key validation.
- sanitized realtime definition snapshots.
- `RealtimeSyncExecutionPlanner` with MySQL CDC Source + JDBC CHANGELOG Sink.
- `RealtimeSyncExecutor` with RUNNING / FAILED / CANCELED lifecycle and Runtime counters.
- shared active-execution registry / cancel semantics.
- task-configured automatic checkpoint interval inside the Local Execution Engine.

Phase 4 / Realtime Phase 2 do not implement:
- durable realtime checkpoint/state-directory ownership.
- stable MySQL replication serverId allocation.
- realtime process restart recovery.
- realtime frontend.
- scheduler.
- retry policy.
- distributed workers.
- process-level execution recovery.
