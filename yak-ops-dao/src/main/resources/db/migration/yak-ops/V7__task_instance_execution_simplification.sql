-- An Instance owns its one physical Job submission and durable dispatch fence.
ALTER TABLE yak_ops_task_instance
  ADD COLUMN owner_incarnation VARCHAR(128) NULL COMMENT 'Embedded dispatcher that owns launch' AFTER state_version,
  ADD COLUMN dispatch_token VARCHAR(128) NULL COMMENT 'Opaque launch ownership token' AFTER owner_incarnation,
  ADD COLUMN runtime_engine VARCHAR(32) NULL COMMENT 'Submitted execution engine' AFTER dispatch_token,
  ADD COLUMN runtime_id VARCHAR(128) NULL COMMENT 'Engine-assigned Job identifier' AFTER runtime_engine;

-- Preserve existing runtime ownership, Job IDs and observed lifecycle timestamps.
UPDATE yak_ops_task_instance i
JOIN yak_ops_task_attempt a
  ON a.workspace_id = i.workspace_id AND a.instance_id = i.id AND a.attempt_number = 1
SET i.owner_incarnation = a.owner_incarnation,
    i.dispatch_token = a.dispatch_token,
    i.runtime_engine = a.runtime_engine,
    i.runtime_id = a.runtime_id,
    i.status = CASE
      WHEN i.status = 'SUBMITTED' AND a.status IN ('LAUNCHING', 'RUNNING') THEN a.status
      ELSE i.status
    END,
    i.start_time = COALESCE(i.start_time, a.start_time),
    i.end_time = COALESCE(i.end_time, a.end_time),
    i.failure_code = COALESCE(i.failure_code, a.failure_code),
    i.state_version = i.state_version + 1;

CREATE INDEX idx_ops_task_instance_owner
  ON yak_ops_task_instance (status, owner_incarnation);

DROP TABLE yak_ops_task_attempt;
