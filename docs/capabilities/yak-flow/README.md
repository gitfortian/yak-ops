# YakFlow Capability

Status: Phase 2 — Local Runtime

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

## Explicit Non-Goals

The current phase does not implement:

- JDBC Source/Sink.
- Debezium or MySQL CDC.
- Transform.
- distributed execution.
- Flink integration.
- exactly-once coordination.
- job persistence or HTTP APIs.

Those capabilities must build on this contract rather than changing batch and CDC into separate execution protocols.

## Dependency Boundary

```text
yak-flow-runtime ------+
                        |
future JDBC connector --+--> yak-flow-api
                        |
future CDC connector ---+
```

`yak-flow-api` itself stays implementation independent and JDK-only.
