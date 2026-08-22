ALTER TABLE yak_realtime_job_deployment
  ADD COLUMN runtime_job_name VARCHAR(128) NULL AFTER idempotency_key,
  ADD COLUMN runtime_identity_state VARCHAR(16) NOT NULL DEFAULT 'REQUIRED' AFTER runtime_job_name,
  ADD UNIQUE KEY uk_realtime_runtime_job_name(runtime_job_name);

-- Rows that existed before this migration never had the deterministic runtime name injected into
-- their Flink pipeline. Mark them LEGACY so recovery never guesses by the user-facing task name.
UPDATE yak_realtime_job_deployment
SET runtime_identity_state='LEGACY'
WHERE runtime_job_name IS NULL;

-- New deployments use the column default REQUIRED. The gateway changes it to BOUND before it starts
-- the Flink CDC CLI, so a crash before BOUND proves no runtime job could have been submitted.
