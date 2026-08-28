-- Preserve enough authoring metadata to retry the exact editor definition and trace retry chains.
ALTER TABLE yak_dev_task_execution
    ADD COLUMN schema_version INT NOT NULL DEFAULT 1 AFTER task_type,
    ADD COLUMN retry_of_execution_id BIGINT NULL AFTER runtime_execution_id,
    ADD KEY idx_yak_dev_task_execution_retry (retry_of_execution_id);
