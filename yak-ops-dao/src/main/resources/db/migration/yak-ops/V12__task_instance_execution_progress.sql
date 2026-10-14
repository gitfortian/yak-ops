ALTER TABLE yak_ops_task_instance
  ADD COLUMN progress_percent TINYINT UNSIGNED NULL COMMENT 'Last measured bounded Source split progress, 0 to 99; null if unavailable',
  ADD INDEX idx_ops_task_instance_latest (workspace_id, definition_id, create_time, id);
