# CDC Change Identity and Runtime Delivery

> **Historical engineering note, not an accepted YOIP.** Preserved from [`docs/yoips/YOIP-15-cdc-change-identity-delivery.md`](../../yoips/YOIP-15-cdc-change-identity-delivery.md) as of 2026-10-25. The code and tests determine currently supported behavior.
> Provenance: [implementation PR #1731](https://github.com/weifuwan/yak-ops/pull/1731).

Historical implementation scope: PR 6 of nine. This is a transport contract, **not** exactly-once JDBC RETRACT.

## Identity separate from business data

`TableRecord` retains its existing three-argument constructor, immutable business
RowData, RowKind and table identity. It now optionally carries an immutable
`ChangeEventId(sourceId, eventId, imageIndex, imageCount)`.

- `sourceId`: the stable, checkpoint-scoped Source definition fingerprint.
- `eventId`: an opaque event token, not a database row key or Chunk Key.
- `imageIndex` and `imageCount`: zero-based position and total row images in
  the complete mutation. An UPDATE has two images (BEFORE=0, AFTER=1) with
  the **same** sourceId and eventId. These are *one* logical event to the Sink.
- Snapshot: `snapshot:<splitId>:<frozen-output-ordinal>`. Every row receives a
  distinct ordinal even when all business column values are identical.
- Streaming MySQL: SHA-256 of canonical, sorted Debezium partition and
  source-offset fields, including Binlog file, position, event and row. Integral
  numeric values have a common canonical encoding, irrespective of Kafka
  Connect Integer/Long deserialization, and map insertion order cannot affect
  the result. It does not expose potentially sensitive source coordinates to
  log diagnostics.

Raw Binlog offsets must contain `file`, `pos`, `event`, `row` and a
source partition before a **replay-safe** identity can be derived. Without
these, the identity is **absent**, not invented from row values, timestamps,
an incrementing JVM counter or file position alone. Existing keyed CDC may
continue without a token; future keyless RETRACT must fail closed on a missing
token. PR 8 must verify actual deployed Debezium offset completeness and
event identity uniqueness over a real transaction containing multiple rows
and across Binlog rotation before enabling RETRACT.

## Delivery and recovery

Both `MySqlCdcRecordEmitter` and `MySqlHybridRecordEmitter` stamp the
transport envelope *in the source mailbox*, after any typed chunk filtering
has selected the emitted images. The existing `SourceOperator`,
`RecordWriterOutput`, `ResultPartition` and `SinkWriterOperator` forward
the TableRecord instance without copying it or building SQL from the metadata.
In particular, `RowData.getArity()` and JDBC column projections are unchanged.

Source offset still advances only after the entire mutation has been
successfully collected by the mailbox. A checkpoint/restart reconstructs
the same Binlog token from the original Debezium position. An unfinished
snapshot replays the original frozen output with the same split ID and row
ordinal. The current split serializers do not store the transient identity
and their **wire format and version are unchanged**.

## Explicit boundaries

- Two UPDATE images flow as consecutive `TableRecord` deliveries with a
  shared event ID. No claim of external JDBC atomic commit is made.
- `ChangeEventId` does not manufacture physical row identity or disambiguate
  pre-existing duplicate business rows.
- Legacy JDBC APPEND/UPSERT records can carry null identity without altering
  their SQL or existing recovery semantics.
- No downstream exactly-once guarantee follows from identifying a replay;
  PR 7 must implement exact-one-row RETRACT, and PR 8 must commit target DML
  and event receipts together in the same transaction.
- Public keyless CDC Source and JDBC RETRACT execution gates stay closed.

## Acceptance

- Identity is stable for reordered offset maps and Integer/Long numeric
  representations, but differs for separate row coordinates, source partitions,
  and rotated Binlog files.
- Absent fine-grained offsets produce **null**, not a fabricated token.
- UPDATE BEFORE/AFTER have the same event ID, distinct image ordinals and
  remain checkpoint-atomic on successful mailbox delivery.
- Failure while emitting the second image does not advance the Source offset;
  replay has the same event identities.
- Frozen snapshot duplicate rows get distinct split ordinals; restarting from
  a serialized partial-output checkpoint retains identical IDs.
- Existing keyed CDC / CDC pipeline / recovery E2E and Maven/Spotless checks
  remain required green.
