-- Data Quality Project Space contract migration.
--
-- Project identity is derived from the already project-scoped Datasource root. The migration never
-- guesses a default Project ID. Any orphaned Quality root remains NULL and makes the NOT NULL
-- contract fail, stopping deployment instead of silently assigning data to the wrong workspace.

ALTER TABLE yak_quality_table_asset
    ADD COLUMN project_id BIGINT NULL COMMENT 'Yak Security Project ID' AFTER id;

ALTER TABLE yak_quality_monitor
    ADD COLUMN project_id BIGINT NULL COMMENT 'Yak Security Project ID' AFTER id;

ALTER TABLE yak_quality_execution
    ADD COLUMN project_id BIGINT NULL COMMENT 'Yak Security Project ID' AFTER id;

UPDATE yak_quality_table_asset asset
INNER JOIN yak_ops_data_source datasource ON datasource.id = asset.data_source_id
SET asset.project_id = datasource.project_id
WHERE asset.project_id IS NULL;

UPDATE yak_quality_monitor monitor
INNER JOIN yak_ops_data_source datasource ON datasource.id = monitor.data_source_id
SET monitor.project_id = datasource.project_id
WHERE monitor.project_id IS NULL;

UPDATE yak_quality_execution execution_row
INNER JOIN yak_quality_monitor monitor ON monitor.id = execution_row.monitor_id
SET execution_row.project_id = monitor.project_id
WHERE execution_row.project_id IS NULL;

ALTER TABLE yak_quality_table_asset
    MODIFY COLUMN project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID',
    DROP INDEX uk_yak_quality_table_asset_target,
    DROP INDEX idx_yak_quality_table_asset_query,
    ADD UNIQUE KEY uk_yak_quality_table_asset_target
      (project_id, data_source_id, database_name, schema_name, table_name),
    ADD KEY idx_yak_quality_table_asset_query
      (project_id, data_source_id, database_name, deleted, table_name);

ALTER TABLE yak_quality_monitor
    MODIFY COLUMN project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID',
    DROP INDEX idx_yak_quality_monitor_target,
    DROP INDEX idx_yak_quality_monitor_result,
    DROP INDEX idx_yak_quality_monitor_updated,
    ADD KEY idx_yak_quality_monitor_target
      (project_id, data_source_id, database_name, schema_name, table_name, deleted),
    ADD KEY idx_yak_quality_monitor_result
      (project_id, last_result, deleted),
    ADD KEY idx_yak_quality_monitor_updated
      (project_id, updated_at, id);

ALTER TABLE yak_quality_execution
    MODIFY COLUMN project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID',
    DROP INDEX idx_yak_quality_execution_monitor,
    DROP INDEX idx_yak_quality_execution_status,
    DROP INDEX idx_yak_quality_execution_result,
    DROP INDEX idx_yak_quality_execution_queued,
    ADD KEY idx_yak_quality_execution_monitor
      (project_id, monitor_id, created_at),
    ADD KEY idx_yak_quality_execution_status
      (project_id, execution_status, created_at),
    ADD KEY idx_yak_quality_execution_result
      (project_id, check_result, created_at),
    ADD KEY idx_yak_quality_execution_queued
      (project_id, queued_at, id);
