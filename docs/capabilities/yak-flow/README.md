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
Bounded Source:
Enumerator
    ↓
1..16 Source Readers
    ↓
bounded Row Channel
    ↓
1 Sink Writer

Continuous Source:
1 Source Reader
    ↓
bounded Row Channel
    ↓
1 Sink Writer
```

The runtime supports:

- bounded Source execution that naturally reaches `SUCCEEDED`.
- configurable bounded Source Reader parallelism from 1 to 16; split assignment remains serialized through one Enumerator and Sink writing remains single-threaded.
- continuous unbounded Source execution that stays running until cancel/failure.
- explicit cancellation.
- runtime status and failure observation.
- checkpoint barrier: capture Source state, enqueue a barrier after already-produced rows, flush Sink when the barrier is consumed, then complete the checkpoint.
- active-execution in-memory latest checkpoint.

The barrier establishes ordering only. Phase 2 does not claim exactly-once delivery and does not persist checkpoints across process restart.

Checkpoint coordination currently remains on the single-Reader path. A bounded execution with Source parallelism greater than 1 rejects explicit checkpoint requests rather than pretending multiple Reader states form one consistent checkpoint. Continuous CDC keeps the existing single-Reader checkpoint path.

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
- supports an optional explicit integer single-primary-key range split contract: split key + inclusive lower/upper bounds + requested split count.
- writes `INSERT` rows with JDBC batch commit.
- provides MySQL, PostgreSQL and Oracle identifier/table dialects.
- requires the target table to exist.

The Local Runtime can now consume bounded JDBC splits with multiple local Source Readers. The Enumerator remains single-threaded for deterministic split assignment, each Reader owns an independent JDBC connection, and all Reader batches converge into one bounded Row Channel consumed by one Sink Writer.

Example:

```text
split key: id
range:     1 .. 10
count:     3

Split 0: [1, 4]
Split 1: [5, 8]
Split 2: [9, 10]
```

Each range split opens its own JDBC read transaction. This V1 guarantees non-overlapping range predicates, not one database-consistent snapshot across all splits.

When `splitSize` is configured, the Enumerator detects an eligible single integer primary key and queries `MIN / MAX / COUNT(*)`. It uses `ceil(rowCount / splitSize)` as the requested split count, then reuses the same non-overlapping range planner. `splitSize` is a target row count only: actual rows per split depend on key distribution.

Tables without an eligible single integer primary key, or tables whose row count does not exceed `splitSize`, stay as one whole-table split. Dynamic planning refuses more than 10,000 splits and asks the caller to increase `splitSize`.

Skew detection, distribution-factor analysis and sampling remain later phases.

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

## MySQL CDC Verification

MySQL CDC now has both unit and real protocol verification.

Package ownership is intentionally shallow:

```text
mysql/
├── source/     # YakFlow Source lifecycle
└── debezium/   # Debezium Engine / Connect implementation
```

The integration test starts a real MySQL 8.4 container with row-based binlog enabled and verifies:

```text
initial snapshot
      ↓
target rows
      ↓
source INSERT / UPDATE / DELETE
      ↓
target converges
      ↓
YakFlow checkpoint
      ↓
Debezium offset file
      ↓
stop / restart with same state directory
      ↓
new binlog change continues syncing
```

This test is part of Maven verification and does not use a developer-owned database.

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
