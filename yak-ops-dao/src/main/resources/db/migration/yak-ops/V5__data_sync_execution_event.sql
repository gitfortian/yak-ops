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
