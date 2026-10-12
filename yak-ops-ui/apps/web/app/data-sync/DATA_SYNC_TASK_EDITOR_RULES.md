# Data Sync Task Editor Rules

Status: Active
Scope: `app/data-sync/task-editor.tsx` and its editor-local components.

Current behavior is defined by the editor, `service/data-sync/definition.ts` and the backend Task Definition API. Shared UI conventions follow [Frontend Rules](../../../../FRONTEND_RULES.md).

## Ownership

- `DataSyncTaskEditorPage` receives an explicit `syncType` and shares datasource selection, Catalog queries, single-table selection and save/publish behavior between OFFLINE and REALTIME.
- Task lists own publication, manual execution and scheduling controls. Task Detail owns Task-scoped execution history; Operations Center owns cross-Task dashboards.

## Task Definition

- Each Task selects one existing Source table and one existing Target table. Source and Target are identified by saved Datasource IDs and Catalog-resolved database, schema and table names.
- Datasource selection uses the shared searchable Select with type icons, refresh and a create-datasource action. A Datasource-bound database/schema is authoritative; an unbound schema uses Catalog options. Source/Target table Selects search and refresh real Catalog tables.
- Changing a Datasource or Schema resets the dependent table selection. Saving requires a task name, Source Datasource/table and a Catalog-resolved Target Datasource/table.
- REALTIME accepts MySQL Sources and MySQL/PostgreSQL/Oracle Targets. The Service serializes REALTIME as `MYSQL_CDC` Source and `UPSERT` JDBC Sink; OFFLINE uses JDBC Source and JDBC Sink with the effective saved write mode.
- The domain Service constructs schema v2 JSON `parameters` with `source`, `transform: []` and `sink`. The editor sends product selection fields through `DataSyncTaskSavePayload` and delegates schema serialization to the Service.
- Task Definitions contain product synchronization parameters. Runtime configuration, retry policy, Source offsets, Schema History and physical Connector tuning remain outside the editor.

## Save and Publish

- Only UNPUBLISHED Tasks are editable. Direct navigation to a PUBLISHED Task presents its status and a return path.
- An update containing only task name or remark omits `parameters` and preserves the definition version. Changes to executable fields submit the complete single-table definition.
- Save writes the definition; Save & Publish writes the definition, saves an applicable OFFLINE Schedule and publishes the Task. Neither action starts execution or enables scheduling implicitly.
- A failed Schedule save stops publication and retains the saved Task. A failed Publish leaves the saved Task UNPUBLISHED with a route back to editing.

## OFFLINE Schedule

- Cron and Time Zone are optional Schedule configuration. A Task without a Schedule is manual-only; a saved Schedule is controlled separately by the Task list.
- The editor uses `CronSchedulerPicker` and a Time Zone Select defaulting to `Asia/Shanghai`, retaining the saved IANA zone. The backend Schedule Preview API supplies the next five fire times.
- A saved Schedule requires valid Cron and Time Zone values; clearing an existing Schedule through an empty Cron is not a delete action.

## Layout and Feedback

- Sections are `基本信息`, `数据源`, `数据来源`, `数据去向`, plus `调度配置` for OFFLINE. Each section uses Yak UI `CollapseSection` and a white bordered content surface.
- OFFLINE uses a fixed PageHeader, local content scroll and a fixed right-side `EditorAnchorStepper`. REALTIME uses viewport scrolling and the same stepper without a Schedule item. Narrow layouts hide the stepper.
- Use Yak UI fields, Selects, Buttons and Alerts. Show only actionable validation, execution prerequisites and data/state risks as required by [UI Copy Minimalism](../../../../FRONTEND_RULES.md#ui-copy-minimalism).

## Verification

- Validate single-table Catalog search/refresh and saved-value echo, Source/Target changes, UNPUBLISHED editing, metadata-only save, Schedule/Publish partial failures and fixed-header/stepper behavior in the real editor.
- Runtime results and backend acceptance must be verified separately; Task definition save/publish is not execution evidence.
