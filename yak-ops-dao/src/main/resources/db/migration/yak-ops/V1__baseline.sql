-- Yak Ops first stable schema baseline.
-- Current product scope contains user/login persistence, workspace membership, user preferences, datasource management and data sync task/instance persistence.
-- This baseline is for rebuildable early-stage databases. Once released to a shared environment it becomes immutable.

CREATE TABLE yak_security_user (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    app_name VARCHAR(64) NOT NULL COMMENT '应用级数据隔离键',
    user_name VARCHAR(64) NOT NULL COMMENT '用户账号',
    pw VARCHAR(2048) NOT NULL COMMENT '单向哈希密码',
    salt VARCHAR(64) NOT NULL DEFAULT '' COMMENT '密码盐兼容字段',
    real_name VARCHAR(128) NULL COMMENT '真实姓名',
    phone VARCHAR(32) NULL COMMENT '手机号码',
    email VARCHAR(128) NULL COMMENT '电子邮箱',
    dept_id BIGINT NULL COMMENT '部门ID，仅作为用户资料字段保留，不创建部门表外键',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '用户状态：1 正常，2 禁用',
    is_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    active_user_name VARCHAR(64)
        GENERATED ALWAYS AS (IF(is_delete = 0, user_name, NULL)) STORED
        COMMENT '未删除用户唯一键',
    PRIMARY KEY (id),
    UNIQUE KEY uk_security_user_app_user_name (app_name, active_user_name),
    KEY idx_security_user_app_email (app_name, email, is_delete),
    KEY idx_security_user_app_phone (app_name, phone, is_delete),
    KEY idx_security_user_app_create_time (app_name, create_time, is_delete)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户账号表';


CREATE TABLE yak_ops_workspace (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    name VARCHAR(128) NOT NULL COMMENT '工作空间展示名称',
    description VARCHAR(500) NULL COMMENT '工作空间说明',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识，仅用于审计',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识，仅用于审计',
    PRIMARY KEY (id),
    KEY idx_ops_workspace_update_time (update_time)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='工作空间表';

CREATE TABLE yak_ops_workspace_member (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    workspace_id VARCHAR(64) NOT NULL COMMENT '工作空间ID',
    user_id VARCHAR(64) NOT NULL COMMENT '成员用户ID',
    role TINYINT UNSIGNED NOT NULL COMMENT '成员角色：1 所有者，2 管理员，3 成员',
    create_time DATETIME(3) NOT NULL COMMENT '加入时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_workspace_member_workspace_user (workspace_id, user_id),
    KEY idx_ops_workspace_member_user_workspace (user_id, workspace_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='工作空间成员关系表';

CREATE TABLE yak_ops_user_preference (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    user_id VARCHAR(64) NOT NULL COMMENT '偏好所属用户ID',
    scene VARCHAR(64) NOT NULL COMMENT '偏好场景，例如 PRODUCT_MENU 或 DATASOURCE_CREATE_TYPE',
    item_key VARCHAR(128) NOT NULL COMMENT '场景内稳定业务标识',
    favorite TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否被用户显式收藏：0 否，1 是',
    sort_order INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '收藏展示顺序，未收藏时为0',
    use_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计使用次数',
    last_used_time DATETIME(3) NULL COMMENT '最近一次使用时间',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_user_preference_user_scene_item (user_id, scene, item_key)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户偏好表';

CREATE TABLE yak_ops_data_source (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作空间ID，是数据源业务归属与隔离边界',
    name VARCHAR(128) NOT NULL COMMENT '数据源名称，在同一工作空间内唯一',
    db_type VARCHAR(32) NOT NULL COMMENT '数据库类型，对应已注册的数据源插件类型',
    jdbc_url VARCHAR(1024) NOT NULL COMMENT 'JDBC 连接地址',
    environment TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '运行环境：0 开发，1 测试，2 生产',
    conn_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '连接状态：0 未测试，1 连接可用，2 连接不可用',
    remark VARCHAR(500) NULL COMMENT '数据源备注',
    connection_params LONGTEXT NOT NULL COMMENT '规范化连接参数 JSON，包含敏感连接信息',
    original_json LONGTEXT NOT NULL COMMENT '前端编辑回显参数 JSON，可能包含敏感连接信息',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_data_source_workspace_name (workspace_id, name),
    KEY idx_ops_data_source_workspace_type (workspace_id, db_type),
    KEY idx_ops_data_source_workspace_environment (workspace_id, environment),
    KEY idx_ops_data_source_workspace_status (workspace_id, conn_status),
    KEY idx_ops_data_source_workspace_update_time (workspace_id, update_time)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='数据源管理表';

CREATE TABLE yak_ops_data_sync_task (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作空间ID，是同步任务业务归属与隔离边界',
    name VARCHAR(128) NOT NULL COMMENT '同步任务名称，在同一工作空间内唯一',
    sync_type TINYINT UNSIGNED NOT NULL COMMENT '同步类型：1 离线同步，2 实时同步',
    source_data_source_id VARCHAR(64) NOT NULL COMMENT '来源数据源ID，仅保存资源引用，不保存连接凭证',
    source_database VARCHAR(128) NULL COMMENT '来源数据库名称，无该层级时为空',
    source_schema VARCHAR(128) NULL COMMENT '来源Schema名称，无该层级时为空',
    source_table VARCHAR(128) NOT NULL COMMENT '来源表名称',
    target_data_source_id VARCHAR(64) NOT NULL COMMENT '目标数据源ID，仅保存资源引用，不保存连接凭证',
    target_database VARCHAR(128) NULL COMMENT '目标数据库名称，无该层级时为空',
    target_schema VARCHAR(128) NULL COMMENT '目标Schema名称，无该层级时为空',
    target_table VARCHAR(128) NOT NULL COMMENT '目标表名称',
    runtime_config LONGTEXT NOT NULL COMMENT 'YakFlow运行参数JSON，不包含数据源连接凭证',
    definition_version INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '任务定义版本，从1开始，定义修改后递增',
    remark VARCHAR(500) NULL COMMENT '同步任务备注',
    create_time DATETIME(3) NOT NULL COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ops_data_sync_task_workspace_name (workspace_id, name),
    KEY idx_ops_data_sync_task_workspace_type_update (workspace_id, sync_type, update_time),
    KEY idx_ops_data_sync_task_workspace_source (workspace_id, source_data_source_id),
    KEY idx_ops_data_sync_task_workspace_target (workspace_id, target_data_source_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='数据同步任务定义表';

CREATE TABLE yak_ops_data_sync_instance (
    id VARCHAR(64) NOT NULL COMMENT '主键ID，由应用雪花算法生成',
    workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作空间ID，是同步实例业务归属与隔离边界',
    task_id VARCHAR(64) NOT NULL COMMENT '来源同步任务ID，不使用数据库物理外键',
    task_name VARCHAR(128) NOT NULL COMMENT '实例启动时的任务名称快照',
    task_version INT UNSIGNED NOT NULL COMMENT '实例启动时采用的任务定义版本',
    sync_type TINYINT UNSIGNED NOT NULL COMMENT '实例同步类型：1 离线同步，2 实时同步',
    trigger_type TINYINT UNSIGNED NOT NULL COMMENT '触发方式：1 手动，2 调度，3 重试',
    status TINYINT UNSIGNED NOT NULL COMMENT '实例状态：1 等待，2 运行中，3 成功，4 失败，5 已取消，6 丢失',
    definition_snapshot LONGTEXT NOT NULL COMMENT '实例启动时的脱敏任务定义快照，禁止包含密码、连接参数、SSH私钥或Token',
    read_rows BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计读取行数',
    write_rows BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计写入行数',
    start_time DATETIME(3) NULL COMMENT '实例实际开始时间',
    finish_time DATETIME(3) NULL COMMENT '实例进入终态的完成时间',
    error_code INT UNSIGNED NULL COMMENT '失败时的结构化错误码',
    error_message VARCHAR(1000) NULL COMMENT '失败时的脱敏错误信息',
    create_time DATETIME(3) NOT NULL COMMENT '实例创建时间',
    update_time DATETIME(3) NOT NULL COMMENT '实例更新时间',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人标识',
    update_by VARCHAR(64) NOT NULL COMMENT '更新人标识',
    PRIMARY KEY (id),
    KEY idx_ops_data_sync_instance_workspace_task_create (workspace_id, task_id, create_time),
    KEY idx_ops_data_sync_instance_workspace_type_create (workspace_id, sync_type, create_time),
    KEY idx_ops_data_sync_instance_workspace_status_create (workspace_id, status, create_time),
    KEY idx_ops_data_sync_instance_workspace_create (workspace_id, create_time)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='数据同步任务实例表';
