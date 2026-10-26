# MySQL Incremental Snapshot: Consistency and Lock Acceptance

This document records the acceptance baseline for unified Chunk discovery and lock-free
typed Snapshot reads. It describes tested behavior, not a claim that Source-only tests
establish end-to-end exactly-once delivery.

## Implementation under test

- `MySqlSnapshotBackfillReader`: Low / range scan / High / ordered Binlog Backfill.
- `MySqlSnapshotSplitReader`: half-open Chunk scans; typed ranges use a pinned InnoDB read view.
- `MySqlChunkBackfill`: keyed or duplicate-preserving keyless reconciliation.
- `MySqlHybridSplitState` and `MySqlBinlogSplitFilter`: durable prepared Chunk and per-Chunk High.
- `MySqlHybridIT`: completed Chunk / checkpoint / Binlog handoff, including partial external writes.

## Real MySQL invariants

| Case | Automated evidence | Required behavior |
| --- | --- | --- |
| BIGINT physical primary key | `MySqlBackfillIT.backfillsRepeatedUpdatesDeletesCrossChunkMovesAndIgnoresOtherTables` | Repeated updates, deletions, inserts and cross-range key moves reconcile only in their matching half-open range |
| Typed range with physical primary key | `MySqlBackfillIT.typedKeyedChunkReconcilesTransactionCommittedBetweenLowAndReadView` | A transaction committed between Low and read-view creation is reconciled even when the snapshot can also see its changes |
| Restricted CDC account | `MySqlBackfillIT.limitedReplicationPrivilegesPermitTypedSnapshotReadViewWithoutGlobalReadLock` | Typed JDBC snapshot read-view creation succeeds without global FTWRL privilege |
| Wide range with bounded payload | `MySqlBackfillIT.wideSnapshotChunkFailsBeforePublishingPartialOutputWhenByteBudgetIsExceeded` | Exceeding the retained payload budget fails before a prepared result is published; 100,000 rows is **not** the only guard |
| No physical primary key, duplicate images | `MySqlKeylessBackfillIT.reconcilesDuplicateRowsInTheLowHighWindowAndFreezesOneDurableChunk` | Preserve the multiplicity of identical rows while processing delete/update/insert |
| Commit after Low, before typed scan | `MySqlKeylessBackfillIT.consistentReadViewPreventsDoubleCountingWritesAfterLowBeforeTypedScan` | Pinned view + Binlog Backfill must neither duplicate nor lose identical rows |
| Keyless Chunk Key changes | `MySqlKeylessBackfillIT.failsClosedIfTheChunkKeyChangesDuringSnapshot` | Reject unsupported key movement explicitly before publishing a chunk |
| Very large identical key group | `MySqlKeylessBackfillIT.checkpointsAnOversizedRepeatedRowGroupFromRealMysqlWithoutDroppingOccurrences` | Splitting must not discard equal-key occurrences; compact checkpoint must preserve multiplicity |
| Per-Chunk High filtering | `MySqlBinlogSplitFilterTest` | Each row image uses its **own** Chunk High, including mixed before/after ranges |
| Frozen output and restore | `MySqlHybridIT.partialExternalWritesRecoverFrozenChunkAndApplyLaterSourceDeletion` | Resume the durable normalized output rather than rescanning newer source data after partial writes |

These are automated assertions against MySQL Testcontainers. They do **not** measure
JVM heap exactly: retained bytes are estimated by the implementation. They do not
prove a true process-kill recovery boundary, general DDL reconciliation or end-to-end
exactly-once delivery to every Sink.

## Refactor guardrails

1. Typed Snapshot must capture Low before establishing its InnoDB read view and reconcile
   the full Low-to-High Binlog interval. Do not add a global read lock as a substitute for
   proving the overlap cases; keyed and keyless paths still require separate acceptance.
2. Do not remove the prepared-output checkpoint merely because Low/High Backfill
   passes: first prove correctness after XA prepare, checkpoint publication, commit,
   and process crashes with a real target database.
3. Do not resolve memory overflows by silently truncating a Chunk, dropping duplicate
   images, skipping Backfill or raising unreviewed user-facing tuning parameters.
4. Keep per-Chunk High, complete UPDATE-pair handling and fail-closed unsupported
   boundaries as acceptance invariants.

## Running

The existing `cdc` integration suite registers `MySqlBackfillIT`,
`MySqlKeylessBackfillIT` and `MySqlHybridIT` in
`scripts/tests/backend_tests.py`; no separate test selector is introduced.

```sh
python3 scripts/tests/backend_tests.py run cdc
```

Requires a local Docker daemon. The CI-selected `cdc` suite must pass with positive
Failsafe evidence and no skipped integration tests.
