# MySQL JDBC Exact-Row RETRACT

> **Historical engineering note, not an accepted YOIP.** Preserved from [`docs/yoips/YOIP-16-mysql-jdbc-exact-row-retract.md`](../../yoips/YOIP-16-mysql-jdbc-exact-row-retract.md) as of 2026-10-25. The code and tests determine currently supported behavior.
> Provenance: [implementation PR #1732](https://github.com/weifuwan/yak-ops/pull/1732).

Historical implementation scope: **PR 7 of nine**. Internal MySQL Writer capability; no public keyless CDC execution.

## Row identity and SQL

A snapshot Chunk Key is not row identity. A keyless source can contain multiple
identical physical rows. MySQL RETRACT therefore binds the *entire* source
before-image in the target column order, and its dialect emits:

```sql
DELETE FROM `target`
WHERE `col_a` <=> ? AND `col_b` <=> ? AND ...
LIMIT 1
```

The null-safe comparator matches nullable fields and the LIMIT clause removes
only one occurrence of an identical row. The JDBC batch result for **each**
DELETE must be exactly 1. A result of 0, more than 1, an incomplete count,
or SUCCESS_NO_INFO causes the Writer transaction to fail and roll back;
there is no fallback to DELETE by Chunk Key, Java deduplication, or
"DELETE all matching rows". MySQL is the only implemented dialect.

INSERT and UPDATE_AFTER use ordinary parameterized INSERT, retaining physical
duplicate multiplicity. A complete UPDATE_BEFORE + UPDATE_AFTER pair becomes
DELETE(old full row) + INSERT(new full row) **in one JDBC transaction**,
even when the original row values did not change. Both images must be adjacent.
If event identities are present they must identify the same two-image change;
transport event IDs remain outside business RowData and SQL parameters.

## Physical target safety

The low-level Writer validates the actual target **before preparing DML**:

- Transactional InnoDB engine; no target unique index / physical PK.
- No triggers, outbound or inbound foreign-key relationships.
- Complete, ordered physical column projection; no generated or auto-increment
  fields; supported matching signed native types and nullability.
- Logical full-row contract limits comparisons to Boolean, signed integer,
  DECIMAL and binary types. Text/collation, floating point, time zone and
  lossy mappings are not admitted.

The target metadata must remain stable for the duration of an execution; live
schema evolution is rejected. All table and column identifiers are quoted by
the dialect, and values are JDBC parameters.

## Boundaries

**PR 7 does not claim end-to-end exactly-once.** A committed target transaction
may precede a Source checkpoint; replaying the same DELETE/INSERT without a
durable receipt can corrupt multiplicity. The public `JdbcSinkFactory` retains
the explicit RETRACT rejection. All keyless MySQL Source execution gates stay
closed. Only direct internal Writer routes and real-database tests exercise
the implementation.

PR 8 must introduce receipts committed with DML in the *same* target
transaction, reject missing fine-grained Source event IDs, prove multirow
transaction / Binlog rotation uniqueness, and fault-inject restart after
target commit but before Source checkpoint. It must also finish runtime
admission and ordering checks before any public flag is enabled.

PR 9 validates target-specific behavior on PostgreSQL and Oracle rather
than assuming MySQL LIMIT/NULL semantics transfer across vendors.

## Acceptance

- Two identical INSERT images remain two physical rows.
- One DELETE removes exactly one; nullable and VARBINARY before-images match.
- UPDATE swaps one old image for one new image as one transaction.
- Missing DELETE, incomplete UPDATE, or mismatched image IDs fail closed.
- A failing batch rolls back earlier inserts in that same transaction.
- Unsafe target indexes are rejected at Writer creation.
- Existing APPEND, keyed UPSERT and checkpoint recovery behavior is unchanged.

CI must run JDBC unit tests, the real MySQL `MySqlKeylessRetractIT`, and
the repository-selected JDBC/CD C checks. Do not weaken or skip tests.
