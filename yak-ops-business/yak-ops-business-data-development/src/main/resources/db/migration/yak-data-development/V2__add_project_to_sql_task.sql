ALTER TABLE yak_dev_sql_task
    ADD COLUMN project_id BIGINT NULL AFTER description,
    ADD KEY idx_yak_dev_sql_task_project (project_id, update_time);
