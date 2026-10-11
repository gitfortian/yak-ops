# Task Plugin Rules

Status: Active — Definition configuration and validation only.

- Register stable uppercase-underscore task types with ServiceLoader. No closed TaskType enum.
- `TaskPluginRegistry` rejects unknown/duplicate/mismatched types and invokes parsing and local validation.
- Definition writes call `validateDefinition`, which passes a trusted `TaskValidationContext` to normalize and validate physical resources.
- The context supplies Workspace-scoped read-only Datasource metadata and physical `TableSchema`. Never expose credentials or mutable DAO state.
- `SyncPlugin` owns OFFLINE/REALTIME parameters, datasource type compatibility and source/target Schema checks.
- Plugin parameters must not include passwords, secrets, tokens, credentials or runtime connection objects.
- Plugin code does not depend on Boot, Business, DAO, YakFlow Runtime or Quartz. It does not persist or execute tasks.
- New task types provide only actual parameter behaviors; do not add speculative execution and Workflow SPIs.
