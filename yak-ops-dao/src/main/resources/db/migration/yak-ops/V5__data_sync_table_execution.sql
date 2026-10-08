-- Yak Ops v1.3.0 Development Draft Migration
--
-- Data Sync Table Execution persistence foundation.
-- V4 remains a v1.3 development draft; both V4 / V5 must be reviewed and
-- squashed into at most one v1.3 Release Migration before Release Freeze.
--
-- V1 / V2 / V3 are published history and must remain immutable.

CREATE TABLE yak_ops_data_sync_table_execution (
    id VARCHAR(64) NOT NULL COMMENT 'Table Execution主键ID，由应用雪花算法生成',
    workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作空间ID，是表级执行历史的隔离边界',
    execution_id VARCHAR(64) NOT NULL COMMENT '所属Data Sync Root Execution ID，不使用数据库物理外键',
    route_id VARCHAR(64) NOT NULL COMMENT 'Root Execution创建时冻结的稳定Table Route ID',
    route_order INT UNSIGNED NOT NULL COMMENT 'Root Execution内冻结的Route顺序，从0开始',
    status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '表级执行状态：0已规划，1等待，2运行中，3成功，4失败，5已取消，6丢失，7等待重试',
    current_attempt INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '表级当前Attempt序号；0表示尚未进入表级Runtime',
    read_rows BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前或最终表级Attempt读取行数镜像',
    write_rows BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前或最终表级Attempt写入行数镜像',
    start_time DATETIME(3) NULL COMMENT '表级Runtime实际开始时间',
    finish_time DATETIME(3) NULL COMMENT '表级执行进入终态时间',
    error_code INT UNSIGNED NULL COMMENT '表级执行失败时的结构化错误码',
    error_message VARCHAR(1000) NULL COMMENT '表级执行失败时的脱敏错误信息',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_data_sync_table_execution_execution_route (workspace_id, execution_id, route_id),
    UNIQUE KEY uk_ops_data_sync_table_execution_execution_order (workspace_id, execution_id, route_order)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='数据同步Root Execution表级执行记录表';
