-- Data Service Stage 3: cluster-safe rate limiting, monotonic runtime generation and observability lifecycle.

ALTER TABLE yak_ops_data_service_api
    ADD COLUMN runtime_generation BIGINT UNSIGNED NOT NULL DEFAULT 1
        COMMENT 'Monotonic runtime cache namespace generation' AFTER circuit_recovery_seconds;

CREATE TABLE IF NOT EXISTS yak_ops_data_service_rate_window (
    api_key_id BIGINT UNSIGNED NOT NULL COMMENT 'Data Service API Key ID',
    window_minute BIGINT NOT NULL COMMENT 'UTC epoch minute',
    request_count INT NOT NULL DEFAULT 0 COMMENT 'Cluster-wide calls admitted in this window',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Last update time',
    PRIMARY KEY (api_key_id, window_minute),
    KEY idx_yak_ops_data_service_rate_window_minute (window_minute)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops Data Service cluster rate-limit windows';

CREATE TABLE IF NOT EXISTS yak_ops_data_service_call_log_hourly (
    project_id BIGINT UNSIGNED NOT NULL COMMENT 'Project Space ID',
    api_id BIGINT UNSIGNED NOT NULL COMMENT 'Data Service API ID',
    bucket_hour DATETIME NOT NULL COMMENT 'UTC hour bucket',
    service_name VARCHAR(256) NOT NULL COMMENT 'Service name snapshot',
    service_path VARCHAR(512) NOT NULL COMMENT 'Service path snapshot',
    total_calls BIGINT NOT NULL DEFAULT 0,
    success_calls BIGINT NOT NULL DEFAULT 0,
    failure_calls BIGINT NOT NULL DEFAULT 0,
    total_duration_ms BIGINT NOT NULL DEFAULT 0,
    total_rows BIGINT NOT NULL DEFAULT 0,
    first_call_at DATETIME(3) DEFAULT NULL,
    last_call_at DATETIME(3) DEFAULT NULL,
    last_success_at DATETIME(3) DEFAULT NULL,
    last_failure_at DATETIME(3) DEFAULT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (project_id, api_id, bucket_hour),
    KEY idx_yak_ops_data_service_rollup_project_hour (project_id, bucket_hour),
    KEY idx_yak_ops_data_service_rollup_api_hour (project_id, api_id, bucket_hour)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Yak Ops Data Service hourly invocation rollup';
