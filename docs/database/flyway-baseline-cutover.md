# Flyway baseline consolidation

This document describes the phase-two schema-history cleanup after physical database foreign-key constraints were removed.

## Scope

The following Flyway domains are consolidated to one `V1__baseline_*.sql` file that declares the current schema directly:

- `yak-analysis`
- `yak-dashboard`
- `yak-dataset`
- `yak-lineage`
- `yak-offline-sync`
- `yak-realtime-sync`
- `yak-workflow`

The new baselines keep primary keys, unique keys, `NOT NULL` constraints and query indexes. Cross-domain and lifecycle references remain as logical `*_id` fields and are maintained by application services.

Historical `ALTER TABLE` steps are folded into the table definitions. Historical transition-only data updates are intentionally not copied into the fresh baselines because a new database has no legacy rows to backfill.

## Compatibility boundary

This is a Flyway history rewrite, not an in-place migration.

Do not deploy these rewritten `V1` files directly to a database whose module-specific Flyway history table already records the old `V1/V2/...` migrations. The checksums and migration set are intentionally different.

For an existing database, complete the old migration chain through the phase-one foreign-key-removal migrations first. Then use a controlled cutover process for that environment: back up the database, verify the final schema/data, and re-baseline the module-specific Flyway history (or recreate the development/test database where destructive reset is acceptable).

Production cutover must use an environment-specific migration plan. Do not delete Flyway history rows or reset a production schema as an ad-hoc deployment step.

## Fresh database expectation

A fresh database should execute only the consolidated `V1` for each affected Flyway domain and arrive directly at the current schema:

- Dashboard publish/theme columns are present from table creation time.
- Dataset development-node and standalone SQL source columns are present from table creation time.
- Lineage evidence indexes are present from table creation time.
- Offline-sync sink/runtime metric columns are present from table creation time.
- Workflow trigger dedupe/backfill/instance-operation columns and indexes are present from table creation time.
- No affected baseline creates database-level FK constraints.

This keeps the schema definition readable while preserving the application-layer lifecycle rules introduced in phase one.
