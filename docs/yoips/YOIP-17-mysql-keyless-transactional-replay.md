# YOIP-17 — MySQL Keyless CDC Transactional Replay Protection

Status: **PR 8 of nine**. Transactional internal MySQL JDBC RETRACT writer;
the public keyless Source and Sink factory admission remain closed.

## Correctness boundary

MySQL can commit destination changes before the YakFlow Source checkpoint finishes.
A restart will then replay previously delivered events. For keyless rows, replaying
INSERT adds duplicates, and replaying DELETE removes another physical occurrence.
Neither a Chunk Key nor a JVM cache can make this safe.

PR 6 provides replay-stable `ChangeEventId` transport identities. PR 7 provides
full-row `DELETE ... LIMIT 1` and atomic UPDATE_BEFORE/UPDATE_AFTER DML. PR 8 adds
a **durable receipt** reserved exactly once in the *same target JDBC transaction*
as the corresponding DML.

## Target database preparation

The target database administrator must provision a durable table in the **same
MySQL database/catalog** as every RETRACT destination:

```sql
CREATE TABLE `yak_flow_cdc_receipts` (
  `receipt_key` BINARY(32) NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
```

Every RETRACT Writer checks the actual table's engine, single column definition,
unique primary key and absence of triggers **before** processing events.
Missing or incompatible receipt tables cause an error. The connector never
creates, alters, truncates or drops this table. It also verifies the target
table itself remains a supported keyless InnoDB table.

**Never clear receipts during a task restart, checkpoint replay, rescale or
connector upgrade.** Retention must exceed the maximum lifetime of all
potential replay sources, including backups and manually restored checkpoints.
The current implementation provides no automated receipt pruning; no time-based
retention may be assumed safe without a durable source-completion frontier.

The Writer needs INSERT access to this table and SELECT access to
`information_schema`. Database permissions should prohibit unreviewed DDL
and unsafe cleanup during active CDC workloads.

## Reservation transaction

The receipt key is SHA-256 over a domain-separated, length-prefixed tuple:
stable `sourceId`, source table identity, target table identity, and event ID.
No mutable row values, local attempt counters or business PKs are involved.
UPDATE_BEFORE and UPDATE_AFTER share one logical event receipt.

For each complete mutation, in input order on a single owning JDBC connection:

1. Execute any earlier pending row statements in the current transaction.
2. `INSERT IGNORE INTO yak_flow_cdc_receipts (receipt_key) VALUES (?)`.
3. If affected rows is 1, apply the mutation; if 0, skip **all** its DML.
   Any other result or SQL error aborts the transaction.
4. Commit receipts and DML together through the existing Writer flush.
   Cancellation, failed DELETE count, incomplete UPDATE or failed transaction
   rolls back both and makes the Writer fail closed.

UPDATE image order and identifiers are checked before admission: two images
on one source table, one shared event, indexes 0 and 1, and image count 2.
All single-image records must have index 0 and count 1. A missing or coarse
Debezium position cannot be replaced with a guessed ID.

An ambiguous commit is never automatically retried within the same JDBC
connection. On a later attempt, the target receipt is authoritative: a prior
commit is skipped; a prior rollback permits the event to be applied.

Unique InnoDB receipts serialize concurrent attempts for the same event, even
if two subtasks attempt replay. This is **per-target MySQL transactional
idempotence**, not a distributed XA transaction with the Source checkpoint.

## Acceptance

- Real MySQL: identical INSERT payloads with distinct event IDs remain distinct.
- Crash-after-target-commit replay: INSERT, DELETE and UPDATE do not change the
  committed row multiplicity or apply the same event twice.
- Failed mutation rolls back its receipt; recovery can apply the same ID once.
- Missing or malformed event identities fail before DML; existing keyed
  APPEND/UPSERT paths preserve their current contracts.
- Real Debezium MySQL Binlog: multiple rows from one SQL statement retain
  different IDs; a Binlog rotation does not collide with previous events.
- Existing JDBC/MySQL, CDC, CDC pipeline, recovery tests and Spotless remain
  required. Do not weaken tests to permit unsupported configurations.

## Remaining product gates

Public `MySqlCdcSourceFactory` keyless admission, typed
`MySqlHybridSplitReader.addSplits`, and public `JdbcSinkFactory` RETRACT
admission are intentionally still disabled. A successful receipt integration
test alone does not establish end-to-end keyless support.

Before enabling public execution, a dedicated Source → Runtime → MySQL Sink
fault-injected E2E must verify the **whole** Hybrid snapshot, checkpoint
handoff, Binlog recovery, exclusive Sink event ordering and actual task restart.
The current Source typed Reader gate must be changed only with these tests,
not independently. This restriction is tracked as a release acceptance blocker,
not silently bypassed in PR 8.

PR 9 must separately validate target-specific PostgreSQL/Oracle behavior,
rather than assuming MySQL's null-safe comparison, LIMIT 1 or receipt DDL
translates directly to other dialects.
