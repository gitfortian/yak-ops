-- Yak Ops v1.12.0 incremental schema.
-- Asset governance (FYOIP-16): scan runs and their per-rule results, the single current Issue per
-- (rule, target), the append-only event history of that Issue, and the daily health snapshots the
-- Workspace score is read from. The migration is additive: no existing table, column or row changes.

CREATE TABLE yak_ops_governance_scan_run (
  id VARCHAR(64) NOT NULL COMMENT 'Application-generated run identity',
  workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the run',
  trigger_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT 'How the run was requested: SCHEDULED or MANUAL',
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT 'PENDING, RUNNING, SUCCEEDED, PARTIAL, FAILED or SKIPPED',
  active_scope TINYINT NULL
    COMMENT 'Holds 1 while pending or running; NULL after any terminal state',
  rule_count INT NOT NULL DEFAULT 0 COMMENT 'Rules the run intended to evaluate',
  succeeded_rule_count INT NOT NULL DEFAULT 0 COMMENT 'Rules that completed a full enumeration',
  failed_rule_count INT NOT NULL DEFAULT 0 COMMENT 'Rules that threw, timed out or exceeded the budget',
  issue_created_count INT NOT NULL DEFAULT 0 COMMENT 'Issues first created by this run',
  issue_reopened_count INT NOT NULL DEFAULT 0 COMMENT 'Issues reopened by this run',
  issue_resolved_count INT NOT NULL DEFAULT 0 COMMENT 'Issues resolved or auto-resolved by this run',
  reason_code VARCHAR(64) NULL
    COMMENT 'Safe machine code such as DATA_MAP_BUSY or APP_RESTART; never an exception message',
  start_time DATETIME(3) NULL COMMENT 'When the worker claimed the run',
  finish_time DATETIME(3) NULL COMMENT 'Terminal time of the run',
  create_time DATETIME(3) NOT NULL COMMENT 'Submission timestamp',
  create_by VARCHAR(64) NOT NULL COMMENT 'Authenticated submitter, or system for the schedule',
  PRIMARY KEY (id),
  UNIQUE KEY uk_governance_scan_workspace (workspace_id, id),
  UNIQUE KEY uk_governance_scan_active (workspace_id, active_scope),
  KEY idx_governance_scan_time (workspace_id, create_time),
  CONSTRAINT ck_governance_scan_status
    CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'SKIPPED')),
  CONSTRAINT ck_governance_scan_active CHECK (
    (status IN ('PENDING', 'RUNNING') AND active_scope = 1) OR
    (status IN ('SUCCEEDED', 'PARTIAL', 'FAILED', 'SKIPPED') AND active_scope IS NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='One governance scan run; the unique active key allows one live run per Workspace';

CREATE TABLE yak_ops_governance_rule_result (
  id VARCHAR(64) NOT NULL COMMENT 'Application-generated rule result identity',
  workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the run',
  scan_id VARCHAR(64) NOT NULL COMMENT 'Run this result belongs to',
  rule_code VARCHAR(64) NOT NULL COMMENT 'Stable rule code compiled into the product',
  rule_version INT NOT NULL COMMENT 'Rule version the run evaluated',
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT 'RUNNING, SUCCEEDED or FAILED',
  finding_count INT NOT NULL DEFAULT 0 COMMENT 'Candidates the rule enumerated',
  issue_created_count INT NOT NULL DEFAULT 0,
  issue_reopened_count INT NOT NULL DEFAULT 0,
  issue_resolved_count INT NOT NULL DEFAULT 0,
  reason_code VARCHAR(64) NULL COMMENT 'Safe machine code; never an exception message',
  start_time DATETIME(3) NULL,
  finish_time DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_governance_rule_result (scan_id, rule_code),
  KEY idx_governance_rule_result_time (workspace_id, rule_code, start_time),
  CONSTRAINT ck_governance_rule_result_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
  CONSTRAINT fk_governance_rule_result_scan FOREIGN KEY (scan_id)
    REFERENCES yak_ops_governance_scan_run (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Per-rule outcome of one governance scan run';

CREATE TABLE yak_ops_governance_issue (
  id VARCHAR(64) NOT NULL COMMENT 'Application-generated Issue identity, stable across reopenings',
  workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the Issue',
  rule_code VARCHAR(64) NOT NULL COMMENT 'Rule that detected this target',
  rule_version INT NOT NULL COMMENT 'Rule version of the latest detection',
  dimension_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT 'DEV or COMPUTE; the dimension the Issue is scored in',
  severity VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT 'HIGH, MEDIUM or LOW; fixed by the rule',
  target_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT 'DATA_MAP_TABLE or TASK_DEFINITION',
  target_id VARCHAR(64) NOT NULL COMMENT 'Identity of the governed object, resolved through its owner',
  target_name VARCHAR(512) NOT NULL COMMENT 'Display name captured at detection, refreshed on re-detection',
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT 'OPEN, HANDLING, RESOLVED or IGNORED',
  owner_id VARCHAR(64) NULL COMMENT 'Assigned Workspace member while in HANDLING',
  ignore_until DATETIME(3) NULL COMMENT 'Mandatory while IGNORED; the next scan reopens an expired window',
  resolved_at DATETIME(3) NULL,
  resolved_by VARCHAR(64) NULL,
  resolution VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'USER or AUTO',
  observed_value DECIMAL(18, 2) NULL COMMENT 'Numeric evidence; the frontend owns the wording',
  threshold_value DECIMAL(18, 2) NULL COMMENT 'Threshold the observation was compared with',
  sample_count INT NULL COMMENT 'Samples the observation was computed from',
  window_days INT NULL COMMENT 'Days the observation looked back over; 0 for an instantaneous fact',
  first_detected_at DATETIME(3) NOT NULL COMMENT 'When this target first entered governance',
  last_detected_at DATETIME(3) NOT NULL COMMENT 'When the condition was last observed',
  last_scan_id VARCHAR(64) NULL COMMENT 'Run that last observed this target; null before the first scan',
  revision BIGINT NOT NULL DEFAULT 1 COMMENT 'Optimistic guard for manual transitions',
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  create_by VARCHAR(64) NOT NULL,
  update_by VARCHAR(64) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_governance_issue_workspace (workspace_id, id),
  UNIQUE KEY uk_governance_issue_target (workspace_id, rule_code, target_type, target_id),
  KEY idx_governance_issue_board (workspace_id, status, severity, dimension_code),
  KEY idx_governance_issue_owner (workspace_id, owner_id, status, update_time),
  KEY idx_governance_issue_scan (workspace_id, rule_code, last_scan_id),
  KEY idx_governance_issue_target_lookup (workspace_id, target_type, target_id),
  CONSTRAINT ck_governance_issue_status CHECK (status IN ('OPEN', 'HANDLING', 'RESOLVED', 'IGNORED')),
  CONSTRAINT ck_governance_issue_severity CHECK (severity IN ('HIGH', 'MEDIUM', 'LOW')),
  CONSTRAINT ck_governance_issue_dimension CHECK (dimension_code IN ('DEV', 'COMPUTE')),
  CONSTRAINT ck_governance_issue_revision CHECK (revision > 0),
  CONSTRAINT ck_governance_issue_ignored CHECK (status <> 'IGNORED' OR ignore_until IS NOT NULL),
  CONSTRAINT ck_governance_issue_resolved CHECK (
    status <> 'RESOLVED' OR (resolved_at IS NOT NULL AND resolution IS NOT NULL AND resolved_by IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Single current governance Issue per (workspace, rule, target)';

CREATE TABLE yak_ops_governance_issue_event (
  id VARCHAR(64) NOT NULL COMMENT 'Application-generated event identity',
  workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace owning the Issue',
  issue_id VARCHAR(64) NOT NULL COMMENT 'Issue this transition belongs to',
  event_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT 'CREATED, REOPENED, ASSIGNED, IGNORED, UNIGNORED, RESOLVED or AUTO_RESOLVED',
  from_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Status before the transition',
  to_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Status after the transition',
  owner_id VARCHAR(64) NULL COMMENT 'Owner carried by an assignment event',
  ignore_until DATETIME(3) NULL COMMENT 'Window carried by an ignore event',
  note VARCHAR(500) NULL COMMENT 'Operator note; never an exception message',
  actor_id VARCHAR(64) NOT NULL COMMENT 'Member who acted, or system for a scan transition',
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_governance_issue_event (workspace_id, issue_id, create_time),
  CONSTRAINT ck_governance_issue_event_type CHECK (event_type IN
    ('CREATED', 'REOPENED', 'ASSIGNED', 'IGNORED', 'UNIGNORED', 'RESOLVED', 'AUTO_RESOLVED')),
  CONSTRAINT fk_governance_issue_event_issue FOREIGN KEY (issue_id)
    REFERENCES yak_ops_governance_issue (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only transition history of a governance Issue';

CREATE TABLE yak_ops_governance_health_snapshot (
  id VARCHAR(64) NOT NULL COMMENT 'Application-generated snapshot identity',
  workspace_id VARCHAR(64) NOT NULL COMMENT 'Workspace the score belongs to',
  dimension_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT 'DEV, COMPUTE or OVERALL',
  snapshot_date DATE NOT NULL COMMENT 'UTC day the score describes',
  score DECIMAL(5, 2) NOT NULL COMMENT 'Score after the documented curve and clamp',
  base_count INT NOT NULL COMMENT 'Eligible assets the rate was computed over',
  weighted_issue_count DECIMAL(10, 2) NOT NULL COMMENT 'Sum of OPEN and HANDLING issue weights',
  issue_count INT NOT NULL DEFAULT 0,
  high_count INT NOT NULL DEFAULT 0,
  medium_count INT NOT NULL DEFAULT 0,
  low_count INT NOT NULL DEFAULT 0,
  evaluated_dimension_count TINYINT NOT NULL COMMENT '1 for a dimension row, 2 for OVERALL',
  formula_version VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT 'Score formula the row was written with',
  scan_id VARCHAR(64) NOT NULL COMMENT 'Run that produced the snapshot',
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_governance_health_day (workspace_id, dimension_code, snapshot_date),
  KEY idx_governance_health_date (workspace_id, snapshot_date),
  CONSTRAINT ck_governance_health_score CHECK (score >= 0 AND score <= 100),
  CONSTRAINT ck_governance_health_base CHECK (base_count > 0),
  CONSTRAINT ck_governance_health_dimension CHECK (dimension_code IN ('DEV', 'COMPUTE', 'OVERALL')),
  CONSTRAINT ck_governance_health_coverage CHECK (evaluated_dimension_count IN (1, 2))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Daily governance score per dimension, replaced as a set by the run that writes it';
