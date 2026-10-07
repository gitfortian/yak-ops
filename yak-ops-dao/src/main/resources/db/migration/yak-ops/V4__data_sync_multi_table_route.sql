-- Yak Ops v1.3.0 Development Draft Migration
--
-- Data Sync Multi-Table Route persistence foundation.
-- This is a v1.3 development draft and must be reviewed / squashed according to
-- FLYWAY_RULES.md before the v1.3.0 Release Freeze.
--
-- V1 / V2 / V3 are published history and must remain immutable.

CREATE TABLE yak_ops_data_sync_table_route (
    id VARCHAR(64) NOT NULL COMMENT '稳定Route ID，由应用雪花算法生成；v1.2历史单表任务回填时复用Task ID',
    workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作空间ID，是Route业务归属与隔离边界',
    task_id VARCHAR(64) NOT NULL COMMENT '所属Data Sync Task ID，不使用数据库物理外键',
    source_database VARCHAR(128) NULL COMMENT '来源数据库名称，无该层级时为空',
    source_schema VARCHAR(128) NULL COMMENT '来源Schema名称，无该层级时为空',
    source_table VARCHAR(128) NOT NULL COMMENT '来源物理表名称',
    target_database VARCHAR(128) NULL COMMENT '目标数据库名称，无该层级时为空',
    target_schema VARCHAR(128) NULL COMMENT '目标Schema名称，无该层级时为空',
    target_table VARCHAR(128) NOT NULL COMMENT '目标物理表名称',
    auto_create_table TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '目标表不存在时是否允许按当前Route的LogicalTable自动建表：0否，1是',
    mapping_config LONGTEXT NULL COMMENT 'Route级字段映射JSON；NULL表示使用大小写不敏感同名映射',
    sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Task内稳定顺序，从0开始',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_data_sync_table_route_task_order (workspace_id, task_id, sort_order)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='数据同步任务表级Route定义表';

-- v1.2 single-table Task → one stable Route.
-- Reusing the existing Task snowflake ID keeps the backfill deterministic and does
-- not mutate task definitionVersion, historical Execution snapshots or CDC state.
INSERT INTO yak_ops_data_sync_table_route (
    id,
    workspace_id,
    task_id,
    source_database,
    source_schema,
    source_table,
    target_database,
    target_schema,
    target_table,
    auto_create_table,
    mapping_config,
    sort_order,
    create_time,
    update_time,
    create_by,
    update_by
)
SELECT
    task.id,
    task.workspace_id,
    task.id,
    task.source_database,
    task.source_schema,
    task.source_table,
    task.target_database,
    task.target_schema,
    task.target_table,
    task.auto_create_table,
    task.mapping_config,
    0,
    task.create_time,
    task.update_time,
    task.create_by,
    task.update_by
FROM yak_ops_data_sync_task task;
