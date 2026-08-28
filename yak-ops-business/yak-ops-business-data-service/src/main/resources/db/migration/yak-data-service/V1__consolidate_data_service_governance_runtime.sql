-- Data Service dedicated Flyway baseline after the legacy shared yak-datasource history.
--
-- Historical Data Service V3~V10 remain frozen in yak_ds_schema_history for checksum/history
-- compatibility. The previously pending V11/V12/V13 changes are consolidated here so Data
-- Service no longer competes with Datasource for the same Flyway version sequence.

-- Stage 1 reliability: bounded detail-page invocation-log read.
ALTER TABLE yak_ops_data_service_call_log
    ADD KEY idx_yak_ops_data_service_log_api_time_id (api_id, create_time, id);

-- Stage 2 governance: Project-scoped management truth while public runtime paths remain global.
-- Project IDs stay nullable at the physical layer because the application compatibility backfill
-- resolves legacy ownership dynamically before PROJECT_REQUIRED management endpoints are used.
ALTER TABLE yak_ops_data_service_api
    ADD COLUMN project_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Yak Security Project Space ID' AFTER id,
    ADD KEY idx_yak_ops_data_service_project_update (project_id, update_time, id),
    ADD KEY idx_yak_ops_data_service_project_enabled (project_id, enabled);

ALTER TABLE yak_ops_data_service_call_log
    ADD COLUMN project_id BIGINT UNSIGNED DEFAULT NULL COMMENT '调用发生时的数据服务 Project Space ID' AFTER id,
    ADD KEY idx_yak_ops_data_service_log_project_time (project_id, create_time, id),
    ADD KEY idx_yak_ops_data_service_log_project_api_time (project_id, api_id, create_time, id);

-- Stage 3 cluster runtime: monotonic cache generation, shared rate limiting and audit rollups.
ALTER TABLE yak_ops_data_service_api
    ADD COLUMN runtime_generation BIGINT UNSIGNED NOT NULL DEFAULT 1
        COMMENT 'Monotonic runtime cache namespace generation' AFTER circuit_recovery_seconds;

CREATE TABLE IF NOT EXISTS yak_ops_data_service_rate_window (
    api_key_id BIGINT UNSIGNED NOT NULL COMMENT 'Data Service API Key ID',
    window_minute BIGINT NOT NULL COMMENT 'Epoch minute shared across instances',
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
    bucket_hour DATETIME NOT NULL COMMENT 'Application/database local hour bucket',
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
