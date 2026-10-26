# MySQL Incremental Snapshot: Consistency and Lock Acceptance

This is the test baseline for the subsequent unified-Chunk and lock-free Snapshot refactors.
It describes **current observable behavior**, not a proposal to add connector options or
a claim that Source-only tests establish end-to-end exactly-once delivery.

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
| Typed range with physical primary key | `MySqlBackfillIT.typedKeyedChunkReconcilesOneCommittedTransactionAfterLowWithoutHoldingTheReadLock` | Transaction committed after the pinned Low/read-view boundary is Backfilled, and the global read lock is not held while executing the post-Low callback |
| Restricted CDC account | `MySqlBackfillIT.limitedReplicationPrivilegesPermitBigintScanButNotTypedGlobalReadLock` | Document the **current discrepancy**: BIGINT uses no FTWRL while Typed requests it even for a physical primary key. The Typed error must be explicit rather than silently falling back |
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

1. Do not delete the Typed FTWRL until lock-free keyed and keyless paths are proven
   separately under concurrent transactions. Keyless full-row identity is not a
   substitute for a stable source primary key.
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
