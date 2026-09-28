# Realtime Sync Frontend Rules

Scope:

- `yak-ops-ui/apps/web/app/realtime-sync/**`
- REALTIME mode of `yak-ops-ui/apps/web/app/data-sync/task-editor.tsx`

## PR5 Boundary

Realtime Sync owns Task configuration plus persisted Instance runtime presentation.

Must:

- Query Task list with `syncType = REALTIME`.
- Source Datasource must be MySQL.
- Target Datasource may be MySQL / PostgreSQL / Oracle.
- Reuse the shared Data Sync Task Editor for Catalog and field mapping.
- Configure only:
  - `checkpointIntervalSeconds`
  - `queueCapacity`
  - `pollBatchSize`
  - `writeBatchSize`
  - `timeoutSeconds`
- Explain that first start performs the initial snapshot and then continuously consumes MySQL Binlog.
- Expose Save and Save & Start.
- After Save & Start, navigate to the created REALTIME Instance detail.
- Keep Start / Edit / Instance / Delete actions on the task list.
- Keep Task Definition and Task Instance as separate Tabs.
- Query Instance page with `syncType = REALTIME`; never filter mixed OFFLINE/REALTIME results only in frontend memory.
- Poll Instance list/detail every 2 seconds only while PENDING/RUNNING data is visible.
- PENDING/RUNNING Instances expose Stop.
- Display persisted `readRows/writeRows` as `读取事件/写入事件`; do not rename them to business row counts.
- Explain that UPDATE produces UPDATE_BEFORE + UPDATE_AFTER events in current YakFlow metrics.
- Display REALTIME snapshot runtime parameters from `realtimeConfig`.
- Display CANCELED as `已停止` for realtime product wording.

Must Not:

- Show or edit state directories, Debezium offsets, schema history or serverId.
- Invent or estimate checkpoint time when the backend does not persist it.
- Add scheduler, retry policy, Transform, DDL sync or multi-table configuration.
- Copy the Offline Instance implementation; OFFLINE and REALTIME wrappers must reuse the shared Data Sync runtime component.
