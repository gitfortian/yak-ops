-- Yak Ops 数据服务第一阶段：API 定义与调用日志。
CREATE TABLE IF NOT EXISTS yak_ops_data_service_api (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name VARCHAR(128) NOT NULL COMMENT '服务名称',
    path VARCHAR(255) NOT NULL COMMENT '服务相对路径',
    data_source_id BIGINT UNSIGNED NOT NULL COMMENT 'Yak Ops 数据源 ID',
    sql_text LONGTEXT NOT NULL COMMENT '只读 SELECT SQL，支持 :name 命名参数',
    max_rows INT NOT NULL DEFAULT 1000 COMMENT '最大返回行数',
    timeout_seconds INT NOT NULL DEFAULT 30 COMMENT '查询超时秒数',
    enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用',
    description VARCHAR(500) DEFAULT NULL COMMENT '说明',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_ops_data_service_path (path),
    KEY idx_yak_ops_data_service_ds (data_source_id),
    KEY idx_yak_ops_data_service_enabled (enabled),
    KEY idx_yak_ops_data_service_update_time (update_time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops 数据服务 API 定义';

CREATE TABLE IF NOT EXISTS yak_ops_data_service_call_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    api_id BIGINT UNSIGNED NOT NULL COMMENT '数据服务 API ID',
    service_name VARCHAR(128) NOT NULL COMMENT '调用时服务名称快照',
    service_path VARCHAR(255) NOT NULL COMMENT '调用时路径快照',
    params_json TEXT DEFAULT NULL COMMENT '请求参数 JSON',
    success TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否成功',
    duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '执行耗时毫秒',
    row_count INT NOT NULL DEFAULT 0 COMMENT '返回行数',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '调用时间',
    PRIMARY KEY (id),
    KEY idx_yak_ops_data_service_log_api (api_id),
    KEY idx_yak_ops_data_service_log_time (create_time),
    KEY idx_yak_ops_data_service_log_success (success)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops 数据服务调用日志';
