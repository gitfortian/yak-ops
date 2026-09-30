# Operations Center Frontend Rules

Scope:

- `yak-ops-ui/apps/web/app/operations/**`
- shared runtime surfaces reused by Operations Center under `yak-ops-ui/apps/web/app/data-sync/**`

## Responsibility Boundary

Operations Center owns cross-Task operational observability. Data Integration owns Task definition / publication plus Task-scoped read-only runtime detail.

The current surfaces are intentionally asymmetric while the dashboard rollout is in progress:

- OFFLINE root is an aggregate Dashboard.
- REALTIME currently retains the existing Task / Instance runtime operations surface.

Must:

- Keep OFFLINE and REALTIME data isolated by `syncType`.
- OFFLINE Dashboard reads only `POST /api/v1/data-sync/operations/dashboard`; do not fetch Instance pages and aggregate them in browser memory.
- OFFLINE range is limited to TODAY / LAST_7_DAYS / LAST_30_DAYS. TODAY renders hourly buckets; 7 / 30 days render daily buckets exactly as supplied by backend.
- OFFLINE Summary Cards show execution count, success count, write volume and average duration. Supporting text may expose success rate, current active Task count, failed / lost counts and abnormal Task count from the same read model.
- OFFLINE charts are Card-wrapped and limited to persisted metrics the backend can prove: read/write volume trend, execution result trend, execution status distribution and FAILED + LOST Task Top 5.
- Do not derive Retry totals by summing Attempt metrics; dashboard readRows / writeRows already follow the backend Execution current/final Attempt mirror semantics.
- Do not invent realtime throughput, events/s, CDC Lag or Checkpoint Lag from cumulative counters. Those require a future persisted Metrics Time Series.
- Reuse the local thin `EChart` integration for ECharts lifecycle: init / setOption / ResizeObserver / dispose. Product charts own option semantics; Yak UI does not depend on ECharts.
- Use Yak UI `Card` only as a neutral visual surface. Card must not learn Data Sync metrics or ECharts options.
- OFFLINE Dashboard is observability-only: it does not render Task definition tables, Instance history Tabs, Run / Stop buttons or Schedule runtime controls.
- Existing OFFLINE Instance detail routes remain compatibility routes, but the Dashboard does not use them as its primary navigation.
- REALTIME currently keeps PUBLISHED Task operations, Start / Stop, Desired State and Instance history behavior until its own dashboard surface replaces that root page.
- Reuse shared Data Sync Execution presentation anywhere an existing compatibility detail route is still rendered.
- Preserve Workspace scoping through the Operations Center AppLayout and backend read model.

Must Not:

- Edit Task definitions in Operations Center.
- Publish / unpublish Tasks in Operations Center.
- Recompute backend dashboard buckets, status zero-fill or failure ranking in frontend code.
- Build a second chart framework or wrap the entire ECharts API into Yak UI props.
- Put runtime history inside the Task Editor; Task Detail is a separate read-only surface.
