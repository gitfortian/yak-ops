ALTER TABLE yak_ops_data_sync_task
    ADD COLUMN desired_state TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'REALTIME期望运行状态：0已停止，1运行中；OFFLINE固定为0' AFTER status,
    ADD KEY idx_ops_data_sync_task_realtime_desired (sync_type, status, desired_state, update_time);

UPDATE yak_ops_data_sync_task task
SET task.desired_state = 1
WHERE task.sync_type = 2
  AND task.status = 1
  AND EXISTS (
      SELECT 1
      FROM yak_ops_data_sync_instance instance_row
      WHERE instance_row.workspace_id = task.workspace_id
        AND instance_row.task_id = task.id
        AND instance_row.sync_type = 2
        AND instance_row.status IN (1, 2, 7)
  );

ALTER TABLE yak_ops_data_sync_instance
    MODIFY COLUMN trigger_type TINYINT UNSIGNED NOT NULL COMMENT 'Execution根触发方式：1手动，2调度，3兼容重试，4自动恢复';
