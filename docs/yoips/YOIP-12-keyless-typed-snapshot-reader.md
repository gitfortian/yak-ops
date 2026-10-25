# YOIP-12: Typed Snapshot JDBC Reader and Recovery Boundary

Status: PR 3 (typed raw snapshot scan). **No live keyless CDC activation.**

## Reading typed ranges

`MySqlSnapshotSplitReader.open` now accepts a `ChunkRange` without
requiring a physical primary key. It checks that the range table, column and
logical type match the frozen source schema, then issues a JDBC SELECT using the
database's native half-open predicates:

```sql
SELECT <frozen projected fields> FROM <table>
 WHERE chunk_key >= ? AND chunk_key < ?
 ORDER BY chunk_key
```

Open-ended boundaries omit their predicates. All identifiers are quoted by the
JDBC dialect; all bound values use JDBC typed parameters, including DECIMAL
values wider than signed BIGINT and collation-ordered strings. The query retains
the existing forward-only, read-only, fetch-size-bounded JDBC cursor. Equal
Chunk Key values are **not** deduplicated or split into separate chunks.

## The critical recovery rule

`fetchTyped()` returns raw `TableRecord` batches *without* a
`snapshotKey`, checkpoint progress cursor, synthetic PK, or consumer-facing
offset. This is intentionally different from the old `fetch()` path, which
still advances the single BIGINT-PK `lastEmittedKey` on mailbox emission.

A typed scan is not durable until the **entire** chunk has been combined with
Low/High Binlog changes, normalized and frozen into the existing
`MySqlSnapshotOutput` payload. Restarting halfway through raw scanning must
start again at the same half-open range, not from `key > lastSeen`, which
would skip equal Chunk Keys. The old `fetch()` refuses typed work and
`fetchTyped()` refuses legacy work.

`MySqlSnapshotBackfillReader`, `MySqlHybridSplitReader`, Source Factory and
the JDBC RETRACT writer still refuse typed/keyless execution. PR 4 will
consume `fetchTyped()` inside the existing Low/High watermarked Backfill
pipeline; neither this PR nor its tests emit raw typed rows downstream.

## Acceptance

- Duplicate physical source rows and duplicate Chunk Key values survive scan,
  including result sets larger than the 256-row fetch batch.
- Multiple typed half-open numeric/DECIMAL ranges cover every row once.
- A partially consumed typed chunk can be closed and fully rescanned;
  no `lastEmittedKey` resume operation is ever permitted.
- SQL range predicate ordering comes from the database (not Java String compare).
- Wrong field/type and already frozen output fail before executing SQL.
- Cancellation stops JDBC fetch; existing BIGINT snapshot tests remain green.

See YOIP-10 and YOIP-11 for admission and Enumerator checkpoint contracts.
