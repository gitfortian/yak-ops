# Offline Sync Frontend Rules

Scope:

- `yak-ops-ui/apps/web/app/offline-sync/**`
- `yak-ops-ui/apps/web/service/data-sync/**`

## Phase 3 Boundary

The offline product configures task definitions and exposes manual execution instance lifecycle.

Must:

- Reuse saved Datasource resources; never ask for database credentials.
- Use Datasource Catalog API for database / Schema / table discovery.
- Display backend mapping preview as the source of truth.
- Keep mapping read-only and same-name in this phase.
- Disable save while the current mapping is incompatible.
- Keep runtime tuning limited to fetch size, read batch size, write batch size and timeout.
- Use existing Yak UI primitives.
- Keep Task Definition and Task Instance as separate Tabs.
- A manual run creates a new Instance and navigates to its detail page.
- PENDING / RUNNING instances expose Stop; terminal instances expose Detail.
- Poll only while visible instance data contains PENDING / RUNNING records.

Must Not:

- Add scheduler / retry policy before their backend lifecycle exists.
- Add filter SQL, split key, pre/post SQL, resource group or Transform.
- Reimplement JDBC type compatibility in frontend code.
