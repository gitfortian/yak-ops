# YOIP-4: CDC Source Boundaries

## Decision

Keep the standalone MySQL Binlog Source and the Hybrid Snapshot + Binlog Source
separate. Both implement YakFlow's `Source` contract, but their checkpoint formats
and restart protocols differ. The production Source factory uses Hybrid. Keep
standalone Binlog for streaming-only use and its existing persisted checkpoints.

Shared responsibilities belong in `yak-flow-connector-base` (SourceReader,
fetcher and mailbox) or `yak-flow-connector-cdc-base` (Snapshot assignment,
split metadata and schema acknowledgment). Connector modules retain vendor-specific
readers, offsets, dialects and schema extraction.

| Responsibility | Shared | MySQL | Future PostgreSQL | Future Oracle |
| --- | --- | --- | --- | --- |
| Reader and fetcher | connector-base | Debezium MySQL engine | Debezium PostgreSQL | Debezium Oracle |
| Snapshot assignment | cdc-base | BIGINT chunks, bounded Backfill | PostgreSQL consistency/chunks | Oracle consistency/chunks |
| Stream offset | Comparable API | Binlog file/position/GTID | WAL LSN and transactions | SCN and LogMiner |
| Schema boundary | SchemaChangeHandler / SchemaCoordinator | MySQL tableChanges | PostgreSQL schema events | Oracle schema events |
| Durable state | YakFlow Runtime | Existing versioned MySQL formats | Vendor-owned serializers | Vendor-owned serializers |

## Compatibility guarantees

- Keep `MySqlBinlogSplitSerializer` v1 and `MySqlHybridSplitSerializer`
  v2/v3 checkpoint reads. Do not rewrite the split IDs, fingerprint or phase ordinals.
- Reader-owned cursors and Enumerator-owned assignment state remain independent.
- Durable Debezium schema history must agree with the saved source position.
  Never consult today's table schema to decode yesterday's Binlog event.
- The Reader may advance its offset only after its mailbox consumes the
  SchemaCoordinator acknowledgment. A pending ACK blocks checkpoints.
- A normalized Snapshot chunk retains prepared output until a completed
  downstream checkpoint. No claim of transactional Sink exactly-once is made.

## PostgreSQL and Oracle prerequisites

1. Use database-native offsets: PostgreSQL WAL LSN and Oracle SCN are not MySQL
   GTIDs. Validate ordering, retention failures, transaction boundaries and restart.
2. Test snapshot consistency under concurrent writes, primary-key changes and DDL
   before extracting more shared reader logic.
3. Restore Debezium offsets together with history/schema metadata. Reject
   recovery from unprovable historical schema states.
4. Kill processes before/after checkpoints and partial Sink writes; validate
   source-to-target contents after recovery, not only counters.
5. Add vendor-scoped database test suites with real fixtures. Extract additional
   shared code only after the second implementation demonstrates duplication.

## References

Flink CDC: `flink-cdc-base/source/assigner/SnapshotSplitAssigner`,
`flink-connector-postgres-cdc/.../offset/PostgresOffset`, and
`flink-connector-oracle-cdc`. Debezium: `PostgresOffsetContext`,
`OracleOffsetContext`. This PR implements neither database connector.
