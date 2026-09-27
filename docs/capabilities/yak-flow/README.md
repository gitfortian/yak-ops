# YakFlow Capability

Status: Phase 1 — Core API

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

## Explicit Non-Goals

Phase 1 does not implement:

- Local Runtime or scheduling.
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
future runtime ---------+
                        |
future JDBC connector --+--> yak-flow-api
                        |
future CDC connector ---+
```

`yak-flow-api` itself stays implementation independent and JDK-only.
