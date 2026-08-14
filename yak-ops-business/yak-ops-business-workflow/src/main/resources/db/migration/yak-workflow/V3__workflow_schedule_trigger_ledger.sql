CREATE TABLE IF NOT EXISTS yak_workflow_schedule_trigger (
    id VARCHAR(80) NOT NULL,
    schedule_id VARCHAR(80) NOT NULL,
    workflow_id VARCHAR(80) NOT NULL,
    trigger_id VARCHAR(160) NOT NULL,
    trigger_source VARCHAR(32) NOT NULL DEFAULT 'CRON',
    planned_fire_time DATETIME(3) NOT NULL,
    actual_fire_time DATETIME(3) NOT NULL,
    execution_strategy VARCHAR(32) NOT NULL,
    misfire_strategy VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    workflow_execution_id VARCHAR(80) NULL,
    execution_status VARCHAR(32) NULL,
    message VARCHAR(1000) NULL,
    error_message TEXT NULL,
    launched_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL,
    update_time DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_workflow_schedule_trigger_plan (schedule_id, planned_fire_time),
    UNIQUE KEY uk_yak_workflow_schedule_trigger_id (trigger_id),
    KEY idx_yak_workflow_schedule_trigger_workflow (workflow_id, status, planned_fire_time),
    KEY idx_yak_workflow_schedule_trigger_execution (workflow_execution_id),
    KEY idx_yak_workflow_schedule_trigger_schedule (schedule_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Trigger Ledger 是审计事实，Schedule 删除后仍保留历史记录，因此不建立级联外键。
-- Published WorkflowExecution 通过 definition_id -> yak_workflow_version.workflow_id 查询工作流归属。
ALTER TABLE yak_workflow_execution
    ADD KEY idx_yak_workflow_execution_definition_status (definition_id, status, created_at);
