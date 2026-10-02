# Offline Sync Frontend Rules

Scope:

- `yak-ops-ui/apps/web/app/offline-sync/**`
- OFFLINE mode of `yak-ops-ui/apps/web/app/data-sync/task-editor.tsx`

## Phase 4 Boundary

The offline product owns Task definition, publication configuration, Schedule definition, a manual Run shortcut on the published Task list, and Task-scoped read-only runtime detail. Operations Center remains the cross-Task observability surface; Stop and Schedule runtime controls are not moved into the Offline Sync definition pages.

Must:

- Reuse saved Datasource resources; never ask for database credentials.
- Keep resource selection and table configuration as separate editor layers: the `数据源` section owns source/target Datasource selectors; `数据来源` owns Schema/table selection; `数据去向` owns Schema/table selection plus OFFLINE write mode.
- Render source and target Datasource cards side by side on wide screens and stack them on smaller screens.
- Do not repeat Datasource Select inside `数据来源` or `数据去向`.
- Treat the saved Datasource connection as the database scope authority. The editor must not expose a second Database selector that can override a bound Datasource database.
- Use Datasource Catalog API for Schema / table discovery inside that Datasource scope.
- Show the bound database / schema as read-only scope context under the Datasource Select.
- Show a Schema Select only when the Datasource itself does not bind a default Schema and Catalog exposes Schemas.
- Display backend mapping preview as the source of truth.
- Keep mapping read-only and same-name in this phase.
- Disable save while the current mapping is incompatible.
- Expose OFFLINE write mode under `数据去向`, never under runtime tuning. Options are APPEND / OVERWRITE / UPSERT with APPEND as the default.
- Use Yak UI `Alert` when OFFLINE write mode is `OVERWRITE`: warn that the target table is cleared before loading and original data is not automatically restored after a later sync failure. Keep APPEND / UPSERT as normal inline descriptions; backend Catalog validation remains the source of truth.
- Keep OFFLINE runtime tuning limited to fetch size, read batch size, write batch size, source parallelism, optional split size and timeout.
- Show the Split consistency Yak UI `Alert` only when `splitSize` is configured: explain that split reads do not guarantee one table-wide snapshot point and may observe different source states while the source table is changing.
- Use existing Yak UI primitives.
- Keep the OFFLINE editor as a full-height local-scroll workspace: PageHeader and the desktop section navigator stay outside the scrolling region, while only the definition content column owns vertical scrolling. Do not rely on sticky positioning for these fixed editor controls and do not change the global AppLayout scroll contract for this page.
- Keep the Offline Sync editor definition-focused; runtime history belongs to the separate Task Detail surface, not an editor Tab.
- Task Detail stays inside Data Integration and shows a compact Task summary plus Task-filtered Execution history. The selected Execution uses `执行情况 / 配置快照 / 执行日志` Tabs: `执行情况` prioritizes result, failure reason and persisted metrics; `配置快照` reads the selected Execution's frozen definitionSnapshot and groups fields into `数据来源 / 数据去向 / 执行策略`; `执行日志` reads persisted product events only. It is read-only for runtime commands.
- OFFLINE Task Detail uses the same full-height local-scroll pattern as the OFFLINE editor: PageHeader stays outside the scrolling region and only the detail content viewport scrolls. Basic Info must not repeat version/type already visible in PageHeader and presents Source → Target as one sync path. Write mode and retry policy are configuration facts and stay out of the Basic Info summary; the selected Execution's frozen values belong to `配置快照`. The primary surface does not expose internal Execution or Datasource IDs. Retry history appears only after an actual retry or while `RETRY_WAITING`. Do not wrap these sections in a second page-wide Card, use sticky positioning, or change the global AppLayout scroll contract.
- `执行日志` only polls every 2 seconds while that Tab is visible and the selected Execution is PENDING / RUNNING / RETRY_WAITING; terminal or hidden logs do not keep polling. Historical Executions may legitimately have an empty event timeline and frontend must not synthesize missing history.
- Operations Center OFFLINE root is an aggregate observability Dashboard. It no longer exposes the previous Task / Instance Tabs or per-Task Run / Stop / Schedule controls; Task Detail remains the place to inspect one Task's Execution history.
- Display readRows / writeRows only from the persisted Instance; frontend must not estimate progress.
- The editor exposes Save and Save & Publish. Save persists an UNPUBLISHED Task; Save & Publish explicitly composes save then publish and returns to the Task list. Runtime execution is not an editor action.
- OFFLINE editor owns optional Schedule definition only: Quartz Cron expression + explicit IANA Time Zone. An empty Cron on a Task that has never created a Schedule means manual-only execution.
- Cron editing uses shared Yak UI `CronSchedulerPicker`: common schedules are configured visually and unsupported advanced Quartz expressions remain available in Advanced Cron mode. The picker may compose a product-owned future-fire preview, but Yak UI itself must not call Data Sync APIs.
- Time Zone is selected rather than free-typed, defaults to `Asia/Shanghai`, and must preserve an already persisted valid zone even when it is outside the common option list. Future 5-fire preview comes from the backend Quartz preview endpoint and is never calculated in browser code.
- Persist Schedule only after Task persistence succeeds because Schedule identity depends on `taskId`; Save & Publish must persist Task, then Schedule, then publish.
- Schedule definition save must never implicitly enable scheduling. A newly created Schedule remains disabled until the user explicitly enables it in Operations Center.
- An existing Schedule cannot be removed by clearing Cron in the editor; Cron remains required once the Schedule exists. Runtime enable / disable remains an Operations Center action.
- Datasource Select uses `value = datasourceId` and `label = datasourceName`; it must pass the value-label map through `Select.items`.
- Table Select uses a stable composite `tableKey` as value and a human-readable table path as label; it must pass the value-label map through `Select.items`.
- Schema Select may omit `items` when the domain value is intentionally identical to the visible label. Database is not editable in Offline Sync when Datasource already binds it.
- Datasource / Schema / Table are dynamic resource Selects: their popup uses Yak UI Select Search composition with local keyword filtering and an explicit refresh action.
- Datasource Select footer exposes “新增数据源” as a product-owned action and routes to Datasource create; Schema / Table do not invent create actions.
- Static enum Selects such as OFFLINE write mode stay simple and do not add search / refresh / footer without a real option-volume need.
- Task list shows the persisted publication status as 已下线 / 已上线 and may filter by that status.
- Task list is a definition summary surface: show task/version, Source → Target, Schedule definition summary, publication status, updater/update time and actions. Sink write mode is not a list-summary field; it belongs to the selected Execution's `配置快照`. Source / Target use the compact `Datasource Name.Table` form and do not repeat bound database/schema context already implied by the Datasource. Render the sync route as a compact vertical flow: Source on the first row, a lightweight downward connector in the icon column, and Target on the second row; both rows use the same neutral Datasource icon rather than database-specific branding. Runtime metrics such as Last Run / Next Run / Attempt / Retry / readRows / writeRows stay in Operations Center or Task Detail.
- Task list Schedule summary renders only `Cron: <expression>` for scheduled tasks and `手动` when no Cron exists. Time Zone stays in editor/detail surfaces and is not repeated in the compact list summary; the list must not calculate next fire time or own Schedule enable / disable.
- Task list updater resolves current-page `updateBy` values with one batch user lookup; never issue one user request per row. Historical `system` stays SYSTEM.
- Task list fixes the action column on the right through Yak UI Table `fixed: "right"`; product code must not rebuild sticky column CSS.
- Task list keeps five stable action slots: 运行、上线/下线、编辑、详情、删除. `详情` is always visible and opens `/offline-sync/:taskId/detail` inside Data Integration.
- `运行` is a manual shortcut for a PUBLISHED Task and creates a MANUAL Execution through the existing Run API. It is disabled while the Task is UNPUBLISHED or already has a PENDING / RUNNING / RETRY_WAITING Execution.
- UNPUBLISHED Task enables 上线 / 编辑 / 详情 / 删除 while 运行 is disabled. PUBLISHED without an active Execution enables 运行 / 下线 / 详情 and disables 编辑 / 删除. An active Execution disables both 运行 and 下线 while 详情 remains available.
- Stop never executes from the Offline Sync list, editor or Task Detail; users inspect the created Execution from Task Detail after running.
- Direct navigation to an editor for a PUBLISHED Task must not expose an editable form; guide the user back to the list to unpublish first.

Must Not:

- Add configurable scheduler concurrency, misfire or catch-up policies beyond the frozen v1.1 backend contract.
- Add filter SQL, split key, pre/post SQL, resource group or Transform.
- Reimplement JDBC type compatibility in frontend code.
