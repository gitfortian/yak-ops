-- Project Space expand migration for datasource roots.
-- The column remains nullable until historical rows are backfilled and optional mode is retired.
ALTER TABLE yak_ops_data_source
    ADD COLUMN project_id BIGINT NULL COMMENT 'Yak Security Project ID' AFTER id,
    DROP INDEX uk_yak_ops_data_source_name,
    ADD UNIQUE KEY uk_yak_ops_data_source_project_name (project_id, name),
    ADD KEY idx_yak_ops_data_source_project_update (project_id, update_time),
    ADD KEY idx_yak_ops_data_source_project_status (project_id, conn_status);
