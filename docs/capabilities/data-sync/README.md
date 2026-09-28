# Data Sync Capability

Status: Offline Phase 4 + Realtime Phase 6 Cross-Database Acceptance

## Goal

Data Sync is the Yak Ops product layer above YakFlow.

The first product milestone is offline synchronization:

```text
Task Definition
      ↓
Task Instance
      ↓
YakFlow Local Execution Engine
      ↓
JdbcSource -> JdbcSink
```

Phase 1 established task and instance persistence. Phase 2 added Catalog reads and the offline task editor. Phase 3 starts saved OFFLINE tasks with YakFlow Local Execution Engine and persists the execution lifecycle. Realtime Phase 1 added the shared Task contract; Realtime Phase 2 now wires saved REALTIME tasks into the existing YakFlow Local Execution Engine.

## Manual Product E2E

Automated Acceptance proves Runtime / Connector behavior against real databases. Product-level human acceptance is documented separately in [Data Sync Manual E2E Playbook](../../e2e/data-sync/README.md).

The Manual E2E layer starts from Source / Target DDL and seed data, walks through the Yak Ops UI, runs the Task, and verifies the final Target rows with SQL. It complements automated tests; it does not replace them.

## Task Definition

A task answers:

> What should be synchronized from which datasource/table to which target datasource/table?

Current task types:

```text
OFFLINE
REALTIME
```

Both types reuse the same Task table and source/target table identity. The persisted `runtime_config` JSON is type-specific rather than a shared configuration schema:
- OFFLINE -> `DataSyncRuntimeConfig`
- REALTIME -> `DataSyncRealtimeConfig`

A task persists:

- Workspace ownership.
- source / target datasource IDs.
- source / target database, schema and table locations.
- first-class `writeMode`: APPEND / OVERWRITE / UPSERT; this is task semantics, not runtime tuning.
- YakFlow runtime tuning JSON, including optional JDBC dynamic `splitSize` and bounded Source `sourceParallelism`.
- definition version for executable Task semantics. The staged publication contract keeps metadata-only changes on the same version and increments only when executable definition changes.
- user-facing name and remark.

Datasource credentials never belong to the task table.

Current write-mode rollout is staged:

```text
APPEND      executable; preserves existing target data
OVERWRITE   executable; commits TRUNCATE TABLE before the INSERT load
UPSERT      executable; updates/inserts by target primary key using native JDBC dialect SQL
```

REALTIME tasks currently persist APPEND for the shared Task contract while runtime writes continue through `JdbcWriteMode.CHANGELOG`.

## Task Publication Lifecycle

The next Task lifecycle milestone introduces an explicit publication gate shared by OFFLINE and REALTIME.

Full contract: [Task Publication Lifecycle](./task-lifecycle.md).

Product actions are “上线 / 下线”. Persisted status values are intentionally named `PUBLISHED / UNPUBLISHED` rather than `ONLINE / OFFLINE` so they do not collide semantically with `DataSyncType.OFFLINE`.

Target lifecycle:

```text
Create
  ↓
UNPUBLISHED v1
  ├── Edit executable definition → v2
  └── Publish
         ↓
     PUBLISHED vN
         ├── Run / Start → Instance(taskVersion = N)
         └── Unpublish → UNPUBLISHED vN
```

Core rules:

- create produces `UNPUBLISHED v1`.
- executable definition is editable only while unpublished.
- publish/unpublish do not create a new definition version.
- only published Tasks may create new Instances.
- unpublish never means Stop; active Instances must end or be canceled first.
- terminal Instance status never changes Task publication status.
- metadata-only changes such as name/remark do not create a new definition version.
- V1 keeps one current Task version; no separate draft/published versions or version-history table are introduced.
- existing rows will be migrated as `PUBLISHED` to preserve current executability; new rows start `UNPUBLISHED`.

The backend lifecycle contract is active: new Tasks start UNPUBLISHED, publish/unpublish are explicit commands, update/delete/run are status-gated, and executable-definition changes own version increments. Frontend action adoption remains the next PR.

## MySQL CDC Source Requirements

REALTIME currently uses the Debezium MySQL connector. The Source MySQL server and account must satisfy the connector prerequisites before a task is started:

- binary logging enabled.
- `binlog_format=ROW`.
- `binlog_row_image=FULL`.
- the CDC account has the permissions needed for initial snapshot and binlog streaming: `SELECT`, `RELOAD`, `SHOW DATABASES`, `REPLICATION SLAVE`, and `REPLICATION CLIENT`.
- hosted MySQL variants may require additional snapshot-lock privileges such as `LOCK TABLES` depending on their locking model.
- the Source table has a primary key, and the Target primary-key set exactly matches it through case-insensitive same-name mapping.

The application allocates the Debezium replication `serverId`; it is not a user-editable task field.

Reference: [Debezium MySQL connector setup](https://debezium.io/documentation/reference/stable/connectors/mysql.html#setting-up-mysql).

## Realtime Phase 1 — Task Contract

Realtime Phase 1 intentionally stops at the product definition boundary:

```text
REALTIME Task
   ↓
MySQL Source table with primary key
   ↓
case-insensitive same-name field mapping
   ↓
Target primary-key set exactly matches Source primary-key set
   ↓
MySQL / PostgreSQL / Oracle Target
```

Realtime config currently contains:

- checkpoint interval.
- CDC queue capacity.
- CDC poll batch size.
- JDBC changelog write batch size.
- connection / statement timeout.

The REALTIME primary-key contract is strict: Source and Target must expose the same primary-key field set under case-insensitive same-name mapping. PK order may differ; missing, extra or different Target PK fields are rejected before execution.

Execution-only values such as Debezium state directory, offsets, schema history and MySQL replication `serverId` are not Task fields.

A REALTIME task can be created, updated, queried and filtered by `syncType`. Realtime Phase 2 adds manual execution without creating a second Task/Instance model.

## Realtime Phase 2 — Execution

Manual realtime run path:

```text
saved REALTIME Task
        ↓
create Instance(PENDING)
        ↓
sanitized snapshot(syncType + writeMode + realtimeConfig)
        ↓
RealtimeSyncExecutionPlanner
        ↓
MySqlCdcSource
(snapshot initial + binlog)
        ↓
LocalExecutionEngine
        ↓
JdbcSink(CHANGELOG)
        ↓
RUNNING
   ├── CANCELED
   └── FAILED
```

The planner re-reads Source/Target Catalog metadata and runtime connections, builds the existing source logical schema plus target physical write schema, and uses `JdbcWriteMode.CHANGELOG` so Debezium INSERT/UPDATE/DELETE events reuse the common `YakRow + RowKind` protocol.

The executor uses the same process-local Instance registry and cancel API as offline sync. Runtime `readRows/writeRows` are persisted while the job is RUNNING; for REALTIME these counters represent YakFlow change events rather than source-table business row cardinality.

## Realtime Phase 3 — Lifecycle + Checkpoint State Ownership

Realtime state is owned outside the Instance record:

```text
${yak.ops.home}/data/data-sync/realtime/
  {workspaceId}/
    {taskId}/
      v{definitionVersion}/
        offsets.dat
        schema-history.dat
```

The identity rule is deliberate:

```text
same task + same definitionVersion
        ↓
same Debezium engine name
same state directory
        ↓
new Instance continues from completed offset

definitionVersion changes
        ↓
new state directory
        ↓
fresh initial snapshot
```

The state files remain connector-private. Data Sync owns only their directory lifecycle and stable identity; Task/Instance JSON never stores Debezium offset structures.

For packaged runtime, `yak.ops.home` is the release home. The Docker image declares `/opt/yak-ops/data` as a volume. Production deployments that require restart continuation across container replacement must persist `${yak.ops.home}/data`; deleting or replacing that directory removes the file-backed CDC state and therefore removes the ability to resume from the previous Debezium offset.

MySQL replication `serverId` is now managed by a process-local allocator. It derives a preferred value from the stable realtime state key, probes on collision, and releases the lease after the execution ends.

Application restart still does not resurrect the previous LocalExecution:

```text
old RUNNING/PENDING Instance
        ↓ application restart
       LOST

same Task/version run again
        ↓
new Instance + new LocalExecution
        ↓
reuse existing connector-owned CDC state
        ↓
continue from persisted Debezium offset
```

This is connector-state continuation through a new Instance, not generic YakFlow Runtime checkpoint restoration, not resurrection of the old Instance, and not an exactly-once claim.

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
- sync type snapshot (OFFLINE / REALTIME).
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
Catalog field → YakColumn
        ↓
JDBC logical compatibility
        ↓
compatible?
        ↓ yes
save OFFLINE task definition
```

The editor contains only:
- basic information.
- source datasource/table.
- target datasource/table plus OFFLINE write mode.
- read-only automatic field mapping result.
- YakFlow runtime tuning.

Datasource scope rule:

```text
Datasource
   ↓ owns bound database / optional bound schema
Offline Sync
   ↓ selects only table, plus Schema only when Datasource leaves it unbound
```

A bound database is displayed as read-only context and cannot be overridden by a Task. Backend task persistence and mapping preview canonicalize scope from Datasource again, so API callers cannot bypass the UI rule.

No filter SQL, split key, custom pre/post SQL, resource group or Transform is introduced in this phase.

OFFLINE write modes now support:

```text
APPEND
  target rows preserved
  ↓
INSERT batches

OVERWRITE
  TRUNCATE TABLE target
  ↓ commit
  INSERT batches

UPSERT
  target primary key
  ↓
  MySQL ON DUPLICATE KEY UPDATE
  PostgreSQL ON CONFLICT DO UPDATE
  Oracle MERGE INTO
```

UPSERT requires the target table to have a primary key and the Source mapping to contain every target primary-key field. OVERWRITE is not an atomic table replacement. If the load fails after TRUNCATE commits, previous target rows are not restored.

## Phase 3 — Offline Execution

Manual run path:

```text
saved Task
    ↓
create Instance(PENDING)
    ↓
sanitized definition snapshot
    ↓
OfflineSyncExecutionPlanner
    ↓
ExecutionPlan(JdbcSource / JdbcSink / sourceSchema)
    ↓
LocalExecutionEngine
    ↓
RUNNING
    ├── SUCCEEDED
    ├── FAILED
    └── CANCELED
```

The current runtime is intentionally single-node. The in-process execution registry exists only to map a running instance to its LocalExecution for cancellation.

`OfflineSyncExecutionPlan` is an in-memory runtime object only. It is built after the Instance exists, may reference runtime datasource connections through JDBC connectors, and must never be persisted or exposed through product APIs.

On application startup:

```text
PENDING / RUNNING from previous process
              ↓
             LOST
```

This is deliberate: Local Execution Engine has no process restart recovery.

## Phase 4 — Metrics + Acceptance

YakFlow exposes a lightweight execution metrics snapshot:

```text
Source batch enters channel
        ↓
readRows + N

SinkWriter.write succeeds
        ↓
writeRows + N
```

OfflineSyncExecutor persists the latest values while the instance is RUNNING and performs a final metrics flush after Runtime termination.

The first offline milestone acceptance is no longer H2-only. CI runs real database containers:

```text
MySQL Source
├── MySQL Sink
├── PostgreSQL Sink
└── Oracle Sink
```

Each acceptance path verifies target rows and final YakFlow metrics.

## Current Capability Boundary

The current product surface includes:

- Workspace-scoped OFFLINE / REALTIME Task CRUD.
- Task publication lifecycle backend with UNPUBLISHED / PUBLISHED state, publish/unpublish commands and status-gated update/delete/run.
- OFFLINE / REALTIME Task lifecycle UI with publication status badges/filtering, publish/unpublish actions, active-instance Stop actions and explicit Save / Save & Publish editor flow.
- persisted Task Instance history with `syncType`, lifecycle status and sanitized definition snapshot.
- shared Catalog-driven mapping validation.
- OFFLINE manual execution, metrics, APPEND / OVERWRITE / UPSERT behavior and real MySQL -> MySQL/PostgreSQL/Oracle JDBC acceptance.
- REALTIME manual execution with MySQL CDC, persisted connector state, restart continuation, metrics and Stop.
- REALTIME Task editor plus Instance list/detail with active polling.
- Manual E2E playbooks for user-visible product verification, alongside automated Acceptance tests.

Current REALTIME product flow:

```text
REALTIME Task
  ↓ Start / Save & Start
Instance(PENDING/RUNNING)
  ↓
MySQL initial snapshot + Binlog
  ↓
JDBC CHANGELOG Sink
  ↓
Instance Tab + Detail
  ├── persisted read/write event counters
  ├── active polling
  └── Stop
```

Instance rows persist `syncType`, so REALTIME pagination is filtered in the backend and remains valid even after the originating Task is deleted. The UI labels `readRows/writeRows` as change events because UPDATE currently emits UPDATE_BEFORE + UPDATE_AFTER.

Checkpoint time is deliberately not displayed because the current Instance contract does not persist a trustworthy last-checkpoint timestamp.

## Realtime Phase 6 — Cross-Database Acceptance

The realtime milestone is accepted against real target databases rather than only a MySQL sink:

```text
MySQL CDC Source
├── MySQL Sink
├── PostgreSQL Sink
└── Oracle Sink
```

Every target path proves:
- initial snapshot.
- INSERT / UPDATE / DELETE changelog application.
- checkpoint completion and persisted Debezium offset.
- cancel.
- state-directory reuse.
- restart continuation.
- exactly one read/write event after inserting one new source row post-checkpoint, proving the second run resumed from the saved offset instead of starting a fresh snapshot.

The acceptance remains at-least-once; passing these tests does not create an exactly-once claim.

It still does not provide:

- automatic resurrection/restart of LOST Instances.
- distributed state ownership / fencing.
- exactly-once transaction coordination.
- persisted last-checkpoint timestamp / checkpoint history UI.
- scheduler or retry policy.
- distributed execution.
- Transform, DDL propagation, schema evolution or multi-table REALTIME tasks.

Those belong to later PRs.
