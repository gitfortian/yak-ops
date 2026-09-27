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
- Allow bounded jobs to finish naturally.
- Keep continuous unbounded jobs alive until cancel or failure.
- Use channel ordering for checkpoint barriers: Source state first, barrier second, Sink flush before checkpoint completion.
- Interrupt blocked local workers on cancel/failure so execution cannot remain stuck on channel operations.
- Treat checkpoint completion as an ordering/durability observation only; it is not an exactly-once contract.

Must Not:
- Add a distributed scheduler, Worker registry, ResourceManager or remote RPC layer.
- Add persistent Job/Attempt tables in this phase.
- Persist opaque `CheckpointState` with Java serialization merely to obtain a file checkpoint.
- Introduce connector-specific logic into the runtime.
- Let an unbounded Source report natural job success only because it is temporarily idle.

Continuous `SourceReader.poll()` implementations must return periodically rather than block forever so cancel and checkpoint requests can be observed.

## Checkpoint Boundary

`CheckpointState` is an opaque connector/runtime contract.

The API keeps connector state opaque. The Local Runtime coordinates in-process checkpoint barriers and keeps only the latest completed checkpoint for the active execution.

The current phase still does not define durable checkpoint serialization, restart recovery or a transaction commit protocol. Those concerns must not leak Debezium offset structures into the public API.

## Dependency Direction

```text
yak-flow-runtime --+
                   |
jdbc connector ----+--> yak-flow-api
                   |
cdc connector -----+
```

`yak-flow-api` depends only on the JDK.
