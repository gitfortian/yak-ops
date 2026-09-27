# Offline Sync Frontend Rules

Scope:
- `yak-ops-ui/apps/web/app/offline-sync/**`
- `yak-ops-ui/apps/web/service/data-sync/**`

## Phase 2 Boundary

The offline editor configures and persists task definitions only.

Must:
- Reuse saved Datasource resources; never ask for database credentials.
- Use Datasource Catalog API for database / Schema / table discovery.
- Display backend mapping preview as the source of truth.
- Keep mapping read-only and same-name in this phase.
- Disable save while the current mapping is incompatible.
- Keep runtime tuning limited to fetch size, read batch size, write batch size and timeout.
- Use existing Yak UI primitives.

Must Not:
- Add run / stop / retry controls before execution lifecycle exists.
- Add filter SQL, split key, pre/post SQL, resource group or Transform.
- Reimplement JDBC type compatibility in frontend code.
