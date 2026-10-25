# YOIP-19 — Keyless Repeated-Row Snapshot Checkpoint Compaction

Status: PR 1 of keyless large-group reliability. **Internal snapshot path only.**
The public keyless Source and JDBC RETRACT factories remain disabled.

## Problem

A nonunique Chunk Key cannot be subdivided in the middle of an equal-key
group by advancing the next Chunk Key. Previously, even 200,000 physically
identical rows failed before they could reach a durable checkpoint:

- `MySqlKeylessRecords` retained one complete row image per occurrence and
  applied a 4 MiB / 100,000-row in-memory limit.
- `MySqlSnapshotOutput` rejected more than 100,000 rows.
- The split serializer rejected an 8 MiB uncompressed snapshot even if
  duplicate rows would compress well.

Reducing the configured chunk size cannot break apart an equal-key group.

## Contract

- Keyless Backfill retains **one representative per distinct full-row image**
  and an integer occurrence count. The bounded retained memory and distinct-row
  limit still apply, without counting identical physical copies many times.
- Each DELETE retracts exactly one occurrence. UPDATE_BEFORE / UPDATE_AFTER
  remain indivisible. Chunk Key movement continues to fail closed.
- A `MySqlSnapshotRows` logical list exposes every physical occurrence in
  deterministic order without materializing a separate object for each copy.
  Snapshot emission offsets remain logical-row ordinals, including duplicates.
- The V5 split serializer persists the counted full-row representation using
  a separate negative row-count marker in the checksum-protected compact frame.
  Version 2, 3, and 4 split state remains readable. A V5 normal (non-counted)
  frame retains the existing scalar encoding.
- The old 8 MiB serialized-state bound remains enforced. Corrupt counts,
  unsupported scalars and truncated snapshots still fail before output.
- No spilled file or uncheckpointed path is allowed as a recovery dependency.

## Supported and unsupported large groups

200,000 **physically identical** rows sharing one Chunk Key can now be
reconciled, frozen, checkpointed, restored, and emitted with the same
occurrence count and stable row ordinals. A subsequent DELETE or UPDATE
changes the stored multiplicity exactly once.

This PR does **not** claim support for arbitrary groups with more than 4 MiB
of **distinct** full-row images, or more than 8 MiB of encoded checkpoint
state. A group containing many different payloads but the same Chunk Key
may still fail closed. Supporting such groups requires a durable paged
snapshot-state format integrated with the runtime checkpoint store, not
an untracked local temporary file or a higher in-memory limit.

The Reader and product-facing Source/Sink execution gates must remain closed
until full end-to-end keyless recovery acceptance.

## Acceptance

- More than 100,000 equal full-row images share a small fixed in-memory
  representation and serialize into a bounded versioned checkpoint.
- DELETE/UPDATE/INSERT retain the exact multiset counts.
- A restored partially emitted chunk preserves its row position and
  frozen result, rather than repeating a fresh JDBC scan.
- Distinct-row / retained-bytes limits still throw before exposing data.
- Previous scalar snapshots and V2/V3/V4 checkpoint wire formats remain
  readable by the V5 serializer.
