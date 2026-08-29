-- Project Space Stage 3.2 contract migration.
-- Stage 3.2 backfill must have completed before this migration reaches a legacy database.
-- This migration intentionally performs no implicit backfill: remaining NULL rows must fail fast.

ALTER TABLE yak_ops_data_service_api
    MODIFY COLUMN project_id BIGINT UNSIGNED NOT NULL COMMENT 'Yak Security Project Space ID';

ALTER TABLE yak_ops_data_service_call_log
    MODIFY COLUMN project_id BIGINT UNSIGNED NOT NULL COMMENT '调用发生时的数据服务 Project Space ID';
