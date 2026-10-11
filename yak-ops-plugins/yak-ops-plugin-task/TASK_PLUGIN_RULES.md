# Task Plugin Rules

Status: Active — Definition parameters and validation only.

- Use ServiceLoader to register stable Task plugin types. Registry rejects unknown, duplicate and mismatched types.
- JSON is the default parameter input format. Explicit HOCON input may be handled by plugins such as DATA_SYNC; canonical storage remains JSON.
- Task Business owns Workspace, identity, versions and publication. The plugin provides typed parameters and metadata-only validation, not persistence or execution.
- DATA_SYNC parameters are organized under `source/`, `sink/`, with one type-specific Parameters class per supported stage. Do not flatten JDBC, MySQL CDC or future Kafka/Doris options into a shared config package.
- HOCON parsing and formatting belong exclusively to `datasync/hocon`; avoid generic Codec, Processor and Manager aliases. Do not parse user input through local files, remote URLs, includes or environment substitutions.
- Only use Workspace-checked, read-only `TaskValidationContext`. Do not expose passwords, tokens, JDBC URLs, source connection objects or raw credentials in Definition parameters or log messages.
- Source JDBC is bounded; Source MYSQL_CDC is continuous and requires UPSERT sink semantics with matching primary keys.
- DATA_SYNC canonical v2 has one Source, empty Transform and one Sink; runtime tuning and retries are not part of the product parameters.
- YakFlow Core/Runtime/Connectors exclusively own physical execution options; do not replicate their Configuration or source/sink writer classes in the Task Plugin.
- Do not add a new Client module or an execution/Workflow SPI solely to parse HOCON. Existing PipelineExecutor and JobClient are the future submission boundary.
- Each new task type or stage must have tests for unknown options, secrets and parameter-schema compatibility.
