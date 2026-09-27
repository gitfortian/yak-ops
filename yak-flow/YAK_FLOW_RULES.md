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

Phase 1 product target:

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

## Checkpoint Boundary

`CheckpointState` is an opaque connector/runtime contract.

Phase 1 does not define:
- persistence format.
- checkpoint storage.
- checkpoint coordination.
- transaction commit protocol.

Those belong to Runtime work and must not leak Debezium offset structures into the public API.

## Dependency Direction

```text
runtime -----------+
                   |
jdbc connector ----+--> yak-flow-api
                   |
cdc connector -----+
```

`yak-flow-api` depends only on the JDK.
