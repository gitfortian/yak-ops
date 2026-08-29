-- Project Space Stage 2 expand migration.
-- Existing rows are backfilled after Yak Security resolves the real default Project ID.

ALTER TABLE yak_ops_sql_execution
    ADD COLUMN project_id BIGINT NULL COMMENT 'Yak Security Project ID' AFTER id,
    ADD KEY idx_yak_ops_sql_execution_project_started (project_id, started_at),
    ADD KEY idx_yak_ops_sql_execution_project_status_started (project_id, status, started_at);
