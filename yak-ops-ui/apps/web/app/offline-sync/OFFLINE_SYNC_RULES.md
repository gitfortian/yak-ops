# Offline Sync Frontend Rules

Scope:

- `yak-ops-ui/apps/web/app/offline-sync/**`
- `yak-ops-ui/apps/web/service/data-sync/**`

## Phase 4 Boundary

The offline product configures task definitions and exposes manual execution instance lifecycle.

Must:

- Reuse saved Datasource resources; never ask for database credentials.
- Keep resource selection and table configuration as separate editor layers: the `数据源` section owns source/target Datasource selectors; `数据来源` and `数据去向` own only Schema (when needed) and table selection.
- Render source and target Datasource cards side by side on wide screens and stack them on smaller screens.
- Do not repeat Datasource Select inside `数据来源` or `数据去向`.
- Treat the saved Datasource connection as the database scope authority. The editor must not expose a second Database selector that can override a bound Datasource database.
- Use Datasource Catalog API for Schema / table discovery inside that Datasource scope.
- Show the bound database / schema as read-only scope context under the Datasource Select.
- Show a Schema Select only when the Datasource itself does not bind a default Schema and Catalog exposes Schemas.
- Display backend mapping preview as the source of truth.
- Keep mapping read-only and same-name in this phase.
- Disable save while the current mapping is incompatible.
- Keep runtime tuning limited to fetch size, read batch size, write batch size and timeout.
- Use existing Yak UI primitives.
- Keep Task Definition and Task Instance as separate Tabs.
- A manual run creates a new Instance and navigates to its detail page.
- PENDING / RUNNING instances expose Stop; terminal instances expose Detail.
- Poll only while visible instance data contains PENDING / RUNNING records.
- Display readRows / writeRows from the persisted Instance; frontend must not estimate progress.
- The editor may offer Save and Save & Run. Save & Run must persist the Task first, then call the normal manual run API and navigate to the created Instance.
- Datasource Select uses `value = datasourceId` and `label = datasourceName`; it must pass the value-label map through `Select.items`.
- Table Select uses a stable composite `tableKey` as value and a human-readable table path as label; it must pass the value-label map through `Select.items`.
- Schema Select may omit `items` when the domain value is intentionally identical to the visible label. Database is not editable in Offline Sync when Datasource already binds it.

Must Not:

- Add scheduler / retry policy before their backend lifecycle exists.
- Add filter SQL, split key, pre/post SQL, resource group or Transform.
- Reimplement JDBC type compatibility in frontend code.
