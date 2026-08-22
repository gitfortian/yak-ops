ALTER TABLE yak_compute_environment
  ADD COLUMN last_check_status VARCHAR(16) NULL AFTER version,
  ADD COLUMN last_check_message VARCHAR(500) NULL AFTER last_check_status,
  ADD COLUMN last_check_time DATETIME(3) NULL AFTER last_check_message;
