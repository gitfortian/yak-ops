# Data Service Migration Contract

Data Service schema changes must not allocate versions from the Datasource Flyway sequence.

## History split

The project originally placed Data Service migrations in the shared location:

```text
classpath:db/migration/yak-datasource
history table: yak_ds_schema_history
```

That made Datasource and Data Service compete for one global version sequence and caused the concrete startup failure where both modules provided `V11`.

Historical Data Service migrations that may already be recorded in `yak_ds_schema_history` are therefore frozen and must not be renamed, edited or deleted:

```text
V3  create data service tables
V4  source revision identity
V5  API-key security/audit caller identity
V6  local runtime resilience policy
V7  documentation metadata
V9  pagination
V10 overview indexes
```

They are compatibility history only.

## Dedicated Data Service namespace

Current and future Data Service migrations live under:

```text
classpath:db/migration/yak-data-service
history table: yak_data_service_schema_history
```

`DataServiceFlywayConfiguration` depends on `opsDataSourceFlyway`, so the shared legacy baseline is migrated first.

The dedicated history starts at baseline version `0`. The first migration is:

```text
V1__consolidate_data_service_governance_runtime.sql
```

It consolidates the previously pending Data Service V11/V12/V13 changes:

- service-scoped invocation-log index
- Project ownership columns/indexes
- monotonic `runtime_generation`
- shared API-key rate-limit window
- hourly invocation rollup

Those old V11/V12/V13 files must not return to `db/migration/yak-datasource`.

## Rules for future changes

1. New Data Service schema changes use only `db/migration/yak-data-service`.
2. Version numbers are local to `yak_data_service_schema_history` (`V2`, `V3`, ...).
3. Applied migrations are immutable; never squash or rename them in place.
4. A new pre-release baseline may be created only together with an explicit database reset/repair plan.
5. Datasource migrations remain owned by `opsDataSourceFlyway`; Data Service must not allocate versions in that namespace again.
