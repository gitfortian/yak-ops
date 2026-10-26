# Keyless Binlog Handoff and Consistent Snapshot Boundaries

> **Historical engineering note, not an accepted YOIP.** Preserved from [`docs/yoips/YOIP-14-keyless-binlog-handoff.md`](../../yoips/YOIP-14-keyless-binlog-handoff.md) as of 2026-10-25. The code and tests determine currently supported behavior.
> Provenance: [implementation PR #1730](https://github.com/weifuwan/yak-ops/pull/1730).

Historical implementation scope: PR 5 (internal Source components only). Keyless public execution and RETRACT Sink remain disabled.

## Atomic Low watermark / consistent JDBC read view

Keyless Backfill now acquires a short-lived MySQL global read lock
(`FLUSH TABLES WITH READ LOCK`), reads a Binlog Low watermark from **that
same connection**, opens a second REPEATABLE READ transaction using
`START TRANSACTION WITH CONSISTENT SNAPSHOT`, then releases the global
read lock **before** issuing the JDBC SELECT on that pinned read view. All statements and connections are closed
even if the read view or query cannot be opened.

This prevents commits between the Low position and the JDBC read view. Rows
committed after Low cannot leak into the raw snapshot and be applied twice
by the Low-to-High Binlog replay. The lock is released immediately after
establishing the read view; it is not held during JDBC query execution, row transfer
or waiting for High. A user requires the MySQL privileges necessary to
acquire a global read lock (for example RELOAD/FLUSH_TABLES, depending on
server version) and to read the Binlog position; failure is explicit.
Cancellation interrupts the active JDBC statements. The old physical-PK
BIGINT path does not acquire a new global lock.

This contract assumes the source is backed by transactionally consistent
InnoDB tables. Nontransactional tables and MySQL servers where no atomic
read view is possible must remain unsupported in the subsequent admission
and live metadata checks.

## Completed chunk High watermarks

`FinishedSnapshotSplitInfo` already stores both legacy BIGINT bounds and
typed `ChunkRange` with a per-chunk High offset. Handoff now builds
`MySqlBinlogSplitFilter` from this **actual persisted metadata**, and
also accepts mixed keyed/keyless tables in one completed checkpoint.

- The streaming Binlog starts at the **minimum** completed chunk High.
- Before/after images are independently checked against their chunk's High.
- Offset progress is retained even when all rows in an event are filtered.
- A typed keyless UPDATE that changes the Chunk Key fails before filtering
  either image; existing keyed UPDATE move behavior remains unchanged.
- Keyless DDL changes fail closed until coordinated Schema Change support
  is designed and verified. Typed bounded VARCHAR ranges still require
  database-native MySQL collation comparisons.

Reader-side typed split admission remains blocked by
`MySqlHybridSplitReader.addSplits`; so does `MySqlHybridCdcSource`
construction. These guards are intentional until event identity and
transactional target-side RETRACT are present.

## Tests

- Real MySQL with Debezium: commits are performed *after* the Low/view
  boundary but *before* the typed row fetch. INSERT, DELETE of exactly one
  duplicate and UPDATE must reconcile to the correct row multiplicity.
- Existing real MySQL keyless Backfill and frozen Checkpoint recovery
  tests continue to run.
- Checkpoint-backed typed handoff: per-chunk High fences, the minimum-High
  streaming resume position, unchanged offset of fully filtered events,
  mixed legacy and typed table metadata.
- Keyless Chunk Key update and DDL are rejected without weakening the
  existing keyed split-filter acceptance tests.

## Remaining release gates

Successful internal read-view and handoff tests do **not** enable live
keyless synchronization. PR 6 must persist stable event identities through
Source/Runtime; PR 7 implements exact-one-row JDBC RETRACT; PR 8 proves
transactional replay after a crash between DML commit and Source Checkpoint.
Product admission, permissions, full table-engine metadata and target
physical collation constraints remain separate fail-closed release gates.
