# Offline Sync Frontend Rules

Scope:

- `yak-ops-ui/apps/web/app/offline-sync/**`
- OFFLINE mode of `yak-ops-ui/apps/web/app/data-sync/task-editor.tsx`

## Phase 4 Boundary

The offline product owns Task definition and publication configuration. Operations Center owns manual execution, Stop and Instance runtime presentation.

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
- Show a Yak UI `Alert` only when `splitSize` is configured: explain that split reads do not guarantee one table-wide snapshot point and may observe different source states while the source table is changing.
- Use existing Yak UI primitives.
- Keep the Offline Sync page definition-focused; do not expose a Task Instance Tab there.
- Route historical Instance access to Operations Center with the current Task filter.
- Operations Center owns manual Run, Stop, Instance list/detail and runtime polling.
- A manual Run in Operations Center creates a new Instance and navigates to the Operations Center Instance detail page.
- Display readRows / writeRows only from the persisted Instance; frontend must not estimate progress.
- The editor exposes Save and Save & Publish. Save persists an UNPUBLISHED Task; Save & Publish explicitly composes save then publish and returns to the Task list. Run is an Operations Center action for PUBLISHED Tasks.
- Datasource Select uses `value = datasourceId` and `label = datasourceName`; it must pass the value-label map through `Select.items`.
- Table Select uses a stable composite `tableKey` as value and a human-readable table path as label; it must pass the value-label map through `Select.items`.
- Schema Select may omit `items` when the domain value is intentionally identical to the visible label. Database is not editable in Offline Sync when Datasource already binds it.
- Datasource / Schema / Table are dynamic resource Selects: their popup uses Yak UI Select Search composition with local keyword filtering and an explicit refresh action.
- Datasource Select footer exposes “新增数据源” as a product-owned action and routes to Datasource create; Schema / Table do not invent create actions.
- Static enum Selects such as OFFLINE write mode stay simple and do not add search / refresh / footer without a real option-volume need.
- Task list shows the persisted publication status as 已下线 / 已上线 and may filter by that status.
- UNPUBLISHED Task definition actions are 上线 / 编辑 / 实例 / 删除.
- PUBLISHED idle Task definition actions are 下线 / 实例.
- PUBLISHED Task with a PENDING / RUNNING Instance exposes only 实例 on the definition page; 下线 is unavailable until the active Instance ends.
- The 实例 action always enters Operations Center. Run / Stop never execute from the Offline Sync definition page.
- Direct navigation to an editor for a PUBLISHED Task must not expose an editable form; guide the user back to the list to unpublish first.

Must Not:

- Add scheduler / retry policy before their backend lifecycle exists.
- Add filter SQL, split key, pre/post SQL, resource group or Transform.
- Reimplement JDBC type compatibility in frontend code.
