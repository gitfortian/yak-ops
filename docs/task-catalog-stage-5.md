# Task Catalog Stage 5

Stage 5 turns a published task revision into a platform-level task asset that orchestration surfaces can discover without reaching into a source domain's tables.

## Goal

```text
Data Development node: 今天统计
        |
        | save
        v
TaskDraft #5
        |
        | publish
        v
TaskRevision v1 (immutable)
        |
        | upsert
        v
TaskAsset
  source = DATA_DEVELOPMENT
  sourceRef = <development node id>
  taskType = SQL
  status = ONLINE
  currentRevision = v1
        |
        v
Task Catalog
        |
        v
Workflow task picker / 数据开发
```

A draft is never a task asset. Only a successfully validated published revision enters the catalog.

## Module boundary

A new neutral business module owns discovery metadata:

```text
yak-ops-business-task-catalog
  |- domain/TaskAsset
  |- repository/TaskAssetRepository
  |- service/TaskCatalogService
  |- controller/v1/TaskCatalogController
  `- db/migration/yak-task-catalog
```

Data Development depends on Task Catalog, not the other way around. Workflow discovers catalog assets through the catalog API and does not read `yak_dev_node`, `yak_dev_task_draft` or `yak_dev_task_revision` directly.

## Persistence

`yak_task_asset` is a small source-neutral index:

```text
id
source
source_ref
project_id
name
task_type
status
current_revision_id
current_revision_no
create_time
update_time
```

`(source, source_ref)` is unique. Publishing v2 updates the same asset instead of creating another asset.

The catalog deliberately does not duplicate SQL text or plugin configuration. Immutable executable content remains in the source revision table. `current_revision_id/current_revision_no` are discovery pointers.

## Publish semantics

Data Development publishing remains one business transaction:

1. lock Draft;
2. validate TaskDefinition through TaskPlugin;
3. create/reuse immutable TaskRevision;
4. mark the development node configured;
5. upsert the corresponding `DATA_DEVELOPMENT` TaskAsset as `ONLINE`.

Even an idempotent re-publish of an unchanged Draft calls Task Catalog again. This lets the publish operation repair a missing/stale catalog row without manufacturing another TaskRevision.

Example:

```text
今天统计
  publish -> revision v1 -> asset currentRevision=v1
  edit
  publish -> revision v2 -> same asset currentRevision=v2
```

## Source lifecycle synchronization

For an already-published Data Development node:

- rename updates TaskAsset display metadata;
- deleting the source node marks the asset `OFFLINE`;
- the asset retains its latest revision pointer so historical references are not erased.

An unpublished node rename/delete does not create a catalog row because catalog metadata updates are no-ops when `(source, source_ref)` does not exist.

## API

```http
GET /api/v1/task-catalog/assets
```

Filters:

```text
source  = DATA_DEVELOPMENT | DATA_INTEGRATION | DATA_QUALITY | INTERNAL
status  = ONLINE | OFFLINE | ARCHIVED | DRAFT
keyword = name/sourceRef fuzzy search
```

`status` defaults to `ONLINE` so orchestration discovery does not accidentally surface offline assets.

Example response shape:

```json
{
  "id": 12,
  "source": "DATA_DEVELOPMENT",
  "sourceRef": "10001",
  "name": "今天统计",
  "taskType": "SQL",
  "status": "ONLINE",
  "currentRevision": {
    "taskAssetId": 12,
    "taskRevisionId": 90001,
    "revisionNo": 2
  }
}
```

## Workflow discovery in Stage 5

`WorkflowTaskPicker` loads:

```http
GET /api/v1/task-catalog/assets?source=DATA_DEVELOPMENT&status=ONLINE
```

Published assets appear under the existing **数据开发** category together with their current revision badge, for example `已发布 v2`.

They are intentionally discovery-only in this stage. The picker disables adding the catalog item and explains that fixed revision binding is introduced in Stage 6.

This is important because enabling selection now would make Workflow persist the legacy generic `taskId`, which cannot yet express the required pair:

```text
taskAssetId + taskRevisionId
```

Stage 5 therefore proves publication and discovery without creating a temporary binding format that must later be migrated.

## Why Workflow does not query Data Development directly

The same catalog contract will later accept assets from:

```text
DATA_INTEGRATION
DATA_DEVELOPMENT
DATA_QUALITY
INTERNAL
```

Workflow remains source-neutral and only depends on task assets. Source-specific draft tables stay private to their authoring domains.

## Transaction and failure behavior

Data Development and Task Catalog use the same Yak business datasource and `yakBusinessTransactionManager`. Catalog publication joins the existing publish transaction. A catalog write failure therefore fails the publish transaction instead of leaving a revision reported as successfully published but invisible to orchestration.

Task Catalog has an independent Flyway location/history table:

```text
classpath:db/migration/yak-task-catalog
flyway_schema_history_task_catalog
```

## Tests

Stage 5 adds/updates tests for:

- TaskAsset publish/upsert normalization;
- default `ONLINE` catalog filtering;
- Development publish -> catalog registration;
- unchanged re-publish -> catalog reconciliation without duplicate TaskRevision;
- development node rename -> catalog metadata sync;
- source deletion -> asset `OFFLINE`.

## Deliberately out of scope

Stage 5 does not:

- persist `taskAssetId + taskRevisionId` on Workflow nodes;
- allow catalog assets to be dragged/added to the Workflow graph yet;
- silently upgrade an existing Workflow to a newer task revision;
- execute SQL from Workflow/Schedule through the unified Task Runtime;
- add durable TaskExecution/Attempt persistence;
- implement Shell/Python/HTTP execution or Worker backends.

## Next: Stage 6

Stage 6 should make a Workflow node bind explicitly to:

```text
taskAssetId = 12
taskRevisionId = 90001
revisionNo = 2
```

After that, a later Data Development publish to v3 changes the catalog's `currentRevision`, but the existing Workflow stays on v2 until the user explicitly upgrades it.
