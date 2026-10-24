# YOIP-9: CDC Chunk Key and JDBC Row Identity Acceptance

Status: PR 7 — row identity guardrails and supported-path acceptance.

## Distinct keys

**Chunk Key** selects the database-ordered snapshot range. It can be non-unique in
the standalone typed MySQL chunk planner. It is **not** the identity of a row.

**Source physical primary key** uniquely identifies a row in the MySQL change
stream. **Sink physical primary key** determines the row affected by JDBC UPSERT,
DELETE and UPDATE_BEFORE. The source keys must map one-to-one to the target keys.
A snapshot Chunk Key or a list of all source field values is not a substitute.

The metadata's `TableSchema.primaryKeys()` is the present authoritative physical
primary-key contract; there is no separate verified unique-index/business-key
capability in this release. A proposed business key must not be accepted merely
because its column names match.

## Supported / rejected modes

| Source | Sink write mode | Admission | Reason |
| --- | --- | --- | --- |
| MySQL CDC, single non-null BIGINT PK | JDBC UPSERT, matching physical PK | Supported | Legacy Hybrid Reader + key-aware JDBC Sink, real MySQL acceptance |
| MySQL CDC, source PK renamed at target | JDBC UPSERT, one-to-one key mapping in write plan | Supported at direct write-plan contract | SQL binds the declared target PK to mapped source PK |
| MySQL CDC, composite or non-BIGINT PK | JDBC UPSERT | Not yet supported | Hybrid snapshot Reader still requires one BIGINT PK |
| MySQL CDC, no physical PK, explicit Chunk Key | JDBC UPSERT | Rejected | Chunk Key does not identify duplicate rows |
| MySQL CDC, no PK | JDBC APPEND | Rejected | APPEND cannot apply streaming DELETE / UPDATE |
| Bounded JDBC source, no PK | JDBC APPEND | Supported when schemas/types match | INSERT-only path does not require row identity |
| Any source without physical PK | JDBC UPSERT with target PK invented from nonunique field | Rejected | DELETE or UPDATE could affect unrelated rows |
| JDBC UPSERT with target key mapped to source non-key field | JDBC UPSERT | Rejected | Invalid source-row identity projection |

The live CDC Source still rejects typed-only ranges. PR 6 can restore their
serialized boundaries, but running them requires chunk-aware Reader, safe
collation comparisons and full source-to-sink recovery integration. This PR
does not change the Source's admission condition.

## Runtime and DDL rules

- Check both at **Definition/Job planning** and **direct Sink construction**.
  Building a `JdbcTableWritePlan` directly must apply the same key projection rule.
- Source and target primary-key columns may have different **names** only if the
  explicit write-plan mapping is one-to-one and type-safe. The default product
  route currently relies on matching column names.
- A target key that maps to a different, non-key source column is never legal.
  All components of a composite physical PK must survive projection.
- Existing JDBC source / target DDL reconciliation cannot silently turn a
  no-PK table into a changelog-capable table by inventing a synthetic key.
- MySQL `ON DUPLICATE KEY UPDATE` also matches **other unique constraints** on
  the target, not just its primary key. Additional target unique constraints
  therefore need separate physical-metadata compatibility assessment before
  a blanket exactly-once guarantee can be stated.
- Native JDBC transactions and Source checkpointing do not by themselves
  establish an end-to-end exactly-once Sink guarantee. Source offset,
  retry/replay and target idempotency must be evaluated together.

## Acceptance evidence

- Unit tests cover the full row-identity mapping: identity keys, renamed
  physical keys, composite physical keys, keyless rejections, non-key mapping
  rejections and INSERT-only APPEND.
- MySQL / PostgreSQL / Oracle dialect unit tests verify target-keyed native
  UPSERT and DELETE SQL (not three live target databases).
- Real MySQL CDC integration tests cover duplicated non-key payloads, DELETE
  of one physical row, source PK moves and streaming checkpoint recovery.
- Existing `cdc`, `cdc-pipeline` and `recovery-e2e` jobs must remain green.

## Still required before enabling keyless end-to-end CDC

Verify physical source unique identity or introduce a rigorously defined sink
changelog strategy for keyless rows, handle full row-image DELETE multiplicity
without deleting all identical matches, preserve chunk-key movement with MySQL
collation semantics, and run real database target tests with failure injection.
A target's plain `DELETE WHERE all_columns = ...` is **not** safe when duplicate
rows exist. Do not enable this by relaxing `MySqlCdcSourceFactory` or
`JdbcSinkSchemaValidator`.
