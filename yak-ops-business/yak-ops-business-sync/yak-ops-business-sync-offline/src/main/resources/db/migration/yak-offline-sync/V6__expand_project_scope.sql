-- Project Space expand migration for Offline Sync roots and runtime facts.
-- project_id stays nullable until default-space backfill and PROJECT_REQUIRED cutover complete.
ALTER TABLE yak_offline_job_definition
    ADD COLUMN project_id BIGINT NULL AFTER id,
    DROP INDEX uk_yak_offline_job_name,
    ADD UNIQUE KEY uk_yak_offline_project_job_name (project_id, job_name),
    ADD KEY idx_yak_offline_project_release (project_id, release_state, update_time),
    ADD KEY idx_yak_offline_project_schedule (project_id, schedule_enabled, cron_expression);

ALTER TABLE yak_offline_batch_execution
    ADD COLUMN project_id BIGINT NULL AFTER id,
    ADD KEY idx_yak_offline_batch_project_status (project_id, status, update_time),
    ADD KEY idx_yak_offline_batch_project_task (project_id, job_definition_id, id);

ALTER TABLE yak_offline_job_execution
    ADD COLUMN project_id BIGINT NULL AFTER id,
    ADD KEY idx_yak_offline_execution_project_status (project_id, status, last_sync_time),
    ADD KEY idx_yak_offline_execution_project_task (project_id, job_definition_id, id);
