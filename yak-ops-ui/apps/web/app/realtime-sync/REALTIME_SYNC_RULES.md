# Realtime Sync Frontend Rules

Scope:

- `yak-ops-ui/apps/web/app/realtime-sync/**`
- REALTIME mode of `yak-ops-ui/apps/web/app/data-sync/task-editor.tsx`

## PR4 Boundary

Realtime Task UI owns task discovery, create/edit configuration and manual start.

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
- After Save & Start, return to the REALTIME task list until the realtime Instance UI is introduced.
- Keep Start / Edit / Delete actions on the task list.

Must Not:

- Show or edit state directories, Debezium offsets, schema history or serverId.
- Add Instance tabs/detail pages before PR5.
- Add scheduler, retry policy, Transform, DDL sync or multi-table configuration.
- Reuse the Offline Instance detail page for REALTIME.
