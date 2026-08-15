-- 数据服务第三阶段：API Key 鉴权、限流与调用方审计。
ALTER TABLE yak_ops_data_service_api
    ADD COLUMN auth_mode VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '访问控制：NONE/API_KEY' AFTER enabled,
    ADD KEY idx_yak_ops_data_service_auth_mode (auth_mode);

CREATE TABLE IF NOT EXISTS yak_ops_data_service_api_key (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    api_id BIGINT UNSIGNED NOT NULL COMMENT '数据服务 API ID',
    name VARCHAR(128) NOT NULL COMMENT '调用方/Key 名称',
    key_prefix VARCHAR(24) NOT NULL COMMENT '仅用于识别的 Key 前缀',
    key_hash CHAR(64) NOT NULL COMMENT 'API Key SHA-256 摘要',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    rate_limit_per_minute INT NOT NULL DEFAULT 60 COMMENT '每分钟最大调用次数',
    expires_at DATETIME(3) DEFAULT NULL COMMENT '过期时间，NULL 表示不过期',
    last_used_at DATETIME(3) DEFAULT NULL COMMENT '最后调用时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_ops_data_service_key_hash (key_hash),
    KEY idx_yak_ops_data_service_key_api (api_id),
    KEY idx_yak_ops_data_service_key_enabled (api_id, enabled),
    KEY idx_yak_ops_data_service_key_expire (expires_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops 数据服务 API Key';

ALTER TABLE yak_ops_data_service_call_log
    ADD COLUMN caller_type VARCHAR(32) NOT NULL DEFAULT 'LEGACY' COMMENT '调用方类型：LEGACY/PUBLIC/API_KEY/CONSOLE' AFTER service_path,
    ADD COLUMN api_key_id BIGINT UNSIGNED DEFAULT NULL COMMENT '调用 API Key ID' AFTER caller_type,
    ADD COLUMN api_key_name VARCHAR(128) DEFAULT NULL COMMENT '调用方名称快照' AFTER api_key_id,
    ADD COLUMN api_key_prefix VARCHAR(24) DEFAULT NULL COMMENT 'API Key 前缀快照' AFTER api_key_name,
    ADD KEY idx_yak_ops_data_service_log_key (api_key_id);
