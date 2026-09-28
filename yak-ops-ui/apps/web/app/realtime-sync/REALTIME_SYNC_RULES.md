# Realtime Sync Frontend Rules

Scope:

- `yak-ops-ui/apps/web/app/realtime-sync/**`
- REALTIME mode of `yak-ops-ui/apps/web/app/data-sync/task-editor.tsx`

## Current V1 Boundary

Realtime Sync owns Task configuration plus persisted Instance runtime presentation. Backend Phase 6 additionally proves the configured REALTIME path against MySQL, PostgreSQL and Oracle targets; the frontend does not duplicate that database-specific validation logic.

Must:

- Query Task list with `syncType = REALTIME`.
- Source Datasource must be MySQL.
- Target Datasource may be MySQL / PostgreSQL / Oracle.
- Reuse the shared Data Sync Task Editor for Catalog and field mapping.
- Treat backend validation as the source of truth for exact Source/Target primary-key correspondence; do not reimplement the PK contract in frontend state.
- Configure only:
  - `checkpointIntervalSeconds`
  - `queueCapacity`
  - `pollBatchSize`
  - `writeBatchSize`
  - `timeoutSeconds`
- Explain that first start performs the initial snapshot and then continuously consumes MySQL Binlog.
- When editing an existing UNPUBLISHED REALTIME Task, show a Yak UI `Alert` that executable-definition changes create a new Task version and that version's first start performs a fresh initial snapshot; metadata-only name/remark changes do not increment the version.
- In the REALTIME source section, show a Yak UI `Alert` that ROW Binlog and CDC account permissions are required; ordinary Datasource connection-test success does not prove CDC readiness.
- The editor exposes Save and Save & Publish. Save persists an UNPUBLISHED Task; Save & Publish explicitly composes save then publish and returns to the Task list. Start remains a separate PUBLISHED Task action.
- Task list shows the persisted publication status as 已下线 / 已上线 and may filter by that status.
- UNPUBLISHED Task actions are 上线 / 编辑 / 实例 / 删除.
- PUBLISHED idle Task actions are 启动 / 下线 / 实例.
- PUBLISHED Task with a PENDING / RUNNING Instance exposes 停止 / 实例; 下线 is unavailable until the active Instance ends.
- Direct navigation to an editor for a PUBLISHED Task must not expose an editable form; guide the user back to the list to unpublish first.
- Keep Task Definition and Task Instance as separate Tabs.
- Query Instance page with `syncType = REALTIME`; never filter mixed OFFLINE/REALTIME results only in frontend memory.
- Poll Instance list/detail every 2 seconds only while PENDING/RUNNING data is visible.
- PENDING/RUNNING Instances expose Stop.
- On active REALTIME Instance detail, show a Yak UI `Alert` that restart continues from the latest completed Checkpoint and a small set of unconfirmed events may be consumed again.
- Display persisted `readRows/writeRows` as `读取事件/写入事件`; do not rename them to business row counts.
- Explain that UPDATE produces UPDATE_BEFORE + UPDATE_AFTER events in current YakFlow metrics.
- Display REALTIME snapshot runtime parameters from `realtimeConfig`.
- Display CANCELED as `已停止` for realtime product wording.
- Datasource / Schema / Table are dynamic resource Selects: their popup uses Yak UI Select Search composition with local keyword filtering and an explicit refresh action.
- Datasource Select footer exposes “新增数据源” as a product-owned action and routes to Datasource create; source/target type restrictions still come from Realtime product rules.
- Static lifecycle/status Selects stay simple and do not add search / refresh / footer without a real option-volume need.

Must Not:

- Show or edit state directories, Debezium offsets, schema history or serverId.
- Invent or estimate checkpoint time when the backend does not persist it.
- Add scheduler, retry policy, Transform, DDL sync or multi-table configuration.
- Copy the Offline Instance implementation; OFFLINE and REALTIME wrappers must reuse the shared Data Sync runtime component.
