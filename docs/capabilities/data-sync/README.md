# Data Sync Capability

Status: Offline Phase 4 + Realtime Phase 2 Execution

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
- YakFlow runtime tuning JSON, including optional JDBC dynamic `splitSize` and bounded Source `sourceParallelism`.
- monotonically increasing definition version.
- user-facing name and remark.

Datasource credentials never belong to the task table.

## Realtime Phase 1 — Task Contract

Realtime Phase 1 intentionally stops at the product definition boundary:

```text
REALTIME Task
   ↓
MySQL Source table with primary key
   ↓
same-name compatible field mapping
   ↓
MySQL / PostgreSQL / Oracle Target
```

Realtime config currently contains:

- checkpoint interval.
- CDC queue capacity.
- CDC poll batch size.
- JDBC changelog write batch size.
- connection / statement timeout.

Execution-only values such as Debezium state directory, offsets, schema history and MySQL replication `serverId` are not Task fields.

A REALTIME task can be created, updated, queried and filtered by `syncType`. Realtime Phase 2 adds manual execution without creating a second Task/Instance model.

## Realtime Phase 2 — Execution

Manual realtime run path:

```text
saved REALTIME Task
        ↓
create Instance(PENDING)
        ↓
sanitized snapshot(syncType + realtimeConfig)
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

PR2 intentionally does not define durable realtime state ownership. The current bootstrap uses:
- `java.io.tmpdir/yak-ops/realtime-sync/{instanceId}` for Debezium state.
- an Instance-ID-derived temporary MySQL replication `serverId`.

Those choices are execution scaffolding only. A process restart still marks active Instances LOST and no REALTIME restore is attempted.

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
- target datasource/table.
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

No filter SQL, split key, pre/post SQL, resource group or Transform is introduced in this phase.

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

## Phase 1 Boundary

Phase 1 provides:

- Flyway tables.
- DAO Entity / Mapper / Repository.
- shared DTO / VO and persistence enums.
- `DataSyncService` task CRUD and task/instance query contract.
- task persistence implementation.

Phase 4 now provides persisted read/write metrics, save-and-run UI and real MySQL -> MySQL/PostgreSQL/Oracle acceptance executed by CI.

Realtime Phase 2 now provides the reusable Task contract, save/run validation, MySQL CDC execution planner, JDBC CHANGELOG sink wiring, process-local runtime lifecycle and cancel/metrics integration.

It still does not provide:

- durable realtime checkpoint/state-directory product ownership.
- stable MySQL replication serverId allocation.
- realtime process restart recovery.
- realtime frontend.
- scheduler.
- retry policy.
- distributed execution.
- process restart recovery.

Those belong to later PRs.
