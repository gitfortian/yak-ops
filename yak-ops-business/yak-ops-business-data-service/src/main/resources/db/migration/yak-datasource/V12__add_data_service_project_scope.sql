-- Data Service Stage 2: project-scoped management truth while public runtime paths remain global.
-- Columns stay nullable at the physical migration layer because the compatibility Project ID is
-- resolved dynamically at application startup. The boot backfill makes every legacy row concrete
-- before PROJECT_REQUIRED management endpoints are used.
ALTER TABLE yak_ops_data_service_api
    ADD COLUMN project_id BIGINT UNSIGNED DEFAULT NULL COMMENT 'Yak Security Project Space ID' AFTER id,
    ADD KEY idx_yak_ops_data_service_project_update (project_id, update_time, id),
    ADD KEY idx_yak_ops_data_service_project_enabled (project_id, enabled);

ALTER TABLE yak_ops_data_service_call_log
    ADD COLUMN project_id BIGINT UNSIGNED DEFAULT NULL COMMENT '调用发生时的数据服务 Project Space ID' AFTER id,
    ADD KEY idx_yak_ops_data_service_log_project_time (project_id, create_time, id),
    ADD KEY idx_yak_ops_data_service_log_project_api_time (project_id, api_id, create_time, id);
