# YakFlow Capability

Status: Phase 4 — MySQL CDC Connector

## Goal

YakFlow is Yak Ops' batch/stream unified data synchronization capability.

The first product milestone is intentionally narrow:

```text
Batch:
MySQL -> MySQL / PostgreSQL / Oracle

CDC:
MySQL CDC -> MySQL / PostgreSQL / Oracle
```

No Transform is part of the first milestone.

## Core Model

YakFlow uses one data plane contract for batch and streaming:

```text
Source
  -> YakRow
  -> Sink
```

A Source declares its lifecycle through `Boundedness`:

```text
BOUNDED
CONTINUOUS_UNBOUNDED
```

CDC does not introduce a second row protocol. Change semantics are carried by `RowKind`:

```text
INSERT
UPDATE_BEFORE
UPDATE_AFTER
DELETE
```

This means a bounded JDBC Source and an unbounded MySQL CDC Source can feed the same Sink contract.

## Phase 1 Scope

This phase establishes only `yak-flow-api`:

- `YakRow`, `RowKind` and the common table schema model.
- `Source`, `SourceSplit`, `SourceSplitEnumerator` and `SourceReader`.
- `Sink` and `SinkWriter`.
- `Boundedness`.
- opaque `CheckpointState`.

## Phase 2 — Local Runtime

Phase 2 adds a minimal single-node execution path:

```text
Source Task
    |
    v
bounded Row Channel
    |
    v
Sink Task
```

The runtime supports:

- bounded Source execution that naturally reaches `SUCCEEDED`.
- continuous unbounded Source execution that stays running until cancel/failure.
- explicit cancellation.
- runtime status and failure observation.
- checkpoint barrier: capture Source state, enqueue a barrier after already-produced rows, flush Sink when the barrier is consumed, then complete the checkpoint.
- active-execution in-memory latest checkpoint.

The barrier establishes ordering only. Phase 2 does not claim exactly-once delivery and does not persist checkpoints across process restart.

## Phase 3 — JDBC Batch Connector

Phase 3 adds the first real database path:

```text
MySQL bounded table read
        ↓
      YakRow
        ↓
   Local Runtime
        ↓
JDBC batch INSERT
        ↓
MySQL / PostgreSQL / Oracle
```

The connector:

- consumes the existing normalized `DataSourceConnection` contract instead of defining duplicate host/port/user/password configuration.
- reuses Datasource JDBC runtime behavior for Driver loading, MySQL Driver isolation and SSH tunneling.
- maps Datasource Catalog columns to `YakTableSchema`.
- reads a table as a bounded Source with forward-only JDBC cursor batches.
- writes `INSERT` rows with JDBC batch commit.
- provides MySQL, PostgreSQL and Oracle identifier/table dialects.
- requires the target table to exist.

The current Local Runtime is still single Source Task / single Sink Task, so the first JDBC Source uses one bounded table split. Parallel table chunking belongs to a later runtime/connector phase where it produces real execution concurrency.

## Phase 4 — MySQL CDC Connector

Phase 4 adds the first continuous pipeline:

```text
MySQL
  ↓ snapshot + binlog
Debezium Engine
  ↓
YakRow + RowKind
  ↓
Local Runtime checkpoint barrier
  ↓
JdbcSink CHANGELOG mode
  ↓
MySQL / PostgreSQL / Oracle
```

Key rules:

- Debezium is private to `yak-flow-connector-cdc-mysql`; no Debezium or Kafka Connect type leaks into `yak-flow-api` or `yak-flow-runtime`.
- the connector uses Debezium `3.6.3.Final`, the current stable release line selected for this phase.
- `snapshot.mode=initial` supplies the initial table contents and then transitions to binlog streaming.
- Debezium offsets and schema history are stored in connector-owned files under the configured state directory.
- Debezium records are acknowledged only after the YakFlow Sink checkpoint barrier has flushed downstream data.
- Local Runtime automatically triggers checkpoints for continuous sources; the default interval is 10 seconds and callers can override it.
- CDC requires a primary key in Phase 4 so downstream INSERT/UPDATE/DELETE can be applied deterministically.
- JDBC Sink CHANGELOG mode treats INSERT and UPDATE_AFTER as idempotent replace-by-primary-key operations, and UPDATE_BEFORE / DELETE as primary-key deletes.

Recovery semantics are at-least-once. If the process stops after the Sink commit but before Debezium persists the acknowledged offset, records can be replayed; CHANGELOG mode is designed to tolerate that replay for primary-key tables.

## Explicit Non-Goals

The current phase does not implement:
- Transform.
- distributed execution.
- Flink integration.
- exactly-once coordination.
- job persistence or HTTP APIs.

Those capabilities must build on this contract rather than changing batch and CDC into separate execution protocols.

## Dependency Boundary

```text
yak-flow-runtime --------+
                          |
jdbc batch connector -----+--> yak-flow-api
                          |
mysql cdc connector -------+
```

`yak-flow-api` itself stays implementation independent and JDK-only.
