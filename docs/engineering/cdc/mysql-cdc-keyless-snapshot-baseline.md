# MySQL CDC Keyless Snapshot Reliability Baseline

> **Historical engineering note, not an accepted YOIP.** Preserved from [`docs/yoips/YOIP-7-mysql-cdc-keyless-snapshot-baseline.md`](../../yoips/YOIP-7-mysql-cdc-keyless-snapshot-baseline.md) as of 2026-10-25. This describes the implementation at the time; current code and tests are authoritative.
> Provenance: [original change](https://github.com/weifuwan/yak-ops/commit/fa8b0ed242d5b9b2189222cb24a167ae1d9b7da3).

Historical implementation scope: test baseline only (PR 1). No new CDC mode is enabled by this change.

## Problem

MySQL Binlog can contain changes from tables without a declared primary key. YakFlow's
hybrid snapshot cannot currently consume such tables: the Source Factory validates a
single non-null BIGINT primary key, chunk boundaries and split checkpoint cursors use
`Long`, and chunk backfill stores one row per `Long` key.

Flink CDC 3.6 separates snapshot chunk selection from the table primary key. Its MySQL
`ChunkUtils.getChunkKeyColumn` accepts a configured non-primary-key column. Its
`SnapshotSplitReader.pollWithBuffer` and `RecordUtils.upsertBinlog` retain multiple
identical records when Debezium supplies no record key. This does **not** remove the risk
of a mutable chunk key crossing snapshot ranges or prove the safety of a JDBC target.

This proposal records current behavior first. Later PRs must change their own
source, backfill, split, checkpoint and sink contracts explicitly.

## Existing tests to preserve

| Invariant | Existing test |
| --- | --- |
| Numeric keyset split ranges and exclusive cursor recovery | `source/assigner/MySqlChunkSplitterTest` |
| Backfill UPDATE/DELETE and cross-chunk primary-key moves | `source/reader/MySqlChunkBackfillTest` |
| Per-chunk High filtering and half-open range boundaries | `source/reader/MySqlBinlogSplitFilterTest` |
| Prepared output checkpoint before external writes, partial output recovery and scalar round trips | `source/split/MySqlSnapshotOutputTest` |
| Corrupt state and hybrid split transport compatibility | `source/split/MySqlHybridSplitSerializerTest` |
| Real MySQL partial target commit, later source deletion and restart | `source/MySqlHybridIT` |
| Real MySQL snapshot backfill mutations | `source/reader/MySqlBackfillIT` |

The new `MySqlCdcKeylessBaselineTest` makes four current boundaries explicit:
the Source rejects a table without a physical primary key; only a non-null
single-column BIGINT primary key passes hybrid admission; keyless backfill,
Binlog split filtering and JDBC changelog writing all fail closed; and the
durable snapshot transport preserves identical row images without deduplication.

These tests are **characterization tests**, not proof that keyless CDC works.
When a subsequent PR implements a capability, replace the relevant rejection
assertion with a positive behavior and recovery test in the same change. Do not
delete tests or mark them skipped to create an apparent pass.

## Required future acceptance matrix

| Case | Expected after the relevant implementation | Owner |
| --- | --- | --- |
| Non-null BIGINT physical primary key | Same results and checkpoint recovery as before | Every PR |
| Single VARCHAR or DECIMAL physical primary key | Typed chunk boundaries, correctly ordered under the database collation | PR 3 |
| Composite physical primary key | One explicitly selected safe chunk column; preserve all logical row keys | PR 3-5 |
| No physical key, stable non-null chunk column | Complete snapshot plus Binlog backfill | PR 2-5 |
| No physical key, duplicate identical rows | Preserve exact row multiplicity through snapshot, update and delete | PR 4 |
| No physical key, non-unique chunk column with many equal values | No overlapping/missing ranges; bounded resource behavior | PR 3-4 |
| UPDATE changes the chunk column while taking a snapshot | Explicitly documented consistency limits; fail closed if unsupported | PR 4-5 |
| UPDATE moves between chunks with distinct High watermarks | Correct before/after filtering and no double application | PR 5 |
| Empty table and initial inserts | Stable schema history, correctly anchored Binlog start | PR 5-6 |
| Restart during split discovery | Resume exactly from the persisted discovery state | PR 3, 6 |
| Restart after normalized output is checkpointed but before emission | Recover the same frozen output, not a new snapshot | PR 6 |
| Restart after only part of the frozen output reached JDBC | Final target equals the source after intervening DELETE/UPDATE | PR 6-7 |
| Different source and target row identities | Validate per-Sink write contract; do not mistake chunk key for Sink key | PR 7 |
| No stable row identity with JDBC UPDATE/DELETE | Reject unless a proven, explicit writer contract exists | PR 7 |
| `binlog_row_image` not FULL | Reject before reading any keyless changes requiring before images | PR 2 |
| Historical checkpoint before typed chunk-state migration | Restore safely or reject with an actionable migration path | PR 6 |

A duplicate identical row is two rows, not one: compare result bags/multisets,
not only `Map<key, row>` or row counts. Test nullable or unsupported chunk
keys, sparse boundaries, case-insensitive collations, unsigned values and
repeated key values with real MySQL whenever the engine starts supporting them.

## Non-negotiable reliability constraints

- **No silent append fallback.** Today JDBC changelog sources require UPSERT
  and matching declared source/target keys. Source capture is not proof that
  the target can process UPDATE or DELETE.
- **No source Exactly-once claim for a non-transactional JDBC target.** The
  existing implementation is at-least-once with keyed convergence, as in
  YOIP-6. The end-to-end contract needs re-acceptance when keys are absent.
- **No mutable row-key reference in checkpoint state.** Preserve scalar
  fidelity, duplicate multiplicity, byte budgets and deterministic recovery.
- **No unconditional snapshot replay after partial target writes.** Keep
  YakFlow's completed-checkpoint gate and frozen normalized output unless a
  different end-to-end recovery design is proved.
- **No checkpoint reinterpretation.** Version new split and enumerator formats,
  preserve supported old formats, and reject incompatible states explicitly.
- **Do not weaken tests or CI.** Run ordinary Maven unit tests without Docker;
  run the registered real-database CDC pipeline suites separately.

## Upstream implementation references

- [Flink CDC `ChunkUtils.getChunkKeyColumn`](https://github.com/apache/flink-cdc/blob/release-3.6.0/flink-cdc-connect/flink-cdc-source-connectors/flink-connector-mysql-cdc/src/main/java/org/apache/flink/cdc/connectors/mysql/source/utils/ChunkUtils.java)
- [Flink CDC `MySqlChunkSplitter`](https://github.com/apache/flink-cdc/blob/release-3.6.0/flink-cdc-connect/flink-cdc-source-connectors/flink-connector-mysql-cdc/src/main/java/org/apache/flink/cdc/connectors/mysql/source/assigners/MySqlChunkSplitter.java)
- [Flink CDC `SnapshotSplitReader`](https://github.com/apache/flink-cdc/blob/release-3.6.0/flink-cdc-connect/flink-cdc-source-connectors/flink-connector-mysql-cdc/src/main/java/org/apache/flink/cdc/connectors/mysql/debezium/reader/SnapshotSplitReader.java)
- [Flink CDC `RecordUtils.upsertBinlog`](https://github.com/apache/flink-cdc/blob/release-3.6.0/flink-cdc-connect/flink-cdc-source-connectors/flink-connector-mysql-cdc/src/main/java/org/apache/flink/cdc/connectors/mysql/source/utils/RecordUtils.java)
- [YOIP-6: MySQL Chunk Backfill and Durable Snapshot Output](../../yoips/YOIP-6-mysql-chunk-backfill-checkpoints.md)
