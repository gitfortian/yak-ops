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

## Execution Package Organization

Execution code is grouped by responsibility, not by OFFLINE / REALTIME duplication:

```text
io.yak.ops.business.datasync.execution
├── executor
│   ├── OfflineSyncExecutor
│   └── RealtimeSyncExecutor
├── planning
│   ├── DataSyncSchemaResolver
│   ├── OfflineSyncExecutionPlan
│   ├── OfflineSyncExecutionPlanner
│   ├── RealtimeSyncExecutionPlan
│   └── RealtimeSyncExecutionPlanner
├── lifecycle
│   ├── DataSyncExecutionRegistry
│   └── DataSyncExecutionRecovery
└── realtime
    ├── RealtimeSyncStateManager
    └── MySqlCdcServerIdAllocator
```

Responsibilities:
- `executor` owns Runtime submission, status transition, metrics flush and terminal-state handling.
- `planning` owns Task snapshot -> YakFlow Source / Sink / schema execution-plan projection.
- `lifecycle` owns OFFLINE / REALTIME shared process-level registry and startup recovery.
- `realtime` owns REALTIME-only CDC state identity and MySQL replication resources.

Dependency direction:

```text
DataSyncServiceImpl
   ↓
executor ─────→ planning
   │              ↓
   ├────────→ lifecycle
   └────────→ realtime
                  ↑
          realtime planning only
```

Must:
- Keep the `execution` root package free of concrete classes.
- Keep OFFLINE and REALTIME executors together under `executor`.
- Keep execution plans, planners and shared schema projection together under `planning`.
- Keep shared process lifecycle classes under `lifecycle`.
- Keep MySQL CDC state/serverId ownership under `realtime`.
- Mirror responsibility packages in tests where package-private behavior is intentionally tested.

Must Not:
- Create one package per class.
- Duplicate `offline/planning` and `realtime/planning` subtrees while each contains only one or two tightly related classes.
- Let `lifecycle` or `realtime` depend back on `executor`.
- Put Datasource credentials or YakFlow runtime objects into lifecycle persistence.

## Task Definition

Must:
- Be Workspace-scoped.
- Keep task name unique inside one Workspace.
- Persist datasource references by datasource ID, not by copying credentials.
- Treat the current Datasource-bound database as authoritative. Task / mapping requests cannot override a bound database.
- Treat the current Datasource-bound Schema as authoritative when present. A task-level Schema is allowed only when the Datasource leaves Schema unbound.
- Keep `definitionVersion` starting at 1 and increment it on every successful task-definition update.
- Persist one type-specific YakFlow config JSON in `runtime_config`; `OFFLINE` uses batch/fetch/split tuning while `REALTIME` uses CDC/checkpoint/write tuning.
- Persist target data semantics as first-class Task field `writeMode`; do not place APPEND / OVERWRITE / UPSERT inside `runtime_config`.
- Keep `DataSyncWriteMode` persistence values stable: APPEND=1, OVERWRITE=2, UPSERT=3.
- OFFLINE accepts APPEND, OVERWRITE and UPSERT. REALTIME remains fixed to APPEND at the Task layer.
- REALTIME currently persists APPEND for the shared Task contract while runtime application continues through `JdbcWriteMode.CHANGELOG`; realtime writeMode is not user-configurable.
- Support `OFFLINE` and `REALTIME` task definitions.
- Keep `REALTIME` Source limited to MySQL CDC in the current milestone.
- Keep `REALTIME` Target limited to MySQL / PostgreSQL / Oracle JDBC sinks in the current milestone.
- Require a primary key on the `REALTIME` Source table.
- Require the REALTIME Target primary-key set to exactly match the Source primary-key set through case-insensitive same-name mapping; PK order may differ, but missing, extra or different PK fields are invalid.

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

Each Instance must persist its own `syncType` snapshot in addition to task ID/name/version. Historical Instance filtering must not depend on the current Task row because Tasks may be deleted.

The instance `definitionSnapshot` is immutable execution input captured when an instance starts. It includes task name, sync type, write mode, datasource IDs/names/types, table locations and the type-specific runtime config.

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
Instance page queries may additionally filter by persisted `sync_type` so OFFLINE and REALTIME product surfaces never mix historical execution records.

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
- realtime state ownership and serverId allocation stay inside `execution.realtime` and never become Task DTO fields.
- frontend routes remain a later stage.

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
- Persist a sanitized definition snapshot before execution starts, including the Task's `writeMode`.
- Resolve runtime datasource credentials by datasource ID only after the instance exists.
- Keep at most one PENDING / RUNNING instance per task in the current single-node product.
- Register each active LocalExecution in the in-process execution registry before transitioning the instance to RUNNING.
- Allow PENDING and RUNNING instances to be canceled.
- Mark all leftover PENDING / RUNNING instances LOST at application startup because Local Execution Engine is not process-recoverable.
- Revalidate current Catalog field compatibility when a task is started.
- Build runtime Catalog schema, datasource connections and JDBC Source / Sink through `OfflineSyncExecutionPlanner`.
- Map OFFLINE APPEND to `JdbcSaveMode.APPEND + JdbcWriteMode.INSERT`.
- Map OFFLINE OVERWRITE to `JdbcSaveMode.OVERWRITE + JdbcWriteMode.INSERT`; target TRUNCATE happens before Source rows are written.
- Map OFFLINE UPSERT to `JdbcSaveMode.APPEND + JdbcWriteMode.UPSERT`.
- Require a target primary key for UPSERT and require the Source mapping to contain every target primary-key field, including all parts of a composite key.
- Revalidate UPSERT primary-key requirements at both save time and run time because Catalog metadata may change.
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
- Revalidate current realtime Source/Target topology, Source primary key, exact Source/Target primary-key correspondence and field compatibility before creating execution input.
- Persist `syncType`, the fixed APPEND `writeMode` and the type-specific config in the sanitized definition snapshot.
- Resolve source/target runtime credentials only inside `RealtimeSyncExecutionPlanner` after the Instance exists.
- Reuse `DataSyncSchemaResolver` so source event value order and target physical column names stay aligned across MySQL/PostgreSQL/Oracle.
- Use `JdbcWriteMode.CHANGELOG` for INSERT/UPDATE/DELETE application.
- Start the Local Execution Engine with the task's `checkpointIntervalSeconds`.
- Reuse the process-local execution registry so the existing cancel API works for both OFFLINE and REALTIME.
- Persist Runtime counters while RUNNING.

Realtime state ownership:
- State root is `${yak.ops.home}/data/data-sync/realtime`; when `yak.ops.home` is absent the current working directory is the base.
- State scope is `{workspaceId}/{taskId}/v{definitionVersion}`.
- `offsets.dat` and `schema-history.dat` are connector-owned files inside that product-owned scope.
- Debezium engine name must be stable for the same Workspace / Task / definitionVersion.
- A later Instance for the same definitionVersion reuses the same state scope.
- A changed definitionVersion uses a fresh state scope and restarts from snapshot.
- Application restart marks old active Instances LOST, but does not delete REALTIME state.
- A later manual run may continue from the latest completed Debezium offset through a new Instance.
- `MySqlCdcServerIdAllocator` must keep active serverIds unique inside the current single-node process, prefer a stable ID derived from the state key, resolve collisions by probing, and release the lease when execution ends.
- `DataSyncExecutionRecovery` owns startup LOST recovery for both OFFLINE and REALTIME.
- `DataSyncExecutionRegistry` owns process-local cancel references for both OFFLINE and REALTIME.

Must Not:
- Claim automatic Instance resurrection after process restart.
- Claim exactly-once.
- Persist state directory paths, Debezium offsets, schema history or serverId leases in Task/Instance definition JSON.
- Delete REALTIME state merely because an Instance becomes CANCELED / FAILED / LOST.
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

The offline milestone remains Phase 4 and fully executable. Realtime Phase 5 adds persisted Instance runtime UI on top of the existing single-node lifecycle.

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

Realtime Phase 5 additionally implements:
- `REALTIME` task type persistence and query.
- dedicated realtime runtime config DTO / VO.
- MySQL Source + MySQL/PostgreSQL/Oracle Target contract validation.
- Source primary-key validation.
- sanitized realtime definition snapshots.
- `RealtimeSyncExecutionPlanner` with MySQL CDC Source + JDBC CHANGELOG Sink.
- `RealtimeSyncExecutor` with RUNNING / FAILED / CANCELED lifecycle and Runtime counters.
- shared `DataSyncExecutionRegistry` cancel semantics.
- centralized `DataSyncExecutionRecovery` startup LOST handling.
- task-configured automatic checkpoint interval inside the Local Execution Engine.
- durable task/version-scoped CDC state directories.
- stable Debezium engine identity across Instances.
- controlled MySQL CDC serverId allocation/release.
- manual rerun continuation from persisted Debezium offsets after stop/failure/process restart.
- persisted Instance `syncType` for historical OFFLINE / REALTIME filtering.
- REALTIME Instance list/detail frontend, active polling, Stop action and persisted event counters.

Phase 4 / Realtime Phase 5 do not implement:
- automatic restart of a LOST Instance.
- distributed state ownership or fencing.
- exactly-once transaction coordination.
- realtime frontend.
- scheduler.
- retry policy.
- distributed workers.
- process-level execution recovery.
