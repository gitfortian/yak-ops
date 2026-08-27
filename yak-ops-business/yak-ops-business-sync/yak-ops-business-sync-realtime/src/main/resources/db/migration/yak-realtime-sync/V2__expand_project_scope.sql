-- Project Space expand migration for Realtime Sync roots and runtime facts.
-- Compute Environment and Runtime Lease remain GLOBAL.
ALTER TABLE yak_realtime_job_definition
    ADD COLUMN project_id BIGINT NULL AFTER id,
    DROP INDEX uk_realtime_name,
    ADD UNIQUE KEY uk_realtime_project_name (project_id, job_name),
    ADD KEY idx_realtime_project_state (project_id, release_state, update_time);

ALTER TABLE yak_realtime_job_deployment
    ADD COLUMN project_id BIGINT NULL AFTER id,
    ADD KEY idx_realtime_deployment_project_state (project_id, observed_state, update_time),
    ADD KEY idx_realtime_deployment_project_definition (project_id, definition_id, id);
