# YakFlow Rules

Scope:
- `yak-flow/**`

Status:
- Active / Staged

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`
- `/docs/capabilities/yak-flow/README.md`

## Product Boundary

YakFlow is a batch/stream unified data synchronization capability.

Current first-milestone product target:

```text
Batch:
MySQL -> MySQL / PostgreSQL / Oracle

CDC:
MySQL CDC -> MySQL / PostgreSQL / Oracle
```

Transform is explicitly outside the first milestone.

## API Contract

Batch and CDC use one Source/Sink protocol.

Must:
- Represent bounded and continuous execution with `Boundedness`.
- Represent row change semantics with `RowKind`.
- Keep `YakRow` usable by both JDBC batch reads and CDC events.
- Keep checkpoint state opaque to the runtime-facing API.
- Keep `yak-flow-api` independent from Spring, JDBC, Debezium and Yak Ops business modules.
- Keep connector-specific state and protocol details inside connector modules when those modules are introduced.

Must Not:
- Add separate `BatchSource` and `CdcSource` contract families.
- Add a `JobMode` enum to distinguish batch from CDC.
- Put Debezium types in `yak-flow-api`.
- Put JDBC types in `yak-flow-api`.
- Add Transform, DAG or SQL-expression contracts before the first synchronization milestone requires them.
- Add distributed scheduler, worker or resource-manager contracts in Phase 1.
- Claim exactly-once semantics without a concrete Source + Runtime + Sink implementation that proves them.

## Row Contract

`YakRow` is the common record carrier.

`RowKind` values:

```text
INSERT
UPDATE_BEFORE
UPDATE_AFTER
DELETE
```

Batch sources normally emit `INSERT`. CDC sources may emit any supported change kind.

The core type system describes portable logical values only. Database-specific native types and conversion rules belong to connectors.

## Local Runtime

The current runtime is deliberately single-node and local.

Must:
- Keep one bounded in-memory channel between Source work and Sink work.
- Allow bounded Source executions to run 1-16 parallel Readers while keeping split assignment serialized through one Enumerator.
- Keep one Sink Writer in Local Runtime; parallel Source Readers converge into the same bounded Row Channel before serial Sink writes.
- Allow bounded jobs to finish naturally.
- Keep continuous unbounded jobs alive until cancel or failure.
- Use channel ordering for checkpoint barriers: Source state first, barrier second, Sink flush before checkpoint completion.
- Keep checkpoint coordination on the single-Reader execution path; bounded executions with source parallelism greater than 1 reject checkpoint requests until multi-Reader checkpoint state aggregation is designed.
- Interrupt blocked local workers on cancel/failure so execution cannot remain stuck on channel operations.
- Treat checkpoint completion as an ordering/durability observation only; it is not an exactly-once contract.

Must Not:
- Add a distributed scheduler, Worker registry, ResourceManager or remote RPC layer.
- Add persistent Job/Attempt tables in this phase.
- Persist opaque `CheckpointState` with Java serialization merely to obtain a file checkpoint.
- Introduce connector-specific logic into the runtime.
- Let an unbounded Source report natural job success only because it is temporarily idle.

Continuous `SourceReader.poll()` implementations must return periodically rather than block forever so cancel and checkpoint requests can be observed.

Local Runtime metrics:
- `readRows` counts rows after a Source batch has successfully entered the Runtime channel.
- `writeRows` counts rows after `SinkWriter.write` returns successfully.
- Metrics are monotonic in one execution and are observation data, not a transaction-commit proof.
- A successful bounded execution must finish with final metrics persisted by the product layer.

## Package Organization

YakFlow package structure follows execution responsibility rather than file count.

Must:
- Keep connector package depth shallow; the connector namespace plus one responsibility subpackage is the default.
- Create a responsibility subpackage only when a real group exists, normally at least two closely related classes.
- Keep one capability family together: Source / Split / Enumerator belong together unless an external runtime boundary gives a clearer ownership split.
- Isolate third-party runtime details in a dedicated package when they have their own types and lifecycle, for example `debezium`.
- Mirror production responsibility packages in unit tests.
- Put real external-system end-to-end tests under an `integration` test package.
- Prefer names that express ownership such as `source`, `sink`, `dialect`, `debezium`; avoid generic dumping grounds.

Must Not:
- Put every class in the connector root package once multiple responsibilities exist.
- Create one package per class.
- Create empty future packages before code exists.
- Use generic `util`, `helper`, `manager` or `common` packages to avoid deciding ownership.
- Split a tightly related class family across packages only for visual symmetry.

Current MySQL CDC layout:

```text
mysql/
├── source/
│   ├── MySqlCdcSource
│   ├── MySqlCdcSourceConfig
│   ├── MySqlCdcSplit
│   ├── MySqlCdcSplitEnumerator
│   └── MySqlCdcEnumeratorState
└── debezium/
    ├── MySqlCdcSourceReader
    ├── MySqlDebeziumEngineConfig
    ├── DebeziumRecordConverter
    ├── DebeziumBatch
    └── MySqlCdcCheckpointState
```

The split is intentional: `source` owns YakFlow Source semantics, while `debezium` owns all Debezium / Kafka Connect implementation details.

## Integration Test Boundary

Connector integration tests may use Testcontainers when a protocol cannot be validated faithfully with an in-memory substitute.

Must:
- Use an isolated container owned by the test; never depend on a developer or shared external database.
- Configure the real source protocol required by the connector, such as MySQL row-based binlog for CDC.
- Use bounded polling timeouts; no unbounded sleeps or hanging waits.
- Cover the important lifecycle boundary, not only connection success.
- Keep unit tests for conversion and config logic even when an integration test exists.

Execution boundary:
- Real-database acceptance classes use the `*IT` suffix so default Surefire discovery does not start Docker during ordinary `Backend verify`.
- `.github/workflows/backend-acceptance.yml` owns real-database acceptance execution.
- Pull requests and pushes run JDBC / MySQL CDC acceptance only when their dependency paths change.
- Manual dispatch and the weekly full sweep run both acceptance suites as a dependency-filter safety net.
- Local JDBC acceptance: `bash mvnw -q -pl yak-flow/yak-flow-connector-jdbc -am -Dtest=OfflineSyncJdbcAcceptanceIT -Dsurefire.failIfNoSpecifiedTests=false test`.
- Local MySQL CDC acceptance: `bash mvnw -q -pl yak-flow/yak-flow-connector-cdc-mysql -am -Dtest=MySqlCdcIntegrationIT -Dsurefire.failIfNoSpecifiedTests=false test`.

The JDBC batch acceptance baseline uses real Testcontainers databases and covers:
- MySQL Source -> MySQL Sink.
- MySQL Source -> PostgreSQL Sink.
- MySQL Source -> Oracle Sink.
- final YakFlow read/write metrics matching transferred rows.

The MySQL CDC integration baseline covers:
- initial snapshot.
- binlog INSERT / UPDATE / DELETE.
- downstream checkpoint completion.
- persisted offset file creation.
- stop and restart with the same connector state directory.

## JDBC Batch Connector

The current bounded JDBC connector owns synchronization behavior, not datasource configuration ownership.

Must:
- Consume normalized `DataSourceConnection` and `DataSourceTablePath` from the Datasource plugin API.
- Reuse Datasource JDBC runtime behavior for Driver loading and SSH tunneling.
- Keep table/column identifiers quoted through a database dialect; never concatenate raw user SQL.
- Read only declared schema columns and preserve column order into `YakRow`.
- Use bounded cursor batches instead of loading an entire table into memory.
- Allow an explicit integer single-primary-key split contract with inclusive lower/upper bounds and a requested split count.
- Generate numeric range splits without gaps, overlaps or empty ranges; split planning is independent from Source Reader parallelism.
- When `splitSize` is configured, detect a single integer primary key, query `MIN / MAX / COUNT(*)`, and derive the requested range split count from the target rows per split.
- Fall back to one whole-table split when no eligible integer single primary key exists or the table row count does not exceed `splitSize`.
- Reject dynamic plans above 10,000 splits instead of allocating an unbounded split list; callers must increase `splitSize`.
- Treat each JDBC split as an independent read transaction; V1 does not claim one database-consistent snapshot across multiple splits.
- Commit Sink writes in explicit JDBC batches.
- Roll back uncommitted Sink data on write/flush failure.
- Own JDBC Catalog field compatibility used by Data Sync mapping preview and runtime execution.
- Treat `JdbcSchemaMapper` logical type support as the compatibility baseline; a JDBC type that cannot map to YakFlow is incompatible.
- Reject integer narrowing; allow integer widening and integer -> DECIMAL only when known target integer-digit capacity is sufficient.
- Preserve known String / Binary capacity and DECIMAL integer/fraction capacity; allow FLOAT -> DOUBLE widening.
- Keep current acceptance coverage on MySQL Source and MySQL/PostgreSQL/Oracle Sink.

Must Not:
- Duplicate datasource host/port/username/password configuration models inside YakFlow.
- Add custom SQL, Transform or arbitrary SQL execution in Phase 3.
- Auto-create target tables in Phase 3.
- Claim snapshot restart consistency from the current row-count checkpoint state.
- Add skew detection or sampling policy in the dynamic range split phase.
- Duplicate JDBC type-family or conversion compatibility rules in Data Sync Business.

Target tables must exist before execution. Auto-create DDL and schema evolution require their own explicit design.

## MySQL CDC Connector

MySQL CDC uses Debezium Engine as a connector-private protocol implementation.

Must:
- Pin Debezium to a stable Final release in the Yak Ops BOM.
- Keep Debezium Engine, SourceRecord, RecordCommitter and schema-history implementation types inside `yak-flow-connector-cdc-mysql`.
- Use the same `YakRow + RowKind` contract as bounded sources.
- Use `snapshot.mode=initial` for the Phase 4 full-snapshot-then-binlog path.
- Persist Debezium offsets and internal schema history under a caller-owned state directory.
- Acknowledge Debezium records only from `notifyCheckpointComplete`, after the Sink barrier flush succeeds.
- Require a primary key for Phase 4 CDC.
- Keep recovery semantics explicitly at-least-once.
- Allow SSH-backed Datasource connections through the existing Datasource JDBC endpoint runtime rather than implementing SSH inside YakFlow.

Must Not:
- Import Debezium or Kafka Connect types into `yak-flow-api` or `yak-flow-runtime`.
- Mark Debezium records processed when they merely enter the YakFlow row channel.
- Claim exactly-once after a process crash.
- Implement Transform, schema evolution or DDL propagation in Phase 4.
- Add Kafka as a mandatory runtime dependency or external service.

## Checkpoint Boundary

`CheckpointState` is an opaque connector/runtime contract.

The API keeps connector state opaque. The Local Runtime coordinates in-process checkpoint barriers and keeps only the latest completed checkpoint for the active execution.

The current phase still does not define durable checkpoint serialization, restart recovery or a transaction commit protocol. Those concerns must not leak Debezium offset structures into the public API.

## Dependency Direction

```text
yak-flow-runtime --------+
                         |
jdbc batch connector -----+--> yak-flow-api
                         |
mysql cdc connector -------+
```

`yak-flow-api` depends only on the JDK.
