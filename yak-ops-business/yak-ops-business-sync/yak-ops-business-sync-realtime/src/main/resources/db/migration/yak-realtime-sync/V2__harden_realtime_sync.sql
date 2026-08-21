ALTER TABLE yak_realtime_job_definition
  ADD COLUMN published_version INT NULL AFTER definition_version;

ALTER TABLE yak_realtime_job_deployment
  MODIFY COLUMN pipeline_yaml LONGTEXT NULL,
  ADD COLUMN spec_summary VARCHAR(1000) NULL AFTER pipeline_yaml,
  ADD COLUMN runtime_revision VARCHAR(128) NULL AFTER runtime_version;

-- Executable Pipeline YAML may contain current connection coordinates. It is deliberately
-- submission-scoped and must not remain in the control-plane database.
UPDATE yak_realtime_job_deployment SET pipeline_yaml = NULL WHERE pipeline_yaml IS NOT NULL;
