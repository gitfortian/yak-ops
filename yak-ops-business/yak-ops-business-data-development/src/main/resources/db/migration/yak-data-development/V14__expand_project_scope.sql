-- Project Space expand migration for Data Development roots and runtime facts.
-- Columns remain nullable until the default-space backfill and PROJECT_REQUIRED cutover complete.
ALTER TABLE yak_dev_directory
    ADD COLUMN project_id BIGINT NULL AFTER id,
    DROP INDEX uk_yak_dev_directory_sibling,
    ADD UNIQUE KEY uk_yak_dev_directory_project_sibling (project_id, parent_id, name),
    ADD KEY idx_yak_dev_directory_project_parent (project_id, parent_id);

ALTER TABLE yak_dev_node
    ADD KEY idx_yak_dev_node_project_directory (project_id, directory_id, name);

ALTER TABLE yak_dev_task_execution
    ADD COLUMN project_id BIGINT NULL AFTER id,
    ADD KEY idx_yak_dev_task_execution_project_time (project_id, start_time),
    ADD KEY idx_yak_dev_task_execution_project_status (project_id, status, start_time);

ALTER TABLE yak_dev_lineage_outbox
    ADD COLUMN project_id BIGINT NULL AFTER task_id,
    ADD KEY idx_yak_dev_lineage_outbox_project_due (project_id, status, next_attempt_time);
