-- Workflow 首页聚合在 Project Space 内按 created_at 扫描时间窗口。
ALTER TABLE yak_workflow_execution
    ADD KEY idx_yak_workflow_execution_project_created (project_id, created_at);

-- Legacy 首页 schedule summary 同时覆盖全局读与 Project Space 内读取。
ALTER TABLE yak_workflow_schedule
    ADD KEY idx_yak_workflow_schedule_next_fire_only (next_fire_time),
    ADD KEY idx_yak_workflow_schedule_project_next_fire (project_id, next_fire_time);
