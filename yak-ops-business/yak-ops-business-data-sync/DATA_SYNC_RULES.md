# Data Sync Rules

Scope:
- `yak-ops-business/yak-ops-business-data-sync/**`
- `yak-ops-common/src/main/java/io/yak/ops/common/**/datasync/**`
- `yak-ops-dao/src/main/java/io/yak/ops/dao/**/datasync/**`
- `yak_ops_data_sync_*` schema

Status:
- Active / Staged

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`
- `/yak-ops-business/BUSINESS_RULES.md`
- `/yak-ops-common/DTO_VO_RULES.md`
- `/yak-ops-dao/DAO_RULES.md`
- `/yak-ops-dao/ENTITY_RULES.md`
- `/yak-ops-dao/FLYWAY_RULES.md`
- `/docs/capabilities/data-sync/README.md`

## Business Boundary

Data Sync owns product task definitions and task instances. YakFlow owns execution mechanics.

```text
DataSyncService
      ↓
Task / Instance persistence

future OfflineSyncExecutor
      ↓
YakFlow
```

This capability explicitly uses `DataSyncService / DataSyncServiceImpl` naming.

## Task Definition

Must:
- Be Workspace-scoped.
- Keep task name unique inside one Workspace.
- Persist datasource references by datasource ID, not by copying credentials.
- Keep `definitionVersion` starting at 1 and increment it on every successful task-definition update.
- Persist YakFlow tuning in `runtimeConfig`; it may contain batch/fetch/timeout settings only.
- Support only `OFFLINE` task type in the current phase.

Must Not:
- Persist datasource password, `connection_params`, `original_json`, SSH private key, token or other secret in a task.
- Create a second datasource connection model inside Data Sync.
- Start YakFlow as a side effect of create/update/query methods in Phase 1.

## Task Instance

Task instance is historical execution state, not the current task definition.

Persisted instance states:

```text
PENDING
RUNNING
SUCCEEDED
FAILED
CANCELED
LOST
```

Trigger types:

```text
MANUAL
SCHEDULE
RETRY
```

Phase 1 only defines persistence. Runtime state transitions are introduced with execution work.

The instance `definitionSnapshot` is immutable execution input captured when an instance starts. It may include task name, datasource IDs/names/types, table locations, runtime config and future field mappings.

It must never contain:
- normalized datasource connection JSON.
- original datasource JSON.
- database passwords.
- SSH password/private key/passphrase.
- API tokens, access keys or equivalent secrets.

## Persistence

Tables:

```text
yak_ops_data_sync_task
yak_ops_data_sync_instance
```

No database physical foreign keys.

Repository queries must always scope Task / Instance product access by `workspace_id`.

Task deletion does not imply deleting historical instances.

## Current Phase

Phase 1 implements:
- task create/update/delete/detail/page.
- instance detail/page query.
- persistence contracts.

Phase 1 does not implement:
- instance creation.
- run/cancel/retry.
- scheduler.
- startup LOST recovery.
- YakFlow execution registry.
