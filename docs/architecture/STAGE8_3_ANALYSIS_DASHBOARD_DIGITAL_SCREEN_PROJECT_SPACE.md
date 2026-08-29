# Stage 8.3: Analysis / Dashboard / Digital Screen Project Space

## Scope

This stage moves the three BI definition planes to `PROJECT_REQUIRED` management semantics:

- `yak_analysis` is a `PROJECT_ROOT`; its Dataset reference must resolve in the current Project.
- `yak_dashboard` is a `PROJECT_ROOT`; DashboardVersion, Widget, Filter, Binding and Interaction
  inherit ownership through `dashboard_id`.
- `yak_digital_screen` is a `PROJECT_ROOT`; immutable publication versions inherit ownership
  through `screen_id`.

The server derives ownership only from trusted `CurrentProject`. Request DTOs do not own or
override `projectId`, and cross-Project IDs fail closed as not found in the current workspace.

## Migration rules

All migrations follow Expand -> deterministic Backfill -> Contract and never use Project `0`,
Project `1`, the first Project, or another guessed default.

### Analysis

`yak_analysis.project_id` is deterministically copied from its referenced, already-contracted
`yak_dataset.project_id`. Orphan Analysis rows stay `NULL`, so the subsequent `NOT NULL` contract
blocks the upgrade.

### Dashboard

Historical Dashboard ownership is accepted only when every explicit reference resolves and all
references agree on one Project. Evidence includes:

- `yak_dashboard_version.active_dataset_id`;
- reusable `yak_dashboard_widget.analysis_id`;
- `inline_analysis_json.datasetId` when present.

Dashboards with no evidence, unresolved references or mixed-Project evidence remain `NULL` and
block the contract migration. This is deliberate: an empty Dashboard has no trustworthy historic
owner that can be inferred from the current schema.

### Digital Screen

Template bindings are opaque JSON and do not provide one stable ownership contract. The expand
migration therefore performs no automatic backfill. Existing installations must explicitly assign
historical rows after inspecting business ownership; the `NOT NULL` migration intentionally fails
until that work is complete.

Useful preflight queries:

```sql
SELECT id, dataset_id FROM yak_analysis WHERE project_id IS NULL;
SELECT id, name FROM yak_dashboard WHERE project_id IS NULL;
SELECT id, name, template_id FROM yak_digital_screen WHERE project_id IS NULL;
```

No migration in this stage updates those rows to a hard-coded Project.

## Runtime isolation

- Analysis CRUD always includes `project_id` and its Dataset gateway uses Dataset's scoped reader.
- Dashboard root CRUD, overview aggregation and Analysis-reference guards include `project_id`.
- Dashboard child reads and writes first prove that the parent Dashboard belongs to the current
  Project.
- Active Dataset, reusable Analysis and inline Analysis Dataset references are validated through
  owner-defined gateways in the current Project.
- Digital Screen root CRUD includes `project_id`; version reads prove the owning Screen before
  returning inherited rows.

Analysis and Dashboard after-commit lineage events freeze `projectId` in the event payload and use
`ProjectContextScope` before reading source state or updating derived lineage. They never depend on
an HTTP ThreadLocal surviving beyond the request boundary.

## HTTP rollout

The following management prefixes are `PROJECT_REQUIRED` and receive
`X-YAK-SECURITY-PROJECT-ID` from the shared console request interceptor:

- `/api/v1/analyses`
- `/api/v1/dashboards`
- `/api/v1/digital-screens`

This stage does not introduce a new anonymous Digital Screen runtime route. The existing published
snapshot endpoints remain part of the authenticated, project-owned management API.
