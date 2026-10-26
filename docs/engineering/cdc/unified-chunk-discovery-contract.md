# MySQL CDC Unified Snapshot Discovery Contract

## Contract

The active Hybrid Source assigns exactly one kind of snapshot range: `ChunkRange`.
A Chunk Key is a non-null, database-ordered scalar used to partition a table into
half-open ranges. It is **not** the row identity. Row reconciliation uses the
physical primary key when one exists and full-row multiplicity for keyless
tables. Neither path invents synthetic unique row identities.

The Enumerator persists `nextTableIndex`, `nextChunkIndex` and
`nextChunkRange`; it no longer persists an independent BIGINT lower bound.
`MySqlChunkSplitter.planNext` and `MySqlHybridSplitAssigner.appendPlan`
are the only production entry points for progressive discovery. The next
boundary must equal the prior range's exclusive upper boundary. A row key
inside an unfinished Chunk is never a valid recovery cursor; unfinished
ranges are rescanned in full and normalized before downstream output.

## Recovery boundary

This change increments the Hybrid **Enumerator** checkpoint serializer to
version 4. Versions 1, 2 and 3 are rejected explicitly. Restoring one of
those versions requires operator-approved task reinitialization and target
reconciliation; it must not silently guess a typed range or advance a Binlog
position. No migration of persisted checkpoints is provided.

The Hybrid **Split** wire format and its prepared-output checkpoint gate
remain unchanged in this PR; those encode downstream recovery semantics and
must not be removed merely because the discovery cursor is now unified.
The BIGINT scan helper retained in `MySqlSnapshotSplitReader` remains a
separate, non-dispatched path pending the next Backfill consolidation. No
new compatibility fallback is introduced.

## Acceptance

- `MySqlChunkSplitterTest`: sparse BIGINT ranges use the typed planner and an
  interrupted scan resumes from the whole half-open range, not the last row.
- `MySqlTypedEnumeratorCheckpointTest`: DECIMAL boundary survives Checkpoint;
  old enumerator versions are rejected.
- `MySqlProgressiveDiscoveryTest`: completed ranges and their High watermarks
  survive a progressive-discovery restore.
- `MySqlTypedSnapshotAssignmentTest`: Enumerator asynchronously assigns
  non-unique DECIMAL Chunk Keys without repeating a completed range.
- `MySqlChunkIdentityTest`: two distinct primary keys in the same Chunk
  survive reconciliation; changing a Chunk Key moves only the matching
  physical row to the next range.

The existing CDC suite and PR Quality Check must pass with no weakened
integration assertions. No global-lock or Checkpoint output-state removal
occurs in this change.
