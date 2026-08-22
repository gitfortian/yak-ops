ALTER TABLE yak_realtime_job_definition
  ADD COLUMN runtime_environment_id BIGINT NULL AFTER description,
  ADD KEY idx_realtime_definition_environment (runtime_environment_id),
  ADD CONSTRAINT fk_realtime_definition_environment
    FOREIGN KEY (runtime_environment_id) REFERENCES yak_compute_environment(id) ON DELETE RESTRICT;

ALTER TABLE yak_realtime_job_deployment
  ADD COLUMN runtime_environment_id BIGINT NULL AFTER definition_version,
  ADD COLUMN runtime_environment_version INT NULL AFTER runtime_environment_id,
  ADD COLUMN runtime_environment_snapshot_json LONGTEXT NULL AFTER runtime_environment_version,
  ADD KEY idx_realtime_deployment_environment (runtime_environment_id);
