-- v1.3 draft: destructive definition-only reset after immutable V1-V3.
-- Environments with prior draft V4-V8 must be recreated from V1-V3.
DROP TABLE yak_ops_data_sync_execution_event;
DROP TABLE yak_ops_data_sync_attempt;
DROP TABLE yak_ops_data_sync_schedule;
DROP TABLE yak_ops_data_sync_instance;
DROP TABLE yak_ops_data_sync_task;

CREATE TABLE yak_ops_task_definition (
  id VARCHAR(64) NOT NULL COMMENT '任务定义稳定ID',
  workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作空间ID',
  name VARCHAR(128) NOT NULL COMMENT '任务名称',
  task_type VARCHAR(64) NOT NULL COMMENT '任务插件类型',
  status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '发布状态0下线1上线',
  current_version INT UNSIGNED NOT NULL COMMENT '当前可执行配置版本',
  remark VARCHAR(500) NULL COMMENT '备注',
  deleted_at DATETIME(3) NULL COMMENT '逻辑删除时间',
  active_name VARCHAR(128) GENERATED ALWAYS AS
    (CASE WHEN deleted_at IS NULL THEN name ELSE NULL END) STORED
    COMMENT '未删除任务唯一名称',
  create_time DATETIME(3) NOT NULL COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL COMMENT '更新时间',
  create_by VARCHAR(64) NOT NULL COMMENT '创建人',
  update_by VARCHAR(64) NOT NULL COMMENT '更新人',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_task_definition_workspace_active_name (workspace_id,active_name),
  KEY idx_ops_task_definition_workspace_type (workspace_id,task_type,status,update_time)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='通用任务定义';

CREATE TABLE yak_ops_task_definition_version (
  id VARCHAR(64) NOT NULL COMMENT '定义版本记录ID',
  workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作空间ID',
  definition_id VARCHAR(64) NOT NULL COMMENT '任务定义ID',
  version INT UNSIGNED NOT NULL COMMENT '可执行版本号',
  parameters LONGTEXT NOT NULL COMMENT '不含连接凭证的插件参数',
  parameter_schema_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '参数结构版本',
  create_time DATETIME(3) NOT NULL COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL COMMENT '更新时间',
  create_by VARCHAR(64) NOT NULL COMMENT '创建人',
  update_by VARCHAR(64) NOT NULL COMMENT '更新人',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ops_task_version_definition_version (workspace_id,definition_id,version)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='不可变任务参数版本';
