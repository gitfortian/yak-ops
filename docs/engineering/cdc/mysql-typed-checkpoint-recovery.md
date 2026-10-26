# MySQL CDC Typed Snapshot Checkpoint Compatibility

> **Historical engineering note, not an accepted YOIP.** Preserved from [`docs/yoips/YOIP-8-mysql-typed-checkpoint-recovery.md`](../../yoips/YOIP-8-mysql-typed-checkpoint-recovery.md) as of 2026-10-25. This describes the implementation at the time; current code and tests are authoritative.
> Provenance: [original change](https://github.com/weifuwan/yak-ops/commit/2601ff597ecd8adc9131fce4052f09b185afb447).

Historical implementation scope: PR 6 (serialization and recovery contract). **Typed snapshot execution is not enabled.**

## State migration

| State | Previously written | New writer | Supported reads |
| --- | --- | --- | --- |
| `MySqlHybridSplitSerializer` | v2 (plain output), v3 (compressed output) | v4 | v2 / v3 / v4 |
| `MySqlHybridEnumeratorStateSerializer` | v2 | v3 | v2 / v3 |
| Nested split inside Enumerator | v3 in Enumerator v2 | v4 in Enumerator v3 | Chosen by **outer** Enumerator version |

The serialized legacy BIGINT boundaries, cursor, Binlog offset, phase, High watermarks,
and frozen snapshot payload retain their meaning. v4 adds an explicitly tagged,
type-checked `ChunkRange`. v3 Enumerator adds the independent typed discovery position
without replacing the historical `Long nextChunkLower` slot.

Historical versions must be decoded with their **actual** version numbers; interpreting
v4 bytes as v2/v3 is corruption and must fail. Unsupported versions, truncated frames,
unknown types and trailing bytes must fail the restore. Never silently discard offsets,
restart from a fresh snapshot or reinitialize a source task.

## Durability boundary

The normalized result is checkpointed **before** the first JDBC write. After an output
checkpoint completes, the Reader advances a persisted `outputPosition` while retaining
the exact frozen rows. Recovery from a partial write must resume the frozen output:
it must not query a newer source snapshot (which could leave previously written but now
deleted rows in a non-transactional JDBC target). The destination's idempotency and row
identity still belong to PR 7 acceptance.

For typed, potentially non-unique Chunk Keys, resuming a live JDBC scan with
`WHERE chunk_key > last_seen_key` can skip rows sharing the last key. Therefore typed
split checkpoints intentionally have **no last-emitted-key cursor**; a complete
normalized chunk and its output position are the resumable unit. No synthetic row key is
introduced.

## Safe feature boundary

- Persisting a typed range is **not** the same as making it executable.
- The active JDBC snapshot Reader and current Binlog handoff continue to accept only
  the historical BIGINT primary-key path. A typed split supplied to the old Reader is
  rejected explicitly, including when restored with prepared output.
- The typed Binlog filter from PR 5 has its own `forChunks` entrypoint, but the
  production Source does not route typed metadata there until Source/Reader/Sink
  acceptance is complete.
- MySQL Collation-specific bounded VARCHAR chunk movement and keyless JDBC UPDATE/DELETE
  remain unsupported, as do target-side changelog writes without a proven row identity.
- Existing release checkpoints and their definition fingerprints must remain valid.

## Acceptance

1. Real v2, v3 and v4 Split state round trips with frozen output and completion watermarks.
2. Enumerator v2 and v3 restore; nested split version matches enclosing state version.
3. Typed BIGINT, DECIMAL (including values beyond signed 64-bit), CHAR and VARCHAR
   boundary scalars retain exact type and value.
4. A checkpoint in the middle of typed discovery restores the same table, column,
   chunk index and next lower boundary.
5. A restored prepared chunk emits the same duplicate row images from its stored output
   position, never creates a new snapshot.
6. Legacy BIGINT CDC, Checkpoint and real-database recovery suites remain green.

See [YOIP-7](mysql-cdc-keyless-snapshot-baseline.md) for the full acceptance matrix.
