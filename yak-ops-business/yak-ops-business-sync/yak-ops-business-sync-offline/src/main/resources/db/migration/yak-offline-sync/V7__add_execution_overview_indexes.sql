-- 首页/运行总览按 execution create_time 做时间窗口聚合。
ALTER TABLE yak_offline_job_execution
    ADD KEY idx_yak_offline_execution_created (create_time, id),
    ADD KEY idx_yak_offline_execution_project_created (project_id, create_time, id);

-- 首页 legacy schedule summary 同时覆盖全局读与 Project Space 内读取。
ALTER TABLE yak_offline_job_definition
    ADD KEY idx_yak_offline_schedule_next
      (schedule_enabled, schedule_next_fire_time),
    ADD KEY idx_yak_offline_project_schedule_next
      (project_id, schedule_enabled, schedule_next_fire_time);
