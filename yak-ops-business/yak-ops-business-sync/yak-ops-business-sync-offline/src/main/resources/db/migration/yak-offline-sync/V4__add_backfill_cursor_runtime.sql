-- Wave 5 expand migration: freeze logical JobSpec on Batch and add durable Task Cursor.
ALTER TABLE yak_offline_batch_execution
    ADD COLUMN logical_job_spec_json LONGTEXT NULL COMMENT 'Batch 创建时冻结的不含凭据逻辑 JobSpec' AFTER config_digest,
    ADD KEY idx_yak_offline_batch_trigger_status (trigger_type, status, id);

-- Wave 2/3/4 Batch already have Attempt 1; copy the frozen logical JobSpec from the earliest Attempt 1.
UPDATE yak_offline_batch_execution b
JOIN (
    SELECT e.batch_id, e.submitted_config
    FROM yak_offline_job_execution e
    JOIN (
        SELECT batch_id, MIN(id) AS first_attempt_id
        FROM yak_offline_job_execution
        WHERE batch_id IS NOT NULL AND attempt_no = 1
        GROUP BY batch_id
    ) first_attempt ON first_attempt.first_attempt_id = e.id
) frozen ON frozen.batch_id = b.id
SET b.logical_job_spec_json = frozen.submitted_config
WHERE b.logical_job_spec_json IS NULL;

CREATE TABLE yak_offline_sync_cursor (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Cursor persistence ID',
    job_definition_id BIGINT NOT NULL COMMENT 'OfflineSyncTask ID',
    cursor_id VARCHAR(128) NOT NULL COMMENT 'Task 内稳定 Cursor identity',
    source_column VARCHAR(256) NOT NULL COMMENT 'Cursor 对应 source column route',
    position_value VARCHAR(1024) NOT NULL COMMENT '当前已提交 Cursor 位置',
    last_succeeded_batch_id BIGINT NULL COMMENT '最近一次推进 Cursor 的 SUCCEEDED Batch ID',
    state_version BIGINT NOT NULL DEFAULT 1 COMMENT 'Cursor CAS version',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_offline_cursor_task_id (job_definition_id, cursor_id),
    KEY idx_yak_offline_cursor_last_batch (last_succeeded_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='离线同步 Task Cursor';
