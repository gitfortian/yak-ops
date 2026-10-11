# Task Plugin Contract

Status: Active — Task Definition parameter registration, parsing and validation.

## Boundary

`TaskPluginFactory` and `TaskPluginRegistry` discover canonical plugin types via ServiceLoader.
`TaskPlugin` owns local parameter parsing, normalization and type-specific definition validation.
`TaskValidationContext` is a narrow, Workspace-scoped, read-only metadata capability supplied
by Boot and backed by Datasource Business. Plugins receive no DAO, HTTP objects or secrets.

DefinitionService -> TaskPluginRegistry -> TaskPlugin -> read-only validation context.

The only built-in task type today is `DATA_SYNC`. `SyncParameters` describes OFFLINE or REALTIME
source and target tables, write mode and optional policy JSON. `SyncValidator` checks supported
datasource types, source/target column compatibility and required primary keys.

The Task Business layer owns definition identity, version persistence, publication state and
all transactions. Plugins never create Definition records or manage Task Instances.

## Future Workflow boundary

A future WorkflowNode points to `(taskDefinitionId, taskDefinitionVersion)`.
One Task Definition may be referenced multiple times. Workflow nodes have independent node IDs;
editing a Task Definition does not silently advance versions inside published workflows.

No Workflow definition, instance, Task execution, Quartz scheduler, Worker or YakFlow
execution adapter is implemented in this reset. Do not add placeholder SPIs.
