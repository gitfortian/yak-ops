# Realtime Sync Frontend Rules

Scope:

- `yak-ops-ui/apps/web/app/realtime-sync/**`
- REALTIME mode of `yak-ops-ui/apps/web/app/data-sync/task-editor.tsx`

## Current V1 Boundary

Realtime Sync owns Task definition, publication configuration and Task-scoped read-only runtime detail. Operations Center owns cross-Task REALTIME observability through the aggregate Dashboard. Backend acceptance proves the configured REALTIME path against MySQL, PostgreSQL and Oracle targets; the frontend does not duplicate that database-specific validation logic.

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
- The editor exposes Save and Save & Publish. Save persists an UNPUBLISHED Task; Save & Publish explicitly composes save then publish and returns to the Task list. Runtime start is not an editor action.
- Task list shows the persisted publication status as 已下线 / 已上线 and may filter by that status.
- Task list is a definition summary surface: show task/version, compact Source → Target, publication status, updater/update time and actions. Do not repeat the product-level `MySQL CDC` mode in every row.
- Source / Target use the compact `Datasource Name.Table` form and the same vertical flow used by OFFLINE: Source first, lightweight downward connector, Target second. Do not repeat database/schema context in the list summary.
- Resolve current-page `updateBy` values with one batch user lookup and render updater + update time as `更新信息`; historical `system` stays `SYSTEM`.
- Fix the action column on the right through Yak UI Table `fixed: "right"`; do not rebuild sticky-column CSS in product code.
- Task list keeps four stable action slots: 上线/下线、编辑、详情、删除. `详情` is always visible and opens `/realtime-sync/:taskId/detail` inside Data Integration.
- UNPUBLISHED Task enables 上线 / 编辑 / 详情 / 删除. PUBLISHED disables 编辑 / 删除; an active PENDING / RUNNING / RETRY_WAITING Execution also disables 下线 while 详情 remains available.
- Start / Stop never execute from the Realtime Sync list, editor or Task Detail.
- Direct navigation to an editor for a PUBLISHED Task must not expose an editable form; guide the user back to the list to unpublish first.
- Keep the Realtime Sync editor definition-focused; runtime history belongs to the separate Task Detail surface, not an editor Tab.
- Task Detail shows Basic Info plus Task-filtered Execution history. The selected Execution uses `执行情况 / 配置快照 / 执行日志` Tabs: `执行情况` uses the shared result-first presentation, keeps internal Execution ID out of the primary surface, places failure context before metrics and only expands retry history when retries exist; `配置快照` reads the selected Execution's frozen definitionSnapshot and groups Source-side CDC parameters, Sink-side write parameters and Execution strategy such as Checkpoint / timeout / retry; logs display persisted product events, not Server Log lines.
- Selected Execution status continues polling only while active data exists. `执行日志` polls every 2 seconds only while the log Tab is visible and the selected Execution is PENDING / RUNNING / RETRY_WAITING; terminal or hidden logs do not keep polling. Historical Executions may legitimately have an empty event timeline and frontend must not synthesize missing history.
- Operations Center REALTIME root reads the aggregate Dashboard with `syncType = REALTIME`; never fetch mixed OFFLINE/REALTIME Instance pages and filter them only in frontend memory.
- The REALTIME Dashboard is observability-only and shows current active Task count, abnormal Task count, AUTO_RECOVERY count, abnormal Execution count, Execution creation trend, status distribution, automatic recovery trend and FAILED + LOST Task ranking.
- The Dashboard must not present readRows / writeRows as TPS, events/s, CDC Lag or Checkpoint Lag. Those continuous runtime metrics require a future persisted Metrics Time Series.
- Legacy Operations Execution detail routes remain compatibility-only. If directly opened, active REALTIME detail may still show Stop plus the Yak UI `Alert` about resuming from the latest completed Checkpoint; the Dashboard does not link to those routes as its primary workflow. Task Detail remains read-only.
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
