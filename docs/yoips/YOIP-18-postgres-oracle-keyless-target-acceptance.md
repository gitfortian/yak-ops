# YOIP-18 — PostgreSQL and Oracle Keyless Target Acceptance

Status: **PR 9 of nine**. Internal JDBC target acceptance following MySQL PR #1732 and
transactional replay PR #1733. This does **not** enable the product's keyless CDC switch.

## Exact-row semantics

A Snapshot Chunk Key is not a physical row key. Every DELETE and UPDATE_BEFORE
uses **all before-image columns**, not a guessed primary key. The target database
must remove exactly one matching physical occurrence of a duplicate row.

| Target | One-row match | Nullable values | Receipt reservation |
|---|---|---|---|
| MySQL | `DELETE ... LIMIT 1` | `<=>` | `INSERT IGNORE` |
| PostgreSQL | subquery-selected `ctid` with `LIMIT 1 FOR UPDATE` | `IS NOT DISTINCT FROM` | `INSERT ... ON CONFLICT DO NOTHING` |
| Oracle | subquery-selected `ROWID` with `ROWNUM = 1` | `DECODE(column, ?, 1, 0) = 1` | `MERGE ... WHEN NOT MATCHED THEN INSERT` |

PostgreSQL `ctid` and Oracle `ROWID` are used **only within one DELETE
statement** to choose a row already matched by its complete contents. They are
not carried in events, persisted as a business key, or relied upon across
transactions. The Writer still requires each DELETE result to be exactly one
affected row; an absent match aborts and rolls back the entire JDBC transaction.

An UPDATE reserves **one** event receipt, then deletes the before-image and
inserts the after-image on the same connection. Receipt and data commit together.
If a checkpoint replays a committed mutation, the existing receipt suppresses
**all** its DML. Replays after rollback have no receipt and may be retried.

## Target preparation

Provision one **per target database/schema** durable receipt table **before**
creating a RETRACT Writer. The Writer never auto-creates or drops receipts.

PostgreSQL, in the destination schema:

```sql
CREATE TABLE yak_flow_cdc_receipts (
  receipt_key BYTEA NOT NULL PRIMARY KEY
);
```

Oracle, in the destination owner schema:

```sql
CREATE TABLE YAK_FLOW_CDC_RECEIPTS (
  RECEIPT_KEY RAW(32) NOT NULL PRIMARY KEY
);
```

Both must be persistent, ordinary nonpartitioned heap tables, contain exactly
one receipt-key field, have an enabled primary key and no triggers or
referential side effects. Never truncate the receipt table while a checkpoint
can replay old events. The same source/route/event combination produces the
same SHA-256 receipt key after a restart.

## Vendor-specific safety

PostgreSQL validates actual catalog metadata for the full target projection,
physical column order and nullability, `smallint`, `integer`, `bigint`,
`boolean`, exact `numeric(p,s)` and `bytea`. It rejects unique
indexes, foreign keys, triggers, rules, generated/identity fields,
row-level security, temporary tables, and non-heap targets.

Oracle validates the actual owner, table shape, full column projection,
NUMBER precision/scale/nullability, physical uniqueness, foreign keys and
triggers. For now Oracle only admits signed NUMBER-backed fields, including
null values. RAW full-row equality, strings/collations, floating point,
timestamp and implicit casts are **not** admitted without their own
vendor-specific correctness tests. The RAW(32) **receipt key** is supported
independently of full-row RAW comparisons.

Both validators fail closed before opening DML statements. Adding generated,
computed or unprojected fields, altering column precision, and creating
unreviewed uniqueness constraints must not be silently accepted.

## Live acceptance

- `PostgresKeylessRetractIT`: duplicate rows, nullable and binary images,
  one-row DELETE, atomic UPDATE, crash-after-commit replay, rollback/retry,
  rejecting a unique index.
- `OracleKeylessRetractIT`: duplicate nullable NUMBER rows, one-row DELETE,
  atomic UPDATE, crash-after-commit replay, rollback/retry, unique indexes
  and unsupported RAW matching.
- `MySqlKeylessRetractIT` and the existing JDBC and CDC suites remain required.
  Both new suites are registered with mandatory minimum test counts so an
  unexecuted IT cannot pass unnoticed.

## Release boundary

PR #1733 implemented **target-side transactional replay**. Full end-to-end
keyless CDC still requires enabling and testing the typed Hybrid Reader,
Source Factory and Sink Factory together, with a deterministic failure after
target commit but before Source checkpoint completion. Those gates must stay
closed until that source-to-runtime-to-target integration passes. PostgreSQL
and Oracle target acceptance does not bypass the prerequisite.

Oracle concurrent duplicate receipt collisions must be treated as errors and
retried by a new execution attempt; do not treat unrelated Oracle SQL errors as
proof of a committed receipt. Only one active keyless target writer per route
is permitted until a concurrent-writer recovery scenario is proven.
