-- Yak Ops v1.1.0 Release Migration
--
-- Upgrade path:
--   v1.0.0 / V1__baseline.sql
--       ↓
--   v1.1.0 / V2__v1_1_0.sql
--
-- This release migration squashes the unpublished v1.1.0 draft migrations
-- previously developed as V2 through V5. SQL order and semantics are preserved.

-- ----------------------------------------------------------------
-- Offline Schedule
-- ----------------------------------------------------------------

CREATE TABLE yak_ops_data_sync_schedule (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作空间ID，是同步调度业务归属与隔离边界',
    task_id VARCHAR(64) NOT NULL COMMENT '关联离线同步任务ID，不使用数据库物理外键',
    cron_expression VARCHAR(128) NOT NULL COMMENT 'Quartz Cron表达式',
    time_zone VARCHAR(64) NOT NULL COMMENT 'Cron解释使用的IANA时区ID，例如Asia/Shanghai',
    enabled TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '调度启用状态：0停用，1启用',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_data_sync_schedule_workspace_task (workspace_id, task_id),
    KEY idx_ops_data_sync_schedule_enabled_update (enabled, update_time)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='数据同步任务调度定义表';

-- ----------------------------------------------------------------
-- Execution Retry / Attempt
-- ----------------------------------------------------------------

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

-- ----------------------------------------------------------------
-- Realtime Desired State / Auto Recovery
-- ----------------------------------------------------------------

ALTER TABLE yak_ops_data_sync_task
    ADD COLUMN desired_state TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'REALTIME期望运行状态：0已停止，1运行中；OFFLINE固定为0' AFTER status,
    ADD KEY idx_ops_data_sync_task_realtime_desired (sync_type, status, desired_state, update_time);

UPDATE yak_ops_data_sync_task task
SET task.desired_state = 1
WHERE task.sync_type = 2
  AND task.status = 1
  AND EXISTS (
      SELECT 1
      FROM yak_ops_data_sync_instance instance_row
      WHERE instance_row.workspace_id = task.workspace_id
        AND instance_row.task_id = task.id
        AND instance_row.sync_type = 2
        AND instance_row.status IN (1, 2, 7)
  );

ALTER TABLE yak_ops_data_sync_instance
    MODIFY COLUMN trigger_type TINYINT UNSIGNED NOT NULL COMMENT 'Execution根触发方式：1手动，2调度，3兼容重试，4自动恢复';

-- ----------------------------------------------------------------
-- Execution Product Event
-- ----------------------------------------------------------------

CREATE TABLE yak_ops_data_sync_execution_event (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作空间ID',
    execution_id VARCHAR(64) NOT NULL COMMENT '关联Data Sync Execution根实例ID，不使用数据库物理外键',
    attempt_id VARCHAR(64) NULL COMMENT '关联Attempt ID；Execution级事件为空，不使用数据库物理外键',
    level TINYINT UNSIGNED NOT NULL COMMENT '产品事件级别：1信息，2警告，3错误',
    event_type TINYINT UNSIGNED NOT NULL COMMENT 'Execution生命周期事件类型：1开始执行，2 Attempt开始，3来源准备，4目标准备，5 Attempt成功，6 Attempt失败，7等待重试，8 Execution成功，9 Execution失败，10取消，11丢失，12自动恢复',
    message VARCHAR(1000) NOT NULL COMMENT '面向用户展示的脱敏事件说明',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    PRIMARY KEY (id),
    KEY idx_ops_data_sync_execution_event_workspace_execution (workspace_id, execution_id, create_time, id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='数据同步Execution产品事件表';

