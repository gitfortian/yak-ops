ALTER TABLE yak_ops_data_sync_task
    ADD COLUMN retry_policy VARCHAR(256) NULL COMMENT '执行重试策略JSON，例如maxAttempts与backoffSeconds' AFTER runtime_config;

UPDATE yak_ops_data_sync_task
SET retry_policy = '{"maxAttempts":1,"backoffSeconds":60}'
WHERE retry_policy IS NULL;

ALTER TABLE yak_ops_data_sync_task
    MODIFY COLUMN retry_policy VARCHAR(256) NOT NULL COMMENT '执行重试策略JSON，例如maxAttempts与backoffSeconds';

ALTER TABLE yak_ops_data_sync_instance
    ADD COLUMN max_attempts INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '本次Execution允许的最大Attempt总数，包含首次执行' AFTER trigger_type,
    ADD COLUMN backoff_seconds INT UNSIGNED NOT NULL DEFAULT 60 COMMENT 'Attempt失败后固定等待秒数' AFTER max_attempts,
    ADD COLUMN current_attempt INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '当前或最终Attempt序号，从1开始' AFTER backoff_seconds,
    ADD COLUMN next_retry_time DATETIME(3) NULL COMMENT '处于等待重试状态时的下一次Attempt计划时间' AFTER current_attempt,
    MODIFY COLUMN status TINYINT UNSIGNED NOT NULL COMMENT 'Execution状态：1等待，2运行中，3成功，4失败，5已取消，6丢失，7等待重试';

CREATE TABLE yak_ops_data_sync_attempt (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作空间ID',
    execution_id VARCHAR(64) NOT NULL COMMENT '关联Data Sync Execution根实例ID，不使用数据库物理外键',
    attempt_no INT UNSIGNED NOT NULL COMMENT 'Execution内Attempt序号，从1开始严格递增',
    status TINYINT UNSIGNED NOT NULL COMMENT 'Attempt状态：1等待，2运行中，3成功，4失败，5已取消，6丢失',
    read_rows BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本Attempt累计读取行数',
    write_rows BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本Attempt累计写入行数',
    start_time DATETIME(3) NULL COMMENT '本Attempt实际开始时间',
    finish_time DATETIME(3) NULL COMMENT '本Attempt进入终态的完成时间',
    error_code INT UNSIGNED NULL COMMENT '本Attempt失败时的结构化错误码',
    error_message VARCHAR(1000) NULL COMMENT '本Attempt失败时的脱敏错误信息',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_data_sync_attempt_execution_no (execution_id, attempt_no),
    KEY idx_ops_data_sync_attempt_workspace_execution (workspace_id, execution_id, attempt_no),
    KEY idx_ops_data_sync_attempt_workspace_status (workspace_id, status, update_time)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='数据同步Execution执行尝试历史表';
