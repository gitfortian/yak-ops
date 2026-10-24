# YOIP-10: Keyless CDC Admission Contract

Status: **contract only** (PR 1). No unkeyed CDC or JDBC RETRACT writer is executable.

## Separate the identities

The MySQL source **Chunk Key** selects a half-open snapshot range. It need not be
unique and must never be interpreted as a source or target row identity. A keyless
snapshot or Binlog change can contain multiple identical row images.

The JDBC **RETRACT** write mode is reserved for exact-row, one-row-at-a-time
deletion and transactional replay protection. Unlike UPSERT, it does not identify
a row by a physical primary key. Merely adding this enum value does not implement
those semantics.

## First admission requirements

| Area | Requirement |
| --- | --- |
| Source table | No physical primary key; resolved column types |
| Source option | Explicit `data-sync.mysql-cdc.keyless.enabled=true` |
| Snapshot Chunk Key | Explicit, present, non-null, TINYINT/SMALLINT/INTEGER/BIGINT/DECIMAL |
| MySQL Binlog | `binlog_format=ROW`, `binlog_row_image=FULL`, empty `binlog_row_value_options` |
| Source DDL | Keyless schema changes must not silently change snapshot column layout |
| Sink mode | Reserved `RETRACT`, **not** APPEND or UPSERT |
| Target row identity | No invented PK; full-row source/target mapping with lossless exact comparison |
| Unsupported comparisons | Text/collation, floating point, time-zone-sensitive fields not yet accepted for exact-row matching |
| Target constraints | Indexes, unique keys, triggers, generated fields and collation require live database catalog preflight |
| Crash recovery | DML and event replay marker must commit in one target transaction |

Existing MySQL global Binlog validation is already implemented in
`MySqlBinlogConfigurationValidator` and runs before Debezium replication.
It is **reused**; PR 1 does not open additional connections during Task admission.
Runtime preparation must perform the catalog checks and compare session/global
Binlog settings before a keyless Source is activated in later PRs.

## Safety boundaries for this PR

- An explicit keyless option receives precise schema validation, then fails
  with an unsupported-execution reason. Missing opt-in retains legacy admission.
- `JdbcSinkFactory` rejects RETRACT for all source modes until the executable
  writer exists; direct `JdbcTableWritePlan` construction also fails closed.
- The contract checker is intentionally stricter than future supported types.
  Source/target text columns need physical collation and comparison verification,
  not an unsafe Java `String.equals` assumption.
- APPEND remains bounded INSERT-only; keyed UPSERT remains the changelog path.
- No UI enabling, automatic migration, changed checkpoint interpretation or
  source/sink fallback is introduced.

## Next PRs

PR 2: typed Snapshot Split assignment and discovery recovery.
PR 3: typed JDBC Snapshot Reader.
PR 4: keyless Snapshot Backfill integration.
PR 5: Binlog watermark handoff and moving-Chunk-Key safety.
PR 6: event identity through Runtime and Checkpoint.
PR 7: exact-row RETRACT for MySQL.
PR 8: transactional replay protection and failure-injected MySQL E2E.
PR 9: live PostgreSQL and Oracle target acceptance.

No keyless product mode may be marked supported until its target-database E2E
has demonstrated duplicate-row fidelity and crash recovery.
