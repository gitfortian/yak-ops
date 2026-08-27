-- Project Space expand migration for Workflow roots and durable runtime facts.
-- project_id remains nullable until default-space backfill and PROJECT_REQUIRED cutover complete.
ALTER TABLE yak_workflow_definition
    ADD COLUMN project_id BIGINT NULL AFTER id,
    ADD KEY idx_yak_workflow_definition_project_status (project_id, status, update_time);

ALTER TABLE yak_workflow_execution
    ADD COLUMN project_id BIGINT NULL AFTER id,
    ADD KEY idx_yak_workflow_execution_project_status (project_id, status, updated_at),
    ADD KEY idx_yak_workflow_execution_project_definition (project_id, definition_id, created_at);

ALTER TABLE yak_workflow_schedule
    ADD COLUMN project_id BIGINT NULL AFTER id,
    ADD KEY idx_yak_workflow_schedule_project_status (project_id, status, next_fire_time),
    ADD KEY idx_yak_workflow_schedule_project_workflow (project_id, workflow_id, update_time);

ALTER TABLE yak_workflow_schedule_trigger
    ADD COLUMN project_id BIGINT NULL AFTER id,
    ADD KEY idx_yak_workflow_trigger_project_status (project_id, status, planned_fire_time),
    ADD KEY idx_yak_workflow_trigger_project_workflow (project_id, workflow_id, create_time);

ALTER TABLE yak_workflow_backfill
    ADD COLUMN project_id BIGINT NULL AFTER id,
    ADD KEY idx_yak_workflow_backfill_project_status (project_id, status, create_time),
    ADD KEY idx_yak_workflow_backfill_project_workflow (project_id, workflow_id, create_time);
