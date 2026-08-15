-- 数据服务第五阶段：参数/响应 Schema 与 OpenAPI 文档元数据。
CREATE TABLE IF NOT EXISTS yak_ops_data_service_documentation (
    api_id BIGINT UNSIGNED NOT NULL COMMENT '数据服务 API ID',
    parameter_schema_json MEDIUMTEXT DEFAULT NULL COMMENT '请求参数文档 JSON',
    response_schema_json MEDIUMTEXT DEFAULT NULL COMMENT '响应行字段文档 JSON',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (api_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops 数据服务 API 文档元数据';
