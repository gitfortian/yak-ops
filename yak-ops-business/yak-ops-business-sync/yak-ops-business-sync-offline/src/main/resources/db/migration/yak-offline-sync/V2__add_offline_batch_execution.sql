-- Wave 1 expand migration: introduce persisted BatchExecution identity without switching runtime paths.
CREATE TABLE yak_offline_batch_execution (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '业务批次 ID',
    job_definition_id BIGINT NOT NULL COMMENT '离线同步任务 ID',
    batch_key VARCHAR(512) NOT NULL COMMENT '任务内稳定业务批次身份',
    trigger_type VARCHAR(32) NOT NULL COMMENT 'MANUAL/SCHEDULE/WORKFLOW/BACKFILL',
    batch_scope_type VARCHAR(32) NOT NULL COMMENT 'FULL_SELECTION/DATA_WINDOW/PARTITION_SCOPE/CURSOR_RANGE',
    batch_scope_value LONGTEXT NOT NULL COMMENT 'BatchScope canonical value',
    batch_scope_fingerprint CHAR(64) NOT NULL COMMENT 'BatchScope SHA-256 fingerprint',
    definition_snapshot_json LONGTEXT NOT NULL COMMENT 'Batch 创建时冻结的 SyncDefinition 快照',
    definition_revision INT NOT NULL COMMENT 'Batch 创建时的定义修订号',
    retry_max_attempts INT NOT NULL COMMENT 'Batch 创建时冻结的最大 Attempt 数',
    retry_backoff_seconds INT NOT NULL COMMENT 'Batch 创建时冻结的 Retry backoff 秒数',
    config_digest CHAR(64) NOT NULL COMMENT '冻结配置摘要',
    status VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/WAITING_RETRY/SUCCEEDED/FAILED/CANCELED/UNKNOWN',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_offline_batch_task_key (job_definition_id, batch_key),
    KEY idx_yak_offline_batch_task_status (job_definition_id, status, id),
    KEY idx_yak_offline_batch_scope (batch_scope_fingerprint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='离线同步业务批次';

ALTER TABLE yak_offline_job_execution
    ADD COLUMN batch_id BIGINT NULL COMMENT '所属业务批次 ID；Wave 1 兼容期允许为空' AFTER job_definition_id,
    ADD KEY idx_yak_offline_execution_batch (batch_id, attempt_no, id);
