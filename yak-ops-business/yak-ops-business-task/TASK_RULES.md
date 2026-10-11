# Task Business Rules

Status: Active — Definition-only backend.

- `definition.DefinitionService` is the only public Task Business entrypoint.
- Stable Task Definition ID, Workspace ownership, publication and immutable versions are owned by the Task Business transaction.
- Every command and query requires a trusted `WorkspaceContext`. Current version queries must match the same Workspace and Definition ID.
- Version increments only when normalized executable parameters change; metadata and publication edits retain the version.
- Publishing validates the frozen current parameters through the plugin again.
- Editing or deleting a published definition is rejected. Delete is logical, retaining immutable versions.
- Plugins validate parameters and physical resources through read-only context, but never write Task Definition or version rows.
- Controllers only inject the Task Business interface. The Data Sync HTTP projection is not a second Service.
- No Instance, Attempt, Scheduler, Quartz, Metrics, Log, Workflow or execution abstractions exist in this reset.
- See [Task Definition Contract](../../docs/capabilities/task-definition.md).
