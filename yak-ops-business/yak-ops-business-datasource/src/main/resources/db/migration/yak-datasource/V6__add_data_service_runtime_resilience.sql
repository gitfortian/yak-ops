-- 数据服务第四阶段：本机结果缓存、熔断保护与运行时策略。
-- 历史服务保持缓存/熔断关闭，避免升级后改变既有线上行为；新建服务由应用层启用保守熔断默认值。
ALTER TABLE yak_ops_data_service_api
    ADD COLUMN cache_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用本机结果缓存' AFTER auth_mode,
    ADD COLUMN cache_ttl_seconds INT NOT NULL DEFAULT 60 COMMENT '缓存 TTL（秒）' AFTER cache_enabled,
    ADD COLUMN cache_max_entries INT NOT NULL DEFAULT 200 COMMENT '单服务最大缓存条目数' AFTER cache_ttl_seconds,
    ADD COLUMN circuit_breaker_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用熔断保护' AFTER cache_max_entries,
    ADD COLUMN circuit_failure_threshold INT NOT NULL DEFAULT 5 COMMENT '连续失败触发熔断阈值' AFTER circuit_breaker_enabled,
    ADD COLUMN circuit_recovery_seconds INT NOT NULL DEFAULT 30 COMMENT '熔断恢复等待秒数' AFTER circuit_failure_threshold;
