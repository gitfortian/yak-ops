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
