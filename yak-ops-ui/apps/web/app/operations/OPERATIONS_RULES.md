# Operations Center Frontend Rules

Scope:

- `yak-ops-ui/apps/web/app/operations/**`
- shared runtime surfaces reused by Operations Center under `yak-ops-ui/apps/web/app/data-sync/**`

## Responsibility Boundary

Operations Center owns Data Sync execution commands, runtime operations and cross-Task visibility. Data Integration owns Task definition / publication plus Task-scoped read-only runtime detail.

Must:

- Keep OFFLINE and REALTIME operations separated by `syncType`.
- List only `PUBLISHED` Tasks in the executable Task view.
- OFFLINE idle Tasks expose Run; REALTIME idle Tasks expose Start.
- PENDING / RUNNING / RETRY_WAITING Tasks expose Stop instead of a second Run / Start action.
- Run / Start creates an Instance and navigates to the Operations Center Instance detail route.
- Stop cancels only the active Instance; it must not unpublish the Task.
- Keep Task execution and Instance history as separate Tabs inside the Operations Center Task page.
- The Instance Tab may be filtered by `taskId`; clearing the filter keeps the user inside Operations Center.
- Reuse shared Data Sync execution presentation across Operations Center and Data Integration Task Detail; do not fork OFFLINE / REALTIME runtime implementations.
- Poll active Instance state only while active execution exists.
- Task operations table must surface automation runtime, not only a binary idle/running state: Last Run, Next Run, Attempt progress, Retry Waiting, and trigger source belong here.
- OFFLINE automation state comes from persisted Schedule + Scheduler runtime `nextFireTime`; frontend must not calculate Quartz Cron next-fire timestamps.
- Operations Center owns OFFLINE Schedule enable / disable for PUBLISHED Tasks. Show the action only when a persisted Schedule exists, refresh the backend read model after mutation, and never create or edit Cron / Time Zone here.
- REALTIME must present Desired State separately from actual Execution state. `desiredState=RUNNING` with no active Execution is an observable mismatch, not “idle”.
- AUTO_RECOVERY is an Execution root trigger and must be labeled as automatic recovery rather than generic retry.
- Execution list remains one row per Execution. Attempt history belongs in Execution detail and must not be flattened into the Instance list.
- Preserve Workspace scoping through the Operations Center AppLayout.
- Data Integration Task lists use `详情` to stay inside the current product and open Task-scoped Execution history; Run / Start / Stop remain outside that detail surface.
- Legacy Data Integration Instance detail routes resolve the Instance and redirect to the corresponding Data Integration Task Detail while preserving the selected Execution.

Must Not:

- Edit Task definitions in Operations Center.
- Publish / unpublish Tasks in Operations Center.
- Duplicate backend lifecycle validation in frontend code.
- Put runtime history inside the Task Editor; Task Detail is a separate read-only surface.
- Create separate OFFLINE and REALTIME copies of generic task-operation or instance-runtime components.
