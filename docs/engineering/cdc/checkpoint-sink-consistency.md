# Checkpoint and Sink Recovery Consistency

## Runtime contract

YakFlow uses a single-JVM aligned checkpoint. The Source reader snapshots delivered
offsets on the mailbox, emits a barrier on its ResultPartition, and the downstream
Sink snapshots after processing the barrier. One `CheckpointSnapshot` contains
Enumerator state, Reader splits, operator state, and optional schema state.

The checkpoint is **published before external commit notification**. This follows
the distinction in Flink's `CheckpointCoordinator`: a completed state snapshot is
the recovery authority, while a transactional Sink may still need its completion
callback. The published checkpoint must retain every prepared XID required to
finish or replay that callback.

Sources: [Flink CheckpointCoordinator](https://github.com/apache/flink/blob/master/flink-runtime/src/main/java/org/apache/flink/runtime/checkpoint/CheckpointCoordinator.java),
[Flink CDC MySqlHybridSplitAssigner](https://github.com/apache/flink-cdc/blob/master/flink-cdc-connect/flink-cdc-source-connectors/flink-connector-mysql-cdc/src/main/java/org/apache/flink/cdc/connectors/mysql/source/assigners/MySqlHybridSplitAssigner.java).

## Sink delivery contract

- Ordinary JDBC UPSERT commits on flush, before the durable checkpoint. It is
  **at-least-once**: committed DML can be replayed after failure. A convergent
  target table is not evidence of side-effect-free exactly-once delivery.
- MySQL XA RETRACT executes DML, prepares an XA transaction at the aligned
  checkpoint, stores the prepared XID alongside Source offsets, and only commits
  after durable checkpoint publication.
- A declined checkpoint does not rewind the running Source. Prepared transactions
  from that attempt must be carried into the next checkpoint (or rolled back
  during whole-job recovery if they were never persisted).
- On restart, recover XIDs from the *latest durable checkpoint only*; roll back
  owned prepared XIDs absent from it, and commit retained XIDs. Never touch
  foreign transactions.
- Connector/J generally returns the complete `XA RECOVER` result at the start
  of a scan, but the XA API permits paged results. Finish the scan before
  performing rollback decisions. Bound scan attempts and result cardinality.
- Only a transaction known to be restored from a durable checkpoint may treat
  `XAER_NOTA` as a possible previously committed XID. This assumes the
  prepared transaction was not manually rolled back outside YakFlow.

Reference: [Flink JDBC SimpleXaConnectionProvider](https://apache.googlesource.com/flink-connector-jdbc/+/92e2b5f73ff087e56e56a55658443d7940efd827/flink-connector-jdbc/src/main/java/org/apache/flink/connector/jdbc/datasource/connections/xa/SimpleXaConnectionProvider.java).

## Recovery fault cases

| Failure point | Recovery outcome |
| --- | --- |
| After Source emission, before Checkpoint | Replay from previous durable offset |
| After ordinary JDBC commit, before Checkpoint | UPSERT can be replayed |
| After XA PREPARE, before Checkpoint | Non-durable prepared XID is an orphan; roll back |
| After Checkpoint publication, before XA COMMIT | Commit XIDs retained in the durable checkpoint |
| After XA COMMIT, before its acknowledgment | Retry durable XIDs; recovered XAER_NOTA is conditional |
| Declined Checkpoint with continued execution | Retain its XIDs through the next successful Checkpoint |

## Scope and limits

The current runtime supports a single linear, single-JVM job; this is not a
distributed Flink checkpoint implementation. Ordinary JDBC remains at-least-once,
and MySQL XA is presently admitted only for the single-route RETRACT path.
These guarantees assume the checkpoint store is durable and the MySQL XA resource
retains prepared transactions. An atomic rename without confirmed directory
durability does not alone prove crash consistency under sudden machine power loss.

Tests cover XA recovery page continuation, owned-orphan cleanup, restored
`XAER_NOTA` semantics, multiple persisted committables, and a two-prepared-
transaction MySQL recovery case. Process `SIGKILL`, abrupt power loss, and
database failover remain separate end-to-end acceptance requirements.
