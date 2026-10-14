CREATE TABLE yak_ops_task_cursor (
  workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning this incremental state',
  definition_id VARCHAR(64) NOT NULL COMMENT 'Task definition owning this table cursor',
  table_key CHAR(64) NOT NULL COMMENT 'Stable SHA-256 of explicit source and target table identity',
  binding CHAR(64) NOT NULL COMMENT 'SHA-256 of all incremental interpretation and routing options',
  cursor_value VARCHAR(128) NULL COMMENT 'Committed position; null until a successful first batch',
  pending_value VARCHAR(128) NULL COMMENT 'Frozen next position reused when an earlier run fails',
  initialized TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether an initial batch has successfully committed',
  revision BIGINT NOT NULL DEFAULT 0 COMMENT 'Monotonic optimistic progress revision',
  PRIMARY KEY (workspace_id, definition_id, table_key)
) COMMENT='Durable table-granular offline incremental progress';

CREATE TABLE yak_ops_task_cursor_batch (
  workspace_id VARCHAR(64) NOT NULL COMMENT 'Owning Workspace',
  instance_id VARCHAR(64) NOT NULL COMMENT 'Task Instance whose snapshot is immutable',
  table_key CHAR(64) NOT NULL COMMENT 'Source-to-target table identity digest',
  binding CHAR(64) NOT NULL COMMENT 'Frozen incremental route configuration digest',
  mode VARCHAR(16) NOT NULL COMMENT 'Incremental comparison mode',
  column_name VARCHAR(128) NOT NULL COMMENT 'Validated source column',
  initial_value VARCHAR(128) NULL COMMENT 'User-configured first cursor if applicable',
  lower_value VARCHAR(128) NULL COMMENT 'Committed cursor when this batch was frozen',
  upper_value VARCHAR(128) NOT NULL COMMENT 'Frozen next cursor for deterministic retry',
  allow_initial_load TINYINT(1) NOT NULL COMMENT 'Whether an initial full scan is configured',
  initial_load TINYINT(1) NOT NULL COMMENT 'Whether this Instance physically runs a full initial scan',
  expected_version BIGINT NOT NULL COMMENT 'Cursor revision expected when result commits',
  PRIMARY KEY (workspace_id, instance_id, table_key)
) COMMENT='Immutable per-Instance incremental window and progress candidate';
