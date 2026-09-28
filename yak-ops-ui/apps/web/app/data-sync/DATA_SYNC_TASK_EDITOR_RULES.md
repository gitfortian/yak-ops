# Data Sync Task Editor Rules

Scope:

- `yak-ops-ui/apps/web/app/data-sync/**`

Depends On:

- `/yak-ops-ui/FRONTEND_RULES.md`
- `/yak-ops-ui/apps/web/APP_RULES.md`
- `/yak-ops-ui/apps/web/FORM_RULES.md`

## Ownership

`app/data-sync` owns only editor behavior genuinely shared by OFFLINE and REALTIME Task configuration.

Shared:

- basic Task information.
- Datasource resource selection.
- Datasource-bound database/schema scope presentation.
- Catalog schema/table discovery.
- automatic same-name mapping preview.
- compatible field mapping presentation.
- common page/header/anchor layout.
- save/update HTTP flow.

Mode-specific:

- OFFLINE runtime config.
- REALTIME runtime config.
- REALTIME Source/Target type restrictions.
- product wording and return route.
- save-and-run post action.

## Must

- Keep `DataSyncTaskEditorPage` parameterized by `syncType`; do not branch by URL pathname.
- Reuse the same Catalog and mapping implementation for OFFLINE and REALTIME.
- Filter REALTIME Source Datasources to MySQL.
- Filter REALTIME Target Datasources to MySQL / PostgreSQL / Oracle.
- Send `runtimeConfig` only for OFFLINE.
- Send `realtimeConfig` only for REALTIME.
- Keep REALTIME first-run semantics visible: initial snapshot followed by MySQL Binlog.
- Keep all Datasource values as IDs and render human-readable names through `Select.items`.
- Keep the backend mapping preview as source of truth.

## Must Not

- Copy the complete Offline Editor into `app/realtime-sync`.
- Put Instance list/detail or runtime dashboard behavior in the shared Task Editor.
- Reimplement JDBC type compatibility.
- Expose Debezium state directory, offset, schema-history or MySQL serverId.
