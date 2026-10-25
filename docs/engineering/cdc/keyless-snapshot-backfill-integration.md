# Keyless Snapshot Backfill Integration

> **Historical engineering note, not an accepted YOIP.** Preserved from [`docs/yoips/YOIP-13-keyless-snapshot-backfill-integration.md`](../../yoips/YOIP-13-keyless-snapshot-backfill-integration.md) as of 2026-10-25. The code and tests determine currently supported behavior.
> Provenance: [implementation PR #1729](https://github.com/weifuwan/yak-ops/pull/1729).

Historical implementation scope: PR 4 in the nine-PR keyless execution plan. **Internal component acceptance only.**

## Watermarked backfill ownership

`MySqlSnapshotBackfillReader` is the single owner of the keyless snapshot
reconciliation path. For typed `ChunkRange` snapshots on tables without a
physical primary key it now:

1. Starts Debezium and acquires a real MySQL Low Binlog watermark.
2. Scans **all** raw rows in the database-ordered half-open Chunk with
   `MySqlSnapshotSplitReader.fetchTyped()`. No row-level resume cursor exists.
3. Captures High after the full scan.
4. Consumes the Low-to-High Binlog interval including heartbeat progress.
   A DDL event changing the source during the interval is rejected.
5. Applies full Before/After images to the bounded
   `MySqlChunkBackfill(ChunkRange, ...)` backed by `MySqlKeylessRecords`,
   keeping the multiplicity of identical rows. UPDATEs whose Chunk Key
   changes during this interval **fail closed**.
6. Produces `MySqlSnapshotOutput(low, high, normalizedRows)`, verifies the
   versioned split serializer accepts it and returns one prepared split.

Legacy BIGINT PK snapshots continue through the unchanged keyed accumulator
and `fetch()` path. The new typed path only exists on the internal reader;
the product-facing Source Factory and `MySqlHybridSplitReader` remain blocked,
as does the reserved JDBC RETRACT Sink.

## Durability

The normalized frozen output is immutable and retains **all** duplicate
record images. A Split State preparation checkpoint must complete before
output can be emitted. After a partially emitted output is checkpointed,
recovery continues from the stored output position, rather than querying a
new, more recent JDBC snapshot. The typed Chunk Range and High watermark
survive checkpoint serialization.

The raw JDBC scanner remains intentionally **non-checkpointed**: failure
before the frozen result is persisted starts the whole Chunk again, never
uses an exclusive last-emitted Chunk Key that could skip duplicates.

## Concurrent-scan consistency is a separate acceptance gate

The Low watermark and ordinary JDBC scan do not currently start at one
atomically established MySQL read-view. Changes **during** the scan can
therefore make it ambiguous whether a duplicate image was already present
in the scan result before a Binlog INSERT or UPDATE_AFTER is replayed. The
component tests below deliberately apply changes after scanning. This PR
**does not certify** arbitrary concurrent mutation during the scan.

A later PR must establish and prove a consistent Low/scan boundary or a
conservative fail-closed method before enabling production keyless CDC.
The existing High watermark filter and typed Binlog handoff remain blocked
until PR 5. Target-side exact-row delete and transaction replay remain
blocked until PR 7 and PR 8.

## Acceptance

- Real MySQL with Debezium: two identical rows, DELETE only one, UPDATE
  a non-key field, INSERT a new row, unrelated tables and out-of-chunk
  writes ignored; frozen High is later than Low.
- Real MySQL: a quiet table retains two physically duplicate row images
  and equal Low/High watermarks.
- Real MySQL: changing Chunk Key in the reconciliation interval aborts
  without exposing partial output.
- Unit coverage: multiplicity after UPDATE/DELETE/INSERT, DECIMAL ranges
  beyond BIGINT, malformed incomplete UPDATE pairs, row/byte budgets.
- Versioned Checkpoint test: state is not emit-able until the preparation
  checkpoint completes; partial output resumes at the original frozen
  position with the same rows, range and High watermark.
- Existing keyed CDC/Recovery E2E and Spotless remain required green.

Do not remove the Source and JDBC RETRACT gates in this PR.
